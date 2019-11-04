/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import android.util.SparseArray;

import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.OperationScheduler;
import com.urbanairship.PendingResult;
import com.urbanairship.Predicate;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AnalyticsListener;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.RegionEvent;
import com.urbanairship.reactive.Function;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Scheduler;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subject;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.util.AirshipHandlerThread;
import com.urbanairship.util.Checks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core automation engine.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AutomationEngine<T extends Schedule> {

    /*
     Standard schedule flow:
         Add Schedule
         => STATE_IDLE
         => trigger goal is met
         => If delay is set
                => STATE_TIME_DELAYED
                => STATE_PREPARING_SCHEDULE
            else
                => STATE_PREPARING_SCHEDULE
         => STATE_WAITING_SCHEDULE_CONDITIONS
         => STATE_EXECUTING
         => If at limit or expired
                => STATE_FINISH
            else if execution interval
                => STATE_PAUSED
            else
                => STATE_IDLE

     STATE_IDLE:
        Active schedule that is not yet expired or triggered.

        Only standard triggers will be considered in this state.

        When schedule is triggered, it will move to either (in order):
            - STATE_FINISHED: If the schedule has expired.
            - STATE_TIME_DELAYED: If the schedule defines a time delay.
            - STATE_PREPARING_SCHEDULE: The driver has started preparing the schedule.

     STATE_TIME_DELAYED:
        The schedule is waiting for its time delay to expire.

        Only cancellation triggers will be evaluated in this state.

        After delay, it will move to either:
            - STATE_FINISHED: If the schedule has expired.
            - STATE_PREPARING_SCHEDULE

     STATE_PREPARING_SCHEDULE:
         Preparing the schedule for execution. The driver is responsible for this step.

         Only cancellation triggers will be considered in this state.

         After the callback, it will move to either (in order):
            - STATE_FINISHED: If the schedule has expired.
            - STATE_FINISHED: If the adapter's prepare result is PREPARE_RESULT_CANCEL.
            - STATE_FINISHED: If the adapter's prepare result is PREPARE_RESULT_PENALIZE and the schedule is at its limit.
            - STATE_PAUSED: If the adapter's prepare result is PREPARE_RESULT_PENALIZE and the schedule has an execution interval.
            - STATE_IDLE: If the result is RESULT_PENALIZE or PREPARE_RESULT_SKIP.
            - STATE_WAITING_SCHEDULE_CONDITIONS: If the result is PREPARE_RESULT_CONTINUE.
            - STATE_PREPARING_SCHEDULE: If the result is PREPARE_RESULT_INVALIDATE.

     STATE_WAITING_SCHEDULE_CONDITIONS:
         The schedule is waiting for other state delay conditions. The conditions are evaluated on the main thread
         and if all the conditions are met, it will check with the driver if it is ready to execute the schedule.
         Once all the conditions are met and the driver is ready, the driver will start executing the schedule.

         Only cancellation triggers will be considered in this state.

         After the conditions are met, it will move to either (in order):
            - STATE_FINISHED: If the schedule has expired.
            - STATE_EXECUTING: If the schedule's conditions are met and the driver is ready to execute.
            - STATE_PREPARING_SCHEDULE: If the ready result is READY_RESULT_INVALIDATE.

     STATE_EXECUTING:
        The schedule is executing. The adapter is responsible for this step.

        After the adapter notifies that the schedule is finished, it will move to either (in order):
            - STATE_FINISHED: If the schedule has expired.
            - STATE_FINISHED: If the schedule's limit is reached.
            - STATE_PAUSED: If the schedule defines an execution interval.
            - STATE_IDLE

     STATE_PAUSED:
        After a schedule is executing and it defines an execution interval, it will be placed into
        this state.

         After the interval has expired, it will move to either (in order):
            - STATE_FINISHED: If the schedule has expired.
            - STATE_IDLE

     STATE_FINISHED:
         The schedule has either met its limit or it has expired. If the edit grace period is defined
         it will stay in this state for at least that amount of time, otherwise the schedule is
         immediately deleted. A schedule is only able to go back to STATE_IDLE if an edit changes
         the schedule to no longer be expired and no longer at its execution limit.

         Note: When moving a schedule to STATE_FINISHED, it will often be deleted immediately if the
               schedule does not define an edit grace period.


     Recovering schedules during App Init:

         - Update schedule states:
            - STATE_FINISHED: If the schedule has expired.
            - STATE_PREPARING_SCHEDULE: If the schedule's state is STATE_WAITING_SCHEDULE_CONDITIONS.
            - STATE_PREPARING_SCHEDULE: If the schedule's state is STATE_EXECUTING.
         - Delete finished schedules if their edit grace period has expired.
         - Schedule time delay alarms for schedules in the state STATE_TIME_DELAYED.
         - Schedule execution interval alarms for schedules in the state STATE_FINISHED.
         - Start preparing schedules in the state STATE_PREPARING_SCHEDULE.

     **/

    private final List<Integer> COMPOUND_TRIGGER_TYPES = Arrays.asList(Trigger.ACTIVE_SESSION, Trigger.VERSION);

    /**
     * Used to sort schedule priority.
     */
    private final Comparator<ScheduleEntry> SCHEDULE_PRIORITY_COMPARATOR =  new Comparator<ScheduleEntry>() {
        @Override
        public int compare(@NonNull ScheduleEntry lh, @NonNull ScheduleEntry rh) {
            if (lh.getPriority() == rh.getPriority()) {
                return 0;
            }
            return lh.getPriority() > rh.getPriority() ? 1 : -1;
        }
    };

    private final AutomationDataManager dataManager;
    private final ActivityMonitor activityMonitor;
    private final AutomationDriver<T> driver;
    private final Analytics analytics;
    private final long scheduleLimit;
    private final OperationScheduler scheduler;
    private boolean isStarted;
    private Handler backgroundHandler;
    private final Handler mainHandler;
    private ScheduleListener<T> scheduleListener;
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private long startTime;
    private final SparseArray<Long> stateChangeTimeStamps = new SparseArray<>();

    @VisibleForTesting
    final HandlerThread backgroundThread;
    private final List<ScheduleOperation> pendingAlarmOperations = new ArrayList<>();

    private String screen;
    private String regionId;

    private Subject<TriggerUpdate> stateObservableUpdates;
    private Subscription compoundTriggerSubscription;
    private Scheduler backgroundScheduler;

    private final ApplicationListener applicationListener = new ApplicationListener() {
        @Override
        public void onForeground(long time) {
            AutomationEngine.this.onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_FOREGROUND, 1.00);
            onScheduleConditionsChanged();
        }

        @Override
        public void onBackground(long time) {
            AutomationEngine.this.onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_BACKGROUND, 1.00);
            onScheduleConditionsChanged();
        }
    };

    private final AnalyticsListener analyticsListener = new AnalyticsListener() {
        @Override
        public void onRegionEventAdded(@NonNull RegionEvent regionEvent) {
            regionId = regionEvent.toJsonValue().optMap().opt("region_id").getString();
            int type = regionEvent.getBoundaryEvent() == RegionEvent.BOUNDARY_EVENT_ENTER ? Trigger.REGION_ENTER : Trigger.REGION_EXIT;
            onEventAdded(regionEvent.toJsonValue(), type, 1.00);
            onScheduleConditionsChanged();
        }

        @Override
        public void onCustomEventAdded(@NonNull CustomEvent customEvent) {
            onEventAdded(customEvent.toJsonValue(), Trigger.CUSTOM_EVENT_COUNT, 1.00);

            BigDecimal eventValue = customEvent.getEventValue();
            if (eventValue != null) {
                onEventAdded(customEvent.toJsonValue(), Trigger.CUSTOM_EVENT_VALUE, eventValue.doubleValue());
            }
        }

        @Override
        public void onScreenTracked(@NonNull String screenName) {
            screen = screenName;
            onEventAdded(JsonValue.wrap(screenName), Trigger.SCREEN_VIEW, 1.00);
            onScheduleConditionsChanged();
        }
    };

    /**
     * Schedule listener.
     */
    public interface ScheduleListener<T> {

        /**
         * Called when a schedule is expired.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onScheduleExpired(@NonNull T schedule);

        /**
         * Called when a schedule is cancelled.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onScheduleCancelled(@NonNull T schedule);

        /**
         * Called when a schedule's limit is reached.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onScheduleLimitReached(@NonNull T schedule);

        /**
         * Called when a new schedule is available.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onNewSchedule(@NonNull T schedule);

    }

    /**
     * Default constructor.
     *
     * @param builder The builder instance.
     */
    private AutomationEngine(@NonNull Builder<T> builder) {
        this.dataManager = builder.dataManager;
        this.activityMonitor = builder.activityMonitor;
        this.analytics = builder.analytics;
        this.driver = builder.driver;
        this.scheduleLimit = builder.limit;
        this.scheduler = builder.scheduler;
        this.backgroundThread = new AirshipHandlerThread("automation");
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Performs setup and starts listening for events.
     */
    public void start() {
        if (isStarted) {
            return;
        }

        this.startTime = System.currentTimeMillis();
        this.backgroundThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());
        this.backgroundScheduler = Schedulers.looper(backgroundThread.getLooper());

        activityMonitor.addApplicationListener(applicationListener);
        analytics.addAnalyticsListener(analyticsListener);

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                resetExecutingSchedules();
                restoreDelayAlarms();
                restoreIntervalAlarms();
                prepareSchedules(dataManager.getScheduleEntries(ScheduleEntry.STATE_PREPARING_SCHEDULE));
            }
        });

        restoreCompoundTriggers();
        onScheduleConditionsChanged();
        onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_APP_INIT, 1.00);
        isStarted = true;
    }

    /**
     * Pauses processing any triggers or executing schedules. Any events will be dropped and
     * not counted towards a trigger's goal.
     *
     * @param isPaused {@code true} to pause the engine, otherwise {@code false}.
     */
    public void setPaused(boolean isPaused) {
        this.isPaused.set(isPaused);

        if (!isPaused) {
            onScheduleConditionsChanged();
        }
    }

    /**
     * Stops the engine. Cleans up any listeners and threads. Once stopped the engine
     * is no longer valid.
     */
    public void stop() {
        if (!isStarted) {
            return;
        }

        compoundTriggerSubscription.cancel();
        activityMonitor.removeApplicationListener(applicationListener);
        analytics.removeAnalyticsListener(analyticsListener);
        cancelAlarms();
        backgroundThread.quit();
        isStarted = false;
    }

    /**
     * Schedules a single action schedule.
     *
     * @param scheduleInfo The {@link ScheduleInfo} instance.
     * @param metadata The schedule metadata.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<T> schedule(@NonNull final ScheduleInfo scheduleInfo, @NonNull final JsonMap metadata) {
        final PendingResult<T> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();

                if (dataManager.getScheduleCount() >= scheduleLimit) {
                    Logger.error("AutomationEngine - Unable to insert schedule due to schedule exceeded limit.");
                    pendingResult.setResult(null);
                    return;
                }

                String scheduleId = UUID.randomUUID().toString();
                ScheduleEntry entry = new ScheduleEntry(scheduleId, scheduleInfo, metadata);

                List<ScheduleEntry> entries = Collections.singletonList(entry);
                dataManager.saveSchedules(entries);
                subscribeStateObservables(entries);

                List<T> result = convertEntries(entries);
                notifyNewSchedule(result);

                Logger.verbose("AutomationEngine - Scheduled entries: %s", result);
                pendingResult.setResult(result.size() > 0 ? result.get(0) : null);
            }
        });

        return pendingResult;
    }

    /**
     * Schedules a list of action schedules.
     *
     * @param scheduleInfos A list of {@link ScheduleInfo}.
     * @param metadata The schedule metadata.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<List<T>> schedule(@NonNull final List<? extends ScheduleInfo> scheduleInfos, final @NonNull JsonMap metadata) {
        final PendingResult<List<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();

                if (dataManager.getScheduleCount() + scheduleInfos.size() > scheduleLimit) {
                    Logger.error("AutomationDataManager - Unable to insert schedule due to schedule exceeded limit.");
                    pendingResult.setResult(Collections.<T>emptyList());
                    return;
                }

                List<ScheduleEntry> entries = new ArrayList<>();
                for (ScheduleInfo info : scheduleInfos) {
                    String scheduleId = UUID.randomUUID().toString();
                    entries.add(new ScheduleEntry(scheduleId, info, metadata));
                }

                dataManager.saveSchedules(entries);
                subscribeStateObservables(entries);

                List<T> result = convertEntries(entries);
                notifyNewSchedule(result);

                Logger.verbose("AutomationEngine - Scheduled entries: %s", result);
                pendingResult.setResult(convertEntries(entries));

            }
        });

        return pendingResult;
    }

    /**
     * Cancels schedules.
     *
     * @param ids A collection of schedule Ids to cancel.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Void> cancel(@NonNull final Collection<String> ids) {
        final PendingResult<Void> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyCancelledSchedule(dataManager.getScheduleEntries(new HashSet<>(ids)));

                dataManager.deleteSchedules(ids);
                cancelScheduleAlarms(ids);

                Logger.verbose("AutomationEngine - Cancelled schedules: %s", ids);
                pendingResult.setResult(null);
            }
        });

        return pendingResult;
    }

    /**
     * Cancels a group of schedules.
     *
     * @param group The schedule group.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Boolean> cancelGroup(@NonNull final String group) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyCancelledSchedule(dataManager.getScheduleEntries(group));

                cancelGroupAlarms(Collections.singletonList(group));

                if (dataManager.deleteGroup(group)) {
                    Logger.verbose("AutomationEngine - Cancelled schedule group: %s", group);
                    pendingResult.setResult(true);
                } else {
                    Logger.verbose("AutomationEngine - Failed to cancel schedule group: %s", group);
                    pendingResult.setResult(false);
                }
            }
        });

        return pendingResult;
    }

    /**
     * Cancels schedule groups.
     *
     * @param groups A collection of groups.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Void> cancelGroups(@NonNull final Collection<String> groups) {
        final PendingResult<Void> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                for (String group : groups) {
                    notifyCancelledSchedule(dataManager.getScheduleEntries(group));
                }

                cancelGroupAlarms(groups);
                dataManager.deleteGroups(groups);
                Logger.verbose("AutomationEngine - Canceled schedule groups: %s", groups);
                pendingResult.setResult(null);
            }
        });

        return pendingResult;
    }

    /**
     * Cancels all schedules.
     *
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Void> cancelAll() {
        final PendingResult<Void> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyCancelledSchedule(dataManager.getScheduleEntries());

                dataManager.deleteAllSchedules();
                cancelAlarms();
                Logger.verbose("AutomationEngine - Canceled all schedules.");
                pendingResult.setResult(null);
            }
        });

        return pendingResult;
    }

    /**
     * Gets a schedule for the given schedule ID.
     *
     * @param scheduleId The schedule ID.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<T> getSchedule(@NonNull final String scheduleId) {
        final PendingResult<T> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                List<T> result = convertEntries(dataManager.getScheduleEntries(Collections.singleton(scheduleId)));
                pendingResult.setResult(result.size() > 0 ? result.get(0) : null);
            }
        });

        return pendingResult;
    }

    /**
     * Gets a list of schedules.
     *
     * @param scheduleIds A collection of schedule IDs.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Collection<T>> getSchedules(@NonNull final Set<String> scheduleIds) {
        final PendingResult<Collection<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                pendingResult.setResult(convertEntries(dataManager.getScheduleEntries(scheduleIds)));
            }
        });

        return pendingResult;
    }

    /**
     * Gets all schedules for the specified group.
     *
     * @param group The schedule group.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Collection<T>> getSchedules(@NonNull final String group) {
        final PendingResult<Collection<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                pendingResult.setResult(convertEntries(dataManager.getScheduleEntries(group)));
            }
        });

        return pendingResult;
    }

    /**
     * Edits a schedule.
     *
     * @param scheduleId The schedule ID.
     * @param edits The schedule edits.
     * @return Pending result with the updated schedule.
     */
    @NonNull
    public PendingResult<T> editSchedule(@NonNull final String scheduleId, @NonNull final ScheduleEdits edits) {
        final PendingResult<T> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                ScheduleEntry entry = dataManager.getScheduleEntry(scheduleId);

                if (entry == null) {
                    Logger.error("AutomationEngine - Schedule no longer exists. Unable to edit: %s", scheduleId);
                    pendingResult.setResult(null);
                    return;
                }

                entry.applyEdits(edits);

                boolean subscribeForStateChanges = false;
                long stateChangeTimeStamp = -1;

                boolean isOverLimit = entry.isOverLimit();
                boolean isExpired = entry.isExpired();

                // Check if the schedule needs to be rehabilitated or finished due to the edits
                if (entry.getExecutionState() == ScheduleEntry.STATE_FINISHED && !isOverLimit && !isExpired) {
                    subscribeForStateChanges = true;
                    stateChangeTimeStamp = entry.getExecutionStateChangeDate();
                    entry.setExecutionState(ScheduleEntry.STATE_IDLE);
                } else if (entry.getExecutionState() != ScheduleEntry.STATE_FINISHED && (isOverLimit || isExpired)) {
                    entry.setExecutionState(ScheduleEntry.STATE_FINISHED);

                    if (isOverLimit) {
                        notifyScheduleLimitReached(entry);
                    } else {
                        notifyExpiredSchedules(Collections.singleton(entry));
                    }
                }

                dataManager.saveSchedule(entry);

                if (subscribeForStateChanges) {
                    subscribeStateObservables(entry, stateChangeTimeStamp);
                }

                List<T> result = convertEntries(dataManager.getScheduleEntries(Collections.singleton(scheduleId)));
                Logger.verbose("AutomationEngine - Updated schedule: %s", result);
                pendingResult.setResult(result.size() > 0 ? result.get(0) : null);
            }
        });

        return pendingResult;
    }

    /**
     * Triggers the engine to recheck all pending schedules.
     */
    public void checkPendingSchedules() {
        if (isStarted) {
            onScheduleConditionsChanged();
        }
    }

    /**
     * Gets all schedules.
     *
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Collection<T>> getSchedules() {
        final PendingResult<Collection<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                pendingResult.setResult(convertEntries(dataManager.getScheduleEntries()));
            }
        });

        return pendingResult;
    }

    /**
     * Sets the schedule listener.
     *
     * @param scheduleListener The listener.
     */
    public void setScheduleListener(@Nullable ScheduleListener<T> scheduleListener) {
        synchronized (this) {
            this.scheduleListener = scheduleListener;
        }
    }

    /**
     * Creates an event observables for compound triggers.
     *
     * @param type The trigger type
     * @return The corresponding observable, or an empty observable in case no match was found.
     */
    @NonNull
    private Observable<JsonSerializable> createEventObservable(@Trigger.TriggerType int type) {
        switch (type) {
            case Trigger.ACTIVE_SESSION:
                return TriggerObservables.newSession(activityMonitor);
            case Trigger.CUSTOM_EVENT_COUNT:
            case Trigger.CUSTOM_EVENT_VALUE:
            case Trigger.LIFE_CYCLE_APP_INIT:
            case Trigger.LIFE_CYCLE_BACKGROUND:
            case Trigger.LIFE_CYCLE_FOREGROUND:
            case Trigger.REGION_ENTER:
            case Trigger.REGION_EXIT:
            case Trigger.SCREEN_VIEW:
            case Trigger.VERSION:
            default:
                return Observable.empty();
        }
    }

    /**
     * Creates a state observables for compound triggers.
     *
     * @param type The trigger type
     * @return The corresponding observable, or an empty observable in case no match was found.
     */
    @NonNull
    private Observable<JsonSerializable> createStateObservable(@Trigger.TriggerType int type) {
        switch (type) {
            case Trigger.ACTIVE_SESSION:
                return TriggerObservables.foregrounded(activityMonitor);
            case Trigger.VERSION:
                return TriggerObservables.appVersionUpdated();
            case Trigger.CUSTOM_EVENT_COUNT:
            case Trigger.CUSTOM_EVENT_VALUE:
            case Trigger.LIFE_CYCLE_APP_INIT:
            case Trigger.LIFE_CYCLE_BACKGROUND:
            case Trigger.LIFE_CYCLE_FOREGROUND:
            case Trigger.REGION_ENTER:
            case Trigger.REGION_EXIT:
            case Trigger.SCREEN_VIEW:
            default:
                return Observable.empty();
        }
    }

    /**
     * Restores compound triggers for all schedule entries.
     */
    @WorkerThread
    private void restoreCompoundTriggers() {
        final List<Observable<TriggerUpdate>> eventObservables = new ArrayList<>();

        for (final @Trigger.TriggerType int type : COMPOUND_TRIGGER_TYPES) {
            Observable<TriggerUpdate> observable = createEventObservable(type).observeOn(backgroundScheduler)
                                                                              .map(new Function<JsonSerializable, TriggerUpdate>() {
                                                                                  @NonNull
                                                                                  @Override
                                                                                  public TriggerUpdate apply(@NonNull JsonSerializable json) {
                                                                                      stateChangeTimeStamps.put(type, System.currentTimeMillis());
                                                                                      return new TriggerUpdate(dataManager.getActiveTriggerEntries(type), json, 1.0);
                                                                                  }
                                                                              });
            eventObservables.add(observable);
        }

        Observable<TriggerUpdate> eventStream = Observable.merge(eventObservables);
        this.stateObservableUpdates = Subject.create();

        this.compoundTriggerSubscription = Observable.merge(eventStream, stateObservableUpdates)
                                                     .subscribe(new Subscriber<TriggerUpdate>() {
                                                         @Override
                                                         public void onNext(@NonNull TriggerUpdate update) {
                                                             updateTriggers(update.triggerEntries, update.json, update.value);
                                                         }
                                                     });

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                subscribeStateObservables(dataManager.getScheduleEntries());
            }
        });

    }

    /**
     * Sorts a list of schedule entries by priority.
     *
     * @param entries The schedule entries.
     */
    @WorkerThread
    private void sortSchedulesByPriority(@NonNull List<ScheduleEntry> entries) {
        // Collections.singletonList and Collections.emptyList will throw an UnsupportedOperationException
        // if you try to sort the entries. Make sure we have more than 1 element (ArrayList) before sorting.
        if (entries.size() > 1) {
            Collections.sort(entries, SCHEDULE_PRIORITY_COMPARATOR);
        }
    }

    /**
     * Processes a list of schedule entries and subscribes for state updates to any compound triggers
     *
     * @param entries The schedule entries.
     */
    @WorkerThread
    private void subscribeStateObservables(@NonNull List<ScheduleEntry> entries) {
        sortSchedulesByPriority(entries);

        for (final ScheduleEntry scheduleEntry : entries) {
            subscribeStateObservables(scheduleEntry, -1);
        }
    }

    /**
     * Subscribes a schedule entry for state updates to any compound triggers
     *
     * @param entry The schedule entry.
     * @param lastStateChangeTime A timestamp to filter out state triggers. Only state changes that happened
     * after the lastStateChangeTime will update the entry's triggers.
     */
    @WorkerThread
    private void subscribeStateObservables(@NonNull final ScheduleEntry entry, final long lastStateChangeTime) {
        Observable.from(COMPOUND_TRIGGER_TYPES)
                  .filter(new Predicate<Integer>() {
                      @Override
                      public boolean apply(Integer triggerType) {
                          if (stateChangeTimeStamps.get(triggerType, startTime) <= lastStateChangeTime) {
                              return false;
                          }

                          for (TriggerEntry triggerEntry : entry.triggerEntries) {
                              if (triggerEntry.type == triggerType) {
                                  return true;
                              }
                          }
                          return false;
                      }
                  })
                  .flatMap(new Function<Integer, Observable<TriggerUpdate>>() {
                      @NonNull
                      @Override
                      public Observable<TriggerUpdate> apply(@NonNull final Integer type) {
                          return createStateObservable(type)
                                  .observeOn(backgroundScheduler)
                                  .map(new Function<JsonSerializable, TriggerUpdate>() {
                                      @NonNull
                                      @Override
                                      public TriggerUpdate apply(@NonNull JsonSerializable json) {
                                          return new TriggerUpdate(dataManager.getActiveTriggerEntries(type, entry.scheduleId), json, 1.0);
                                      }
                                  });
                      }
                  })
                  .subscribe(new Subscriber<TriggerUpdate>() {
                      @Override
                      public void onNext(@NonNull TriggerUpdate value) {
                          stateObservableUpdates.onNext(value);
                      }
                  });
    }

    /**
     * Resets the schedules that were executing back to pending execution.
     */
    @WorkerThread
    private void resetExecutingSchedules() {
        List<ScheduleEntry> entries = dataManager.getScheduleEntries(ScheduleEntry.STATE_EXECUTING,
                ScheduleEntry.STATE_WAITING_SCHEDULE_CONDITIONS);

        if (entries.isEmpty()) {
            return;
        }

        for (ScheduleEntry entry : entries) {
            entry.setExecutionState(ScheduleEntry.STATE_PREPARING_SCHEDULE);
        }

        dataManager.saveSchedules(entries);
        Logger.verbose("AutomationEngine: Schedules reset state to STATE_PREPARING_SCHEDULE: %s", entries);
    }

    /**
     * Expires active schedules past their end date and deletes finished schedules past the edit
     * grace period.
     */
    @WorkerThread
    private void cleanSchedules() {
        List<ScheduleEntry> expired = dataManager.getActiveExpiredScheduleEntries();
        List<ScheduleEntry> finished = dataManager.getScheduleEntries(ScheduleEntry.STATE_FINISHED);

        handleExpiredEntries(expired);

        Set<String> schedulesToDelete = new HashSet<>();
        for (ScheduleEntry entry : finished) {
            long finishDate;

            // If grace period is unset - use the executionStateChangeDate as finishDate to avoid unnecessarily keeping schedules around
            if (entry.getEditGracePeriod() == 0) {
                finishDate = entry.getEnd() + entry.getEditGracePeriod();
            } else {
                finishDate = entry.getExecutionStateChangeDate();
            }

            if (System.currentTimeMillis() >= finishDate) {
                schedulesToDelete.add(entry.scheduleId);
            }
        }

        if (!schedulesToDelete.isEmpty()) {
            Logger.verbose("AutomationEngine - Deleting finished schedules: %s", schedulesToDelete);
            dataManager.deleteSchedules(schedulesToDelete);
        }
    }

    /**
     * Cancel delayed schedule runnables.
     *
     * @param scheduleIds A set of identifiers to cancel.
     */
    @WorkerThread
    private void cancelScheduleAlarms(@NonNull Collection<String> scheduleIds) {
        for (ScheduleOperation alarmOperation : new ArrayList<>(pendingAlarmOperations)) {
            if (scheduleIds.contains(alarmOperation.scheduleId)) {
                alarmOperation.cancel();
                pendingAlarmOperations.remove(alarmOperation);
            }
        }
    }

    /**
     * Cancel delay schedule handler by a group.
     *
     * @param groups A schedule identifier.
     */
    @WorkerThread
    private void cancelGroupAlarms(@NonNull Collection<String> groups) {
        for (ScheduleOperation alarmOperation : new ArrayList<>(pendingAlarmOperations)) {
            if (groups.contains(alarmOperation.group)) {
                alarmOperation.cancel();
                pendingAlarmOperations.remove(alarmOperation);
            }
        }
    }

    /**
     * Cancels all pending alarms.
     */
    @WorkerThread
    private void cancelAlarms() {
        for (ScheduleOperation operation : pendingAlarmOperations) {
            operation.cancel();
        }

        pendingAlarmOperations.clear();
    }

    /**
     * Reschedule delays.
     */
    @WorkerThread
    private void restoreDelayAlarms() {
        List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(ScheduleEntry.STATE_TIME_DELAYED);
        if (scheduleEntries.isEmpty()) {
            return;
        }

        List<ScheduleEntry> schedulesToUpdate = new ArrayList<>();

        for (ScheduleEntry scheduleEntry : scheduleEntries) {
            // No delay, mark it to be executed
            if (scheduleEntry.seconds == 0) {
                continue;
            }

            long delay = TimeUnit.SECONDS.toMillis(scheduleEntry.seconds);
            long remainingDelay = scheduleEntry.getDelayFinishDate() - System.currentTimeMillis();

            if (remainingDelay <= 0) {
                scheduleEntry.setExecutionState(ScheduleEntry.STATE_PREPARING_SCHEDULE);
                schedulesToUpdate.add(scheduleEntry);
                continue;
            }

            // If remaining delay is greater than the original delay, reset the delay.
            if (remainingDelay > delay) {
                scheduleEntry.setDelayFinishDate(System.currentTimeMillis() + delay);
                schedulesToUpdate.add(scheduleEntry);
                remainingDelay = delay;
            }

            scheduleDelayAlarm(scheduleEntry, remainingDelay);
        }

        dataManager.saveSchedules(schedulesToUpdate);
    }

    /**
     * Reschedule interval operations.
     */
    @WorkerThread
    private void restoreIntervalAlarms() {
        List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(ScheduleEntry.STATE_PAUSED);
        if (scheduleEntries.isEmpty()) {
            return;
        }

        List<ScheduleEntry> schedulesToUpdate = new ArrayList<>();

        for (ScheduleEntry scheduleEntry : scheduleEntries) {
            long pausedTime = System.currentTimeMillis() - scheduleEntry.getExecutionStateChangeDate();

            if (pausedTime >= scheduleEntry.getInterval()) {
                scheduleEntry.setExecutionState(ScheduleEntry.STATE_IDLE);
                schedulesToUpdate.add(scheduleEntry);
                continue;
            }

            scheduleIntervalAlarm(scheduleEntry, pausedTime - scheduleEntry.getInterval());
        }

        dataManager.saveSchedules(schedulesToUpdate);
    }

    /**
     * Called when one of the schedule conditions changes.
     */
    private void onScheduleConditionsChanged() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(ScheduleEntry.STATE_WAITING_SCHEDULE_CONDITIONS);
                if (scheduleEntries.isEmpty()) {
                    return;
                }

                sortSchedulesByPriority(scheduleEntries);
                for (ScheduleEntry entry : scheduleEntries) {
                    attemptExecution(entry);
                }
            }
        });
    }

    /**
     * For a given event, retrieves and iterates through any relevant triggers.
     *
     * @param json The relevant event data.
     * @param type The event type.
     * @param value The trigger value to increment by.
     */
    private void onEventAdded(@NonNull final JsonSerializable json, final int type, final double value) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Logger.debug("Automation - Updating triggers with type: %s", type);
                List<TriggerEntry> triggerEntries = dataManager.getActiveTriggerEntries(type);
                if (triggerEntries.isEmpty()) {
                    return;
                }
                updateTriggers(triggerEntries, json, value);
            }
        });
    }

    /**
     * Iterates through a list of triggers that need to respond to an event or state. If a trigger goal
     * is achieved, the correlated schedule is retrieved and the action is applied. The trigger progress
     * and schedule count will then either be incremented or reset / removed.
     *
     * @param triggerEntries The triggers
     * @param json The relevant event or state data.
     * @param value The trigger value to increment by.
     */
    private void updateTriggers(@NonNull final List<TriggerEntry> triggerEntries, @NonNull final JsonSerializable json, final double value) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isPaused.get() || triggerEntries.isEmpty()) {
                    return;
                }

                Set<String> triggeredSchedules = new HashSet<>();
                Set<String> cancelledSchedules = new HashSet<>();

                List<TriggerEntry> triggersToUpdate = new ArrayList<>();

                for (TriggerEntry trigger : triggerEntries) {
                    if (trigger.jsonPredicate != null && !trigger.jsonPredicate.apply(json)) {
                        continue;
                    }

                    triggersToUpdate.add(trigger);
                    trigger.setProgress(trigger.getProgress() + value);

                    if (trigger.getProgress() >= trigger.goal) {
                        trigger.setProgress(0);

                        if (trigger.isCancellation) {
                            cancelledSchedules.add(trigger.scheduleId);
                            cancelScheduleAlarms(Collections.singletonList(trigger.scheduleId));
                        } else {
                            triggeredSchedules.add(trigger.scheduleId);
                        }
                    }
                }

                dataManager.saveTriggers(triggersToUpdate);

                if (!cancelledSchedules.isEmpty()) {
                    handleCancelledSchedules(dataManager.getScheduleEntries(cancelledSchedules));
                }

                if (!triggeredSchedules.isEmpty()) {
                    handleTriggeredSchedules(dataManager.getScheduleEntries(triggeredSchedules));
                }
            }
        });
    }

    /**
     * Processes a list of cancelled schedule entries.
     *
     * @param scheduleEntries A list of cancelled schedule entries.
     */
    @WorkerThread
    private void handleCancelledSchedules(@NonNull final List<ScheduleEntry> scheduleEntries) {
        if (scheduleEntries.isEmpty()) {
            return;
        }

        for (ScheduleEntry entry : scheduleEntries) {
            entry.setExecutionState(ScheduleEntry.STATE_IDLE);
        }

        dataManager.saveSchedules(scheduleEntries);
    }

    /**
     * Processes a list of triggered schedule entries.
     *
     * @param scheduleEntries A list of triggered schedule entries.
     */
    @WorkerThread
    private void handleTriggeredSchedules(@NonNull final List<ScheduleEntry> scheduleEntries) {
        if (isPaused.get() || scheduleEntries.isEmpty()) {
            return;
        }

        final HashSet<ScheduleEntry> schedulesToUpdate = new HashSet<>();
        final HashSet<ScheduleEntry> expiredSchedules = new HashSet<>();
        final List<ScheduleEntry> schedulesToPrepare = new ArrayList<>();

        for (final ScheduleEntry scheduleEntry : scheduleEntries) {
            if (scheduleEntry.getExecutionState() != ScheduleEntry.STATE_IDLE) {
                continue;
            }

            schedulesToUpdate.add(scheduleEntry);

            // Expired schedules
            if (scheduleEntry.isExpired()) {
                expiredSchedules.add(scheduleEntry);
                continue;
            }

            // Reset cancellation triggers
            for (TriggerEntry triggerEntry : scheduleEntry.triggerEntries) {
                if (triggerEntry.isCancellation) {
                    triggerEntry.setProgress(0);
                }
            }

            // Check for delays
            if (scheduleEntry.seconds > 0) {
                scheduleEntry.setExecutionState(ScheduleEntry.STATE_TIME_DELAYED);
                scheduleEntry.setDelayFinishDate(TimeUnit.SECONDS.toMillis(scheduleEntry.seconds) + System.currentTimeMillis());
                scheduleDelayAlarm(scheduleEntry, TimeUnit.SECONDS.toMillis(scheduleEntry.seconds));
                continue;
            }

            // IDLE -> PREPARE
            scheduleEntry.setExecutionState(ScheduleEntry.STATE_PREPARING_SCHEDULE);
            schedulesToPrepare.add(scheduleEntry);
        }

        dataManager.saveSchedules(schedulesToUpdate);
        prepareSchedules(schedulesToPrepare);
        handleExpiredEntries(expiredSchedules);
    }

    /**
     * Called to prepare the schedules after they have been triggered.
     *
     * @param entries The entries to prepare.
     */
    @WorkerThread
    private void prepareSchedules(@NonNull final List<ScheduleEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        sortSchedulesByPriority(entries);
        for (T schedule : convertEntries(entries)) {
            final String scheduleId = schedule.getId();
            driver.onPrepareSchedule(schedule, new AutomationDriver.PrepareScheduleCallback() {
                @Override
                public void onFinish(@AutomationDriver.PrepareResult final int result) {
                    backgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {

                            // Grab the updated entry
                            ScheduleEntry scheduleEntry = dataManager.getScheduleEntry(scheduleId);

                            // Make sure we are still suppose to be preparing the schedule
                            if (scheduleEntry == null || scheduleEntry.getExecutionState() != ScheduleEntry.STATE_PREPARING_SCHEDULE) {
                                return;
                            }

                            // Verify the schedule is not expired
                            if (scheduleEntry.isExpired()) {
                                handleExpiredEntry(scheduleEntry);
                                return;
                            }

                            switch (result) {
                                case AutomationDriver.PREPARE_RESULT_CANCEL:
                                    dataManager.deleteSchedule(scheduleId);
                                    notifyCancelledSchedule(Collections.singleton(scheduleEntry));
                                    break;

                                case AutomationDriver.PREPARE_RESULT_CONTINUE:
                                    scheduleEntry.setExecutionState(ScheduleEntry.STATE_WAITING_SCHEDULE_CONDITIONS);
                                    dataManager.saveSchedule(scheduleEntry);
                                    attemptExecution(scheduleEntry);
                                    break;

                                case AutomationDriver.PREPARE_RESULT_SKIP:
                                    scheduleEntry.setExecutionState(ScheduleEntry.STATE_IDLE);
                                    dataManager.saveSchedule(scheduleEntry);
                                    break;

                                case AutomationDriver.PREPARE_RESULT_PENALIZE:
                                    onScheduleFinishedExecuting(scheduleEntry);
                                    break;

                                case AutomationDriver.PREPARE_RESULT_INVALIDATE:
                                    prepareSchedules(dataManager.getScheduleEntries(Collections.singleton(scheduleId)));
                                    break;
                            }
                        }
                    });
                }
            });
        }
    }

    /**
     * Called to attempt executing a schedule entry.
     *
     * @param scheduleEntry The schedule entry.
     */
    @WorkerThread
    private void attemptExecution(@NonNull final ScheduleEntry scheduleEntry) {
        if (scheduleEntry.getExecutionState() != ScheduleEntry.STATE_WAITING_SCHEDULE_CONDITIONS) {
            Logger.error("Unable to execute schedule when state is %s scheduleID: %s", scheduleEntry.getExecutionState(), scheduleEntry.scheduleId);
            return;
        }

        // Verify the schedule is not expired
        if (scheduleEntry.isExpired()) {
            handleExpiredEntry(scheduleEntry);
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        ScheduleRunnable<Integer> runnable = new ScheduleRunnable<Integer>(scheduleEntry.scheduleId, scheduleEntry.group) {
            @Override
            public void run() {
                T schedule = null;
                result = AutomationDriver.READY_RESULT_NOT_READY;

                if (isPaused.get()) {
                    return;
                }

                if (isScheduleConditionsSatisfied(scheduleEntry)) {
                    try {
                        schedule = driver.createSchedule(scheduleEntry.scheduleId, scheduleEntry.metadata, scheduleEntry);
                        result = driver.onCheckExecutionReadiness(schedule);
                    } catch (ParseScheduleException e) {
                        Logger.error(e, "Unable to create schedule.");
                        this.exception = e;
                    }
                }
                latch.countDown();

                if (AutomationDriver.READY_RESULT_CONTINUE == result && schedule != null) {
                    driver.onExecuteTriggeredSchedule(schedule, new ScheduleExecutorCallback(scheduleEntry.scheduleId));
                }
            }
        };

        this.mainHandler.post(runnable);

        try {
            latch.await();
        } catch (InterruptedException ex) {
            Logger.error(ex, "Failed to execute schedule. ");
            Thread.currentThread().interrupt();
        }

        if (runnable.exception != null) {
            Logger.error("Failed to check conditions. Deleting schedule: %s", scheduleEntry.scheduleId);
            dataManager.deleteSchedule(scheduleEntry.scheduleId);
            notifyCancelledSchedule(Collections.singleton(scheduleEntry));
        } else {
            int result = runnable.result == null ? AutomationDriver.READY_RESULT_NOT_READY : runnable.result;
            switch (result) {
                case AutomationDriver.READY_RESULT_INVALIDATE:
                    Logger.verbose("AutomationEngine - Schedule invalidated: %s", scheduleEntry.scheduleId);
                    scheduleEntry.setExecutionState(ScheduleEntry.STATE_PREPARING_SCHEDULE);
                    dataManager.saveSchedule(scheduleEntry);
                    prepareSchedules(dataManager.getScheduleEntries(Collections.singleton(scheduleEntry.scheduleId)));
                    break;

                case AutomationDriver.READY_RESULT_CONTINUE:
                    Logger.verbose("AutomationEngine - Schedule executing: %s", scheduleEntry.scheduleId);
                    scheduleEntry.setExecutionState(ScheduleEntry.STATE_EXECUTING);
                    dataManager.saveSchedule(scheduleEntry);
                    break;

                case AutomationDriver.READY_RESULT_NOT_READY:
                    Logger.verbose("AutomationEngine - Schedule not ready for execution: %s", scheduleEntry.scheduleId);
                    break;
            }
        }
    }

    /**
     * Helper method to notify the schedule listener for expired schedule entries.
     *
     * @param entries Expired schedule entries.
     */
    @WorkerThread
    private void notifyExpiredSchedules(@NonNull Collection<ScheduleEntry> entries) {
        notifyHelper(convertEntries(entries), new Notify<T>() {
            @Override
            public void notify(@NonNull ScheduleListener<T> listener, @NonNull T schedule) {
                listener.onScheduleExpired(schedule);
            }
        });
    }

    /**
     * Helper method to notify the schedule listener for cancelled schedule entries.
     *
     * @param entries Cancelled schedule entries.
     */
    @WorkerThread
    private void notifyCancelledSchedule(@NonNull Collection<ScheduleEntry> entries) {
        notifyHelper(convertEntries(entries), new Notify<T>() {
            @Override
            public void notify(@NonNull ScheduleListener<T> listener, @NonNull T schedule) {
                listener.onScheduleCancelled(schedule);
            }
        });
    }

    /**
     * Helper method to notify the schedule listener for schedule who reached its execution limit.
     *
     * @param entry The schedule.
     */
    @WorkerThread
    private void notifyScheduleLimitReached(@NonNull ScheduleEntry entry) {
        notifyHelper(convertEntries(Collections.singleton(entry)), new Notify<T>() {
            @Override
            public void notify(@NonNull ScheduleListener<T> listener, @NonNull T schedule) {
                listener.onScheduleLimitReached(schedule);
            }
        });
    }

    /**
     * Helper method to notify the schedule listener for new schedules.
     *
     * @param schedules The new schedules.
     */
    @WorkerThread
    private void notifyNewSchedule(@NonNull final List<T> schedules) {
        notifyHelper(schedules, new Notify<T>() {
            @Override
            public void notify(@NonNull ScheduleListener<T> listener, @NonNull T schedule) {
                listener.onNewSchedule(schedule);
            }
        });
    }

    /**
     * Called to notify the schedule listener.
     *
     * @param <T> The schedule.
     */
    private interface Notify<T> {

        void notify(@NonNull ScheduleListener<T> listener, @NonNull T schedule);

    }

    /**
     * Notify helper. Calls the listener on the main thread and handles any null checks.
     *
     * @param entries The entries.
     * @param notify The notify method.
     */
    @WorkerThread
    private void notifyHelper(@NonNull final Collection<T> entries, @NonNull final Notify<T> notify) {
        if (this.scheduleListener == null || entries.isEmpty()) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (T schedule : entries) {
                    ScheduleListener<T> listener = AutomationEngine.this.scheduleListener;
                    if (listener != null) {
                        notify.notify(listener, schedule);
                    }
                }
            }
        });
    }

    /**
     * Called when a schedule is finished executing.
     *
     * @param scheduleEntry The schedule entry.
     */
    @WorkerThread
    private void onScheduleFinishedExecuting(@Nullable final ScheduleEntry scheduleEntry) {
        if (scheduleEntry == null) {
            return;
        }

        Logger.verbose("AutomationEngine - Schedule finished: %s", scheduleEntry.scheduleId);

        // Update the count
        scheduleEntry.setCount(scheduleEntry.getCount() + 1);

        boolean isOverLimit = scheduleEntry.isOverLimit();

        // Expired
        if (scheduleEntry.isExpired()) {
            handleExpiredEntry(scheduleEntry);
            return;
        }

        if (isOverLimit) {
            // At limit
            scheduleEntry.setExecutionState(ScheduleEntry.STATE_FINISHED);
            notifyScheduleLimitReached(scheduleEntry);

            // Delete the schedule if its finished and no edit grace period is defined
            if (scheduleEntry.getEditGracePeriod() <= 0) {
                dataManager.deleteSchedule(scheduleEntry.scheduleId);
                return;
            }

        } else if (scheduleEntry.getInterval() > 0) {
            // Execution interval
            scheduleEntry.setExecutionState(ScheduleEntry.STATE_PAUSED);
            scheduleIntervalAlarm(scheduleEntry, scheduleEntry.getInterval());
        } else {
            // Back to idle
            scheduleEntry.setExecutionState(ScheduleEntry.STATE_IDLE);
        }

        dataManager.saveSchedule(scheduleEntry);
    }

    /**
     * Schedules a delay for a schedule entry.
     *
     * @param scheduleEntry The schedule entry.
     * @param delay The delay in milliseconds.
     */
    private void scheduleDelayAlarm(@NonNull final ScheduleEntry scheduleEntry, long delay) {
        final ScheduleOperation operation = new ScheduleOperation(scheduleEntry.scheduleId, scheduleEntry.group) {
            @Override
            protected void onRun() {
                ScheduleEntry scheduleEntry = dataManager.getScheduleEntry(scheduleId);
                if (scheduleEntry != null && scheduleEntry.getExecutionState() == ScheduleEntry.STATE_TIME_DELAYED) {

                    // Expired
                    if (scheduleEntry.isExpired()) {
                        handleExpiredEntry(scheduleEntry);
                        return;
                    }

                    // Delayed => Preparing
                    scheduleEntry.setExecutionState(ScheduleEntry.STATE_PREPARING_SCHEDULE);
                    dataManager.saveSchedule(scheduleEntry);

                    prepareSchedules(Collections.singletonList(scheduleEntry));
                }
            }
        };

        operation.addOnRun(new Runnable() {
            @Override
            public void run() {
                pendingAlarmOperations.remove(operation);
            }
        });
        pendingAlarmOperations.add(operation);
        scheduler.schedule(delay, operation);
    }

    /**
     * Schedules an interval alarm for a schedule.
     *
     * @param scheduleEntry The schedule entry.
     * @param interval The interval in milliseconds.
     */
    @WorkerThread
    private void scheduleIntervalAlarm(@NonNull ScheduleEntry scheduleEntry, long interval) {
        final ScheduleOperation operation = new ScheduleOperation(scheduleEntry.scheduleId, scheduleEntry.group) {
            @Override
            protected void onRun() {
                ScheduleEntry scheduleEntry = dataManager.getScheduleEntry(scheduleId);
                if (scheduleEntry == null || scheduleEntry.getExecutionState() != ScheduleEntry.STATE_PAUSED) {
                    return;
                }

                // Expired
                if (scheduleEntry.isExpired()) {
                    handleExpiredEntry(scheduleEntry);
                    return;
                }

                long pauseStartTime = scheduleEntry.getExecutionStateChangeDate();

                // Paused => Idle
                scheduleEntry.setExecutionState(ScheduleEntry.STATE_IDLE);
                dataManager.saveSchedule(scheduleEntry);

                subscribeStateObservables(scheduleEntry, pauseStartTime);
            }
        };

        operation.addOnRun(new Runnable() {
            @Override
            public void run() {
                pendingAlarmOperations.remove(operation);
            }
        });

        pendingAlarmOperations.add(operation);
        scheduler.schedule(interval, operation);
    }

    /**
     * Converts a list of generic entries to typed entries.
     *
     * @param entries The list of entries to convert.
     * @return The list of converted entries.
     */
    @NonNull
    private List<T> convertEntries(@NonNull Collection<ScheduleEntry> entries) {
        List<T> schedules = new ArrayList<>();
        for (ScheduleEntry entry : entries) {
            try {
                schedules.add(driver.createSchedule(entry.scheduleId, entry.metadata, entry));
            } catch (Exception e) {
                Logger.error(e, "Unable to create schedule.");
                cancel(Collections.singletonList(entry.scheduleId));
            }
        }

        return schedules;
    }

    /**
     * Checks if the schedule entry's conditions are met.
     *
     * @param scheduleEntry The schedule entry.
     * @return {@code true} if the conditions are met, otherwise {@code false}.
     */
    @MainThread
    private boolean isScheduleConditionsSatisfied(@NonNull ScheduleEntry scheduleEntry) {
        if (scheduleEntry.screens != null && !scheduleEntry.screens.isEmpty()) {
            if (!scheduleEntry.screens.contains(screen)) {
                return false;
            }
        }

        if (scheduleEntry.regionId != null && !scheduleEntry.regionId.equals(regionId)) {
            return false;
        }

        switch (scheduleEntry.appState) {
            case ScheduleDelay.APP_STATE_FOREGROUND:
                if (!activityMonitor.isAppForegrounded()) {
                    return false;
                }
                break;

            case ScheduleDelay.APP_STATE_BACKGROUND:
                if (activityMonitor.isAppForegrounded()) {
                    return false;
                }
                break;

            case ScheduleDelay.APP_STATE_ANY:
                break;
        }

        return true;
    }

    /**
     * Sets the execution state, saves the schedule, and notifies any listeners of the
     * expired schedule.
     *
     * @param scheduleEntry The expired schedule entry.
     */
    private void handleExpiredEntry(@NonNull ScheduleEntry scheduleEntry) {
        handleExpiredEntries(Collections.singleton(scheduleEntry));
    }

    /**
     * Sets the execution state, saves the schedule, and notifies any listeners of the
     * expired schedules.
     *
     * @param entries The expired schedule entries.
     */
    private void handleExpiredEntries(@NonNull Collection<ScheduleEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        List<String> schedulesToDelete = new ArrayList<>();
        List<ScheduleEntry> schedulesToUpdate = new ArrayList<>();

        for (ScheduleEntry scheduleEntry : entries) {
            scheduleEntry.setExecutionState(ScheduleEntry.STATE_FINISHED);
            if (scheduleEntry.getEditGracePeriod() >= 0) {
                schedulesToUpdate.add(scheduleEntry);
            } else {
                schedulesToDelete.add(scheduleEntry.scheduleId);
            }
        }

        dataManager.saveSchedules(schedulesToUpdate);
        dataManager.deleteSchedules(schedulesToDelete);
        notifyExpiredSchedules(entries);
    }

    private class ScheduleOperation extends CancelableOperation {

        final String scheduleId;
        final String group;

        ScheduleOperation(String scheduleId, String group) {
            super(backgroundHandler.getLooper());
            this.scheduleId = scheduleId;
            this.group = group;
        }

    }

    private abstract class ScheduleRunnable<ReturnType> implements Runnable {

        final String scheduleId;
        final String group;
        ReturnType result;
        Exception exception;

        ScheduleRunnable(String scheduleId, String group) {
            this.scheduleId = scheduleId;
            this.group = group;
        }

    }

    private class ScheduleExecutorCallback implements AutomationDriver.ExecutionCallback {

        private final String scheduleId;

        ScheduleExecutorCallback(String scheduleId) {
            this.scheduleId = scheduleId;
        }

        @Override
        public void onFinish() {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    onScheduleFinishedExecuting(dataManager.getScheduleEntry(scheduleId));
                }
            });
        }

    }

    /**
     * Model object representing trigger update data.
     */
    private static class TriggerUpdate {

        final List<TriggerEntry> triggerEntries;
        final JsonSerializable json;
        final double value;

        TriggerUpdate(final List<TriggerEntry> triggerEntries, final JsonSerializable json, final double value) {
            this.triggerEntries = triggerEntries;
            this.json = json;
            this.value = value;
        }

    }

    /**
     * Engine builder.
     *
     * @param <T> The schedule type.
     */
    public static class Builder<T extends Schedule> {

        private long limit;
        private ActivityMonitor activityMonitor;
        private AutomationDriver<T> driver;
        private AutomationDataManager dataManager;
        private Analytics analytics;
        private OperationScheduler scheduler;

        /**
         * Sets the schedule limit.
         *
         * @param limit The schedule limit.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setScheduleLimit(long limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets the {@link ActivityMonitor}.
         *
         * @param activityMonitor The {@link ActivityMonitor}.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setActivityMonitor(@NonNull ActivityMonitor activityMonitor) {
            this.activityMonitor = activityMonitor;
            return this;
        }

        /**
         * Sets the {@link Analytics} instance.
         *
         * @param analytics The {@link Analytics} instance..
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setAnalytics(@NonNull Analytics analytics) {
            this.analytics = analytics;
            return this;
        }

        /**
         * Sets the {@link AutomationDriver<T>}.
         *
         * @param driver The engine's driver.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setDriver(@NonNull AutomationDriver<T> driver) {
            this.driver = driver;
            return this;
        }

        /**
         * Sets the {@link AutomationDataManager}.
         *
         * @param dataManager The data manager.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setDataManager(@NonNull AutomationDataManager dataManager) {
            this.dataManager = dataManager;
            return this;
        }

        /**
         * Sets the operation scheduler.
         *
         * @param scheduler The operation scheduler.
         * @return The builder instance.
         */
        @NonNull
        public Builder<T> setOperationScheduler(@NonNull OperationScheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Builds the engine.
         *
         * @return An automation engine.
         */
        @NonNull
        public AutomationEngine<T> build() {
            Checks.checkNotNull(dataManager, "Missing data manager");
            Checks.checkNotNull(analytics, "Missing analytics");
            Checks.checkNotNull(activityMonitor, "Missing activity monitor");
            Checks.checkNotNull(driver, "Missing driver");
            Checks.checkNotNull(scheduler, "Missing scheduler");
            Checks.checkArgument(limit > 0, "Missing schedule limit");

            return new AutomationEngine<>(this);
        }

    }

}
