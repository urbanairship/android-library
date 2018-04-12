/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.util.SparseArray;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.OperationScheduler;
import com.urbanairship.PendingResult;
import com.urbanairship.Predicate;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AnalyticsListener;
import com.urbanairship.analytics.CustomEvent;
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
import com.urbanairship.util.Checks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationEngine<T extends Schedule> {

    private final List<Integer> COMPOUND_TRIGGER_TYPES = Arrays.asList(Trigger.ACTIVE_SESSION, Trigger.VERSION);

    private final AutomationDataManager dataManager;
    private final ActivityMonitor activityMonitor;
    private final AutomationDriver<T> driver;
    private final Analytics analytics;
    private final long scheduleLimit;
    private final OperationScheduler scheduler;
    private boolean isStarted;
    private Handler backgroundHandler;
    private Handler mainHandler;
    private ScheduleExpiryListener<T> expiryListener;
    private AtomicBoolean isPaused = new AtomicBoolean(false);

    private long startTime;
    private SparseArray<Long> stateChangeTimeStamps = new SparseArray<>();

    @VisibleForTesting
    HandlerThread backgroundThread;
    private final List<ScheduleOperation> pendingAlarmOperations = new ArrayList<>();

    private String screen;
    private String regionId;

    private Subject<TriggerUpdate> stateObservableUpdates;
    private Subscription compoundTriggerSubscription;
    private Scheduler backgroundScheduler;

    private final ActivityMonitor.Listener activityListener = new ActivityMonitor.SimpleListener() {
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
        public void onRegionEventAdded(RegionEvent regionEvent) {
            regionId = regionEvent.toJsonValue().getMap().opt("region_id").getString();
            int type = regionEvent.getBoundaryEvent() == RegionEvent.BOUNDARY_EVENT_ENTER ? Trigger.REGION_ENTER : Trigger.REGION_EXIT;
            onEventAdded(regionEvent.toJsonValue(), type, 1.00);
            onScheduleConditionsChanged();
        }

        @Override
        public void onCustomEventAdded(CustomEvent customEvent) {
            onEventAdded(customEvent.toJsonValue(), Trigger.CUSTOM_EVENT_COUNT, 1.00);

            BigDecimal eventValue = customEvent.getEventValue();
            if (eventValue != null) {
                onEventAdded(customEvent.toJsonValue(), Trigger.CUSTOM_EVENT_VALUE, eventValue.doubleValue());
            }
        }

        @Override
        public void onScreenTracked(String screenName) {
            screen = screenName;
            onEventAdded(JsonValue.wrap(screenName), Trigger.SCREEN_VIEW, 1.00);
            onScheduleConditionsChanged();
        }
    };

    /**
     * Expired schedule listener.
     */
    public interface ScheduleExpiryListener<T extends Schedule> {
        /**
         * Called when a schedule is expired.
         *
         * @param schedule The expired schedule.
         */
        void onScheduleExpired(T schedule);
    }

    /**
     * Default constructor.
     *
     * @param builder The builder instance.
     */
    private AutomationEngine(Builder<T> builder) {
        this.dataManager = builder.dataManager;
        this.activityMonitor = builder.activityMonitor;
        this.analytics = builder.analytics;
        this.driver = builder.driver;
        this.scheduleLimit = builder.limit;
        this.scheduler = builder.scheduler;
        this.backgroundThread = new HandlerThread("automation");
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

        activityMonitor.addListener(activityListener);
        analytics.addAnalyticsListener(analyticsListener);

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                resetExecutingSchedules();
                restoreDelayAlarms();
                restoreIntervalAlarms();
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
        activityMonitor.removeListener(activityListener);
        analytics.removeAnalyticsListener(analyticsListener);
        cancelAlarms();
        backgroundThread.quit();
        isStarted = false;
    }

    /**
     * Schedules a single action schedule.
     *
     * @param scheduleInfo The {@link ScheduleInfo} instance.
     * @return A pending result.
     */
    public PendingResult<T> schedule(@NonNull final ScheduleInfo scheduleInfo) {
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
                ScheduleEntry entry = new ScheduleEntry(scheduleId, scheduleInfo);

                List<ScheduleEntry> entries = Collections.singletonList(entry);
                dataManager.saveSchedules(entries);
                subscribeStateObservables(entries);

                List<T> result = convertEntries(entries);
                Logger.verbose("AutomationEngine - Scheduled entries: " + result);
                pendingResult.setResult(result.size() > 0 ? result.get(0) : null);


            }
        });

        return pendingResult;
    }

    /**
     * Schedules a list of action schedules.
     *
     * @param scheduleInfos A list of {@link ScheduleInfo}.
     * @return A pending result.
     */
    public PendingResult<List<T>> schedule(@NonNull final List<? extends ScheduleInfo> scheduleInfos) {
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
                    entries.add(new ScheduleEntry(scheduleId, info));
                }


                dataManager.saveSchedules(entries);
                subscribeStateObservables(entries);

                List<T> result = convertEntries(entries);
                Logger.verbose("AutomationEngine - Scheduled entries: " + result);
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
    public PendingResult<Void> cancel(@NonNull final Collection<String> ids) {
        final PendingResult<Void> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                dataManager.deleteSchedules(ids);
                cancelScheduleAlarms(ids);

                Logger.verbose("AutomationEngine - Cancelled schedules: " + ids);
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
    public PendingResult<Boolean> cancelGroup(@NonNull final String group) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cancelGroupAlarms(Collections.singletonList(group));

                if (dataManager.deleteGroup(group)) {
                    Logger.verbose("AutomationEngine - Cancelled schedule group: " + group);
                    pendingResult.setResult(true);
                } else {
                    Logger.verbose("AutomationEngine - Failed to cancel schedule group: " + group);
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
    public PendingResult<Void> cancelGroups(@NonNull final Collection<String> groups) {
        final PendingResult<Void> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cancelGroupAlarms(groups);
                dataManager.deleteGroups(groups);
                Logger.verbose("AutomationEngine - Canceled schedule groups: " + groups);
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
    public PendingResult<Void> cancelAll() {
        final PendingResult<Void> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
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
    public PendingResult<T> getSchedule(@NonNull final String scheduleId) {
        final PendingResult<T> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
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
    public PendingResult<Collection<T>> getSchedules(@NonNull final Set<String> scheduleIds) {
        final PendingResult<Collection<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
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
    public PendingResult<Collection<T>> getSchedules(final String group) {
        final PendingResult<Collection<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
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
    public PendingResult<T> editSchedule(@NonNull final String scheduleId, @NonNull final ScheduleEdits edits) {
        final PendingResult<T> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                ScheduleEntry entry = dataManager.getScheduleEntry(scheduleId);

                if (entry == null) {
                    Logger.error("AutomationEngine - Schedule no longer exists. Unable to edit: " + scheduleId);
                    pendingResult.setResult(null);
                    return;
                }

                entry.applyEdits(edits);

                boolean subscribeForStateChanges = false;
                long stateChangeTimeStamp = -1;

                boolean isOverLimit = entry.getLimit() > 0 && entry.getCount() >= entry.getLimit();
                boolean isExpired = entry.getEnd() >= 0 && entry.getEnd() < System.currentTimeMillis();

                // Check if the schedule needs to be rehabilitated or finished due to the edits
                if (entry.getExecutionState() == ScheduleEntry.STATE_FINISHED && !isOverLimit && !isExpired) {
                    subscribeForStateChanges = true;
                    stateChangeTimeStamp = entry.getExecutionStateChangeDate();
                    entry.setExecutionState(ScheduleEntry.STATE_IDLE);
                } else if (entry.getExecutionState() != ScheduleEntry.STATE_FINISHED && (isOverLimit || isExpired)) {
                    entry.setExecutionState(ScheduleEntry.STATE_FINISHED);
                }

                dataManager.saveSchedules(Collections.singletonList(entry));

                if (subscribeForStateChanges) {
                    subscribeStateObservables(entry, stateChangeTimeStamp);
                }

                List<T> result = convertEntries(dataManager.getScheduleEntries(Collections.singleton(scheduleId)));
                Logger.error("AutomationEngine - Updated schedule: " + result);
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
     * Sets the schedule expiry listener.
     *
     * @param expiryListener The listener.
     */
    public void setScheduleExpiryListener(ScheduleExpiryListener<T> expiryListener) {
        synchronized (this) {
            this.expiryListener = expiryListener;
        }
    }

    /**
     * Creates an event observables for compound triggers.
     *
     * @param type The trigger type
     * @return The corresponding observable, or an empty observable in case no match was found.
     */
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
                                                                                  @Override
                                                                                  public TriggerUpdate apply(JsonSerializable json) {
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
                                                         public void onNext(TriggerUpdate update) {
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
    private void sortSchedulesByPriority(List<ScheduleEntry> entries) {
        // Collections.singletonList and Collections.emptyList will throw an UnsupportedOperationException
        // if you try to sort the entries. Make sure we have more than 1 element (ArrayList) before sorting.
        if (entries.size() > 1) {
            Collections.sort(entries, new Comparator<ScheduleEntry>() {
                @Override
                public int compare(ScheduleEntry lh, ScheduleEntry rh) {
                    return lh.getPriority() - rh.getPriority();
                }
            });
        }
    }

    /**
     * Processes a list of schedule entries and subscribes for state updates to any compound triggers
     *
     * @param entries The schedule entries.
     */
    @WorkerThread
    private void subscribeStateObservables(List<ScheduleEntry> entries) {
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
    private void subscribeStateObservables(final ScheduleEntry entry, final long lastStateChangeTime) {
        if (entry.getExecutionState() != ScheduleEntry.STATE_IDLE &&
                entry.getExecutionState() != ScheduleEntry.STATE_PENDING_EXECUTION) {
            return;
        }

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
                      @Override
                      public Observable<TriggerUpdate> apply(final Integer type) {
                          return createStateObservable(type)
                                  .observeOn(backgroundScheduler)
                                  .map(new Function<JsonSerializable, TriggerUpdate>() {
                                      @Override
                                      public TriggerUpdate apply(JsonSerializable json) {
                                          return new TriggerUpdate(dataManager.getActiveTriggerEntries(type, entry.scheduleId), json, 1.0);
                                      }
                                  });
                      }
                  })
                  .subscribe(new Subscriber<TriggerUpdate>() {
                      @Override
                      public void onNext(TriggerUpdate value) {
                          stateObservableUpdates.onNext(value);
                      }
                  });
    }

    /**
     * Resets the schedules that were executing back to pending execution.
     */
    @WorkerThread
    private void resetExecutingSchedules() {
        List<ScheduleEntry> entries = dataManager.getScheduleEntries(ScheduleEntry.STATE_EXECUTING);
        if (entries.isEmpty()) {
            return;
        }

        for (ScheduleEntry entry : entries) {
            entry.setExecutionState(ScheduleEntry.STATE_PENDING_EXECUTION);
        }

        dataManager.saveSchedules(entries);
        Logger.verbose("AutomationEngine: Schedules reset to pending execution: " + entries);
    }

    /**
     * Expires active schedules past their end date and deletes finished schedules past the edit
     * grace period.
     */
    @WorkerThread
    private void cleanSchedules() {
        List<ScheduleEntry> expired = dataManager.getActiveExpiredScheduleEntries();
        List<ScheduleEntry> finished = dataManager.getScheduleEntries(ScheduleEntry.STATE_FINISHED);

        Set<ScheduleEntry> update = new HashSet<>();
        Set<String> delete = new HashSet<>();

        for (ScheduleEntry entry : expired) {
            if (entry.getEditGracePeriod() > 0) {
                entry.setExecutionState(ScheduleEntry.STATE_FINISHED);
                update.add(entry);
            } else {
                delete.add(entry.scheduleId);
            }
        }

        notifyExpiredSchedules(expired);

        for (ScheduleEntry entry : finished) {
            if (System.currentTimeMillis() >= entry.getExecutionStateChangeDate() + entry.getEditGracePeriod()) {
                delete.add(entry.scheduleId);
            }
        }

        if (!update.isEmpty()) {
            Logger.verbose("AutomationEngine - Updating expired schedules to finished state: " + update);
            dataManager.saveSchedules(update);
        }

        if (!delete.isEmpty()) {
            Logger.verbose("AutomationEngine - Deleting finished schedules: " + delete);
            dataManager.deleteSchedules(delete);
        }
    }

    /**
     * Cancel delayed schedule runnables.
     *
     * @param scheduleIds A set of identifiers to cancel.
     */
    @WorkerThread
    private void cancelScheduleAlarms(Collection<String> scheduleIds) {
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
    private void cancelGroupAlarms(Collection<String> groups) {
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
        List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(ScheduleEntry.STATE_PENDING_EXECUTION);
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
            long remainingDelay = scheduleEntry.getPendingExecutionDate() - System.currentTimeMillis();

            if (remainingDelay <= 0) {
                continue;
            }

            // If remaining delay is greater than the original delay, reset the delay.
            if (remainingDelay > delay) {
                scheduleEntry.setPendingExecutionDate(System.currentTimeMillis() + delay);
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
                List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(ScheduleEntry.STATE_PENDING_EXECUTION);
                if (scheduleEntries.isEmpty()) {
                    return;
                }


                handleTriggeredSchedules(scheduleEntries);
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
    private void onEventAdded(final JsonSerializable json, final int type, final double value) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Logger.debug("Automation - Updating triggers with type: " + type);
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
    private void updateTriggers(@NonNull final List<TriggerEntry> triggerEntries, final JsonSerializable json, final double value) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isPaused.get() || triggerEntries.isEmpty()) {
                    return;
                }

                Set<String> triggeredSchedules = new HashSet<>();
                Set<String> cancelledSchedules = new HashSet<>();

                Map<String, List<TriggerEntry>> triggersToUpdate = new HashMap<>();

                for (TriggerEntry trigger : triggerEntries) {
                    if ((json != null && (trigger.jsonPredicate != null && !trigger.jsonPredicate.apply(json)))) {
                        continue;
                    }

                    if (!triggersToUpdate.containsKey(trigger.scheduleId)) {
                        triggersToUpdate.put(trigger.scheduleId, new ArrayList<TriggerEntry>());
                    }

                    triggersToUpdate.get(trigger.scheduleId).add(trigger);
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

                if (!cancelledSchedules.isEmpty()) {
                    triggeredSchedules.removeAll(cancelledSchedules);
                    List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(cancelledSchedules);
                    for (ScheduleEntry entry : scheduleEntries) {
                        entry.setPendingExecutionDate(-1);
                        entry.setExecutionState(ScheduleEntry.STATE_IDLE);
                    }

                    dataManager.saveSchedules(scheduleEntries);
                }

                if (!triggeredSchedules.isEmpty()) {
                    List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(triggeredSchedules);

                    for (String deletedSchedule : handleTriggeredSchedules(scheduleEntries)) {
                        triggersToUpdate.remove(deletedSchedule);
                    }
                }

                List<TriggerEntry> updatedTriggers = new ArrayList<>();
                for (Map.Entry<String, List<TriggerEntry>> entry : triggersToUpdate.entrySet()) {
                    updatedTriggers.addAll(entry.getValue());
                }

                dataManager.saveTriggers(updatedTriggers);
            }
        });
    }

    /**
     * Processes a list of triggered schedule entries.
     *
     * @param scheduleEntries A list of triggered schedule entries.
     * @return A set of deleted schedule IDs.
     */
    @WorkerThread
    private Set<String> handleTriggeredSchedules(final List<ScheduleEntry> scheduleEntries) {
        if (isPaused.get() || scheduleEntries.isEmpty()) {
            return new HashSet<>();
        }

        final HashSet<ScheduleEntry> expiredScheduleEntries = new HashSet<>();
        final HashSet<String> schedulesToDelete = new HashSet<>();
        final HashSet<ScheduleEntry> schedulesToUpdate = new HashSet<>();

        sortSchedulesByPriority(scheduleEntries);

        for (final ScheduleEntry scheduleEntry : scheduleEntries) {
            // Expired schedules
            if (scheduleEntry.getEnd() >= 0 && scheduleEntry.getEnd() < System.currentTimeMillis()) {
                expiredScheduleEntries.add(scheduleEntry);
                scheduleEntry.setExecutionState(ScheduleEntry.STATE_FINISHED);

                if (scheduleEntry.getEditGracePeriod() <= 0) {
                    schedulesToDelete.add(scheduleEntry.scheduleId);
                } else {
                    schedulesToUpdate.add(scheduleEntry);
                }

                continue;
            }

            // Idle => Pending
            if (scheduleEntry.getExecutionState() == ScheduleEntry.STATE_IDLE) {
                schedulesToUpdate.add(scheduleEntry);

                for (TriggerEntry triggerEntry : scheduleEntry.triggerEntries) {
                    if (triggerEntry.isCancellation) {
                        triggerEntry.setProgress(0);
                    }
                }

                scheduleEntry.setExecutionState(ScheduleEntry.STATE_PENDING_EXECUTION);
                if (scheduleEntry.seconds > 0) {
                    scheduleEntry.setPendingExecutionDate(TimeUnit.SECONDS.toMillis(scheduleEntry.seconds) + System.currentTimeMillis());
                    scheduleDelayAlarm(scheduleEntry, TimeUnit.SECONDS.toMillis(scheduleEntry.seconds));
                    continue;
                }
            }

            final CountDownLatch latch = new CountDownLatch(1);
            ScheduleRunnable<Boolean> runnable = new ScheduleRunnable<Boolean>(scheduleEntry.scheduleId, scheduleEntry.group) {
                @Override
                public void run() {
                    T schedule = null;
                    result = false;

                    if (isPaused.get()) {
                        return;
                    }

                    if (isScheduleConditionsSatisfied(scheduleEntry)) {
                        try {
                            schedule = driver.createSchedule(scheduleEntry.scheduleId, scheduleEntry);

                            if (driver.isScheduleReadyToExecute(schedule)) {
                                result = true;
                            }
                        } catch (ParseScheduleException e) {
                            Logger.error("Unable to create schedule.", e);
                            this.exception = e;
                        }
                    }
                    latch.countDown();

                    if (result && schedule != null) {
                        driver.onExecuteTriggeredSchedule(schedule, new ScheduleExecutorCallback(scheduleEntry.scheduleId));
                    }
                }
            };

            this.mainHandler.post(runnable);

            try {
                latch.await();
            } catch (InterruptedException ex) {
                Logger.error("Failed to execute schedule. ", ex);
            }

            if (runnable.exception != null) {
                schedulesToDelete.add(scheduleEntry.scheduleId);
                schedulesToUpdate.remove(scheduleEntry);
            } else if (runnable.result) {
                scheduleEntry.setExecutionState(ScheduleEntry.STATE_EXECUTING);
                schedulesToUpdate.add(scheduleEntry);
            }
        }

        notifyExpiredSchedules(expiredScheduleEntries);

        dataManager.saveSchedules(schedulesToUpdate);
        dataManager.deleteSchedules(schedulesToDelete);

        return schedulesToDelete;
    }

    /**
     * Helper method to notify the expiry listener for expired schedule entries.
     *
     * @param expiredScheduleEntries Expired schedule entries.
     */
    @WorkerThread
    private void notifyExpiredSchedules(@NonNull Collection<ScheduleEntry> expiredScheduleEntries) {
        final List<T> schedules = convertEntries(expiredScheduleEntries);
        if (schedules.isEmpty()) {
            return;
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (T schedule : schedules) {
                    synchronized (this) {
                        if (expiryListener != null) {
                            expiryListener.onScheduleExpired(schedule);
                        }
                    }
                }
            }
        });
    }

    /**
     * Called when a schedule is finished executing.
     *
     * @param scheduleId The schedule ID.
     */
    private void onScheduleFinishedExecuting(final String scheduleId) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                ScheduleEntry scheduleEntry = dataManager.getScheduleEntry(scheduleId);

                if (scheduleEntry == null) {
                    return;
                }

                Logger.verbose("AutomationEngine - Schedule finished: " + scheduleId);
                scheduleEntry.setPendingExecutionDate(-1);
                scheduleEntry.setCount(scheduleEntry.getCount() + 1);

                boolean deleteSchedule = false;

                if (scheduleEntry.getLimit() > 0 && scheduleEntry.getCount() >= scheduleEntry.getLimit()) {
                    scheduleEntry.setExecutionState(ScheduleEntry.STATE_FINISHED);
                    deleteSchedule = scheduleEntry.getEditGracePeriod() <= 0;
                } else if (scheduleEntry.getExecutionState() != ScheduleEntry.STATE_FINISHED) {
                    if (scheduleEntry.getInterval() > 0) {
                        scheduleEntry.setExecutionState(ScheduleEntry.STATE_PAUSED);
                        scheduleIntervalAlarm(scheduleEntry, scheduleEntry.getInterval());
                    } else {
                        scheduleEntry.setExecutionState(ScheduleEntry.STATE_IDLE);
                    }
                }

                if (deleteSchedule) {
                    Logger.verbose("AutomationEngine - Deleting schedule: " + scheduleId);
                    dataManager.deleteSchedule(scheduleId);
                } else {
                    dataManager.saveSchedules(Collections.singletonList(scheduleEntry));
                }
            }
        });
    }


    /**
     * Schedules a delay for a schedule entry.
     *
     * @param scheduleEntry The schedule entry.
     * @param delay The delay in milliseconds.
     */
    private void scheduleDelayAlarm(final ScheduleEntry scheduleEntry, long delay) {

        final ScheduleOperation operation = new ScheduleOperation(scheduleEntry.scheduleId, scheduleEntry.group) {
            @Override
            protected void onRun() {
                ScheduleEntry scheduleEntry = dataManager.getScheduleEntry(scheduleId);
                if (scheduleEntry != null && scheduleEntry.getExecutionState() == ScheduleEntry.STATE_PENDING_EXECUTION) {
                    handleTriggeredSchedules(Collections.singletonList(scheduleEntry));
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
    private void scheduleIntervalAlarm(ScheduleEntry scheduleEntry, long interval) {
        final ScheduleOperation operation = new ScheduleOperation(scheduleEntry.scheduleId, scheduleEntry.group) {
            @Override
            protected void onRun() {
                ScheduleEntry scheduleEntry = dataManager.getScheduleEntry(scheduleId);
                if (scheduleEntry != null && scheduleEntry.getExecutionState() == ScheduleEntry.STATE_PAUSED) {
                    long pauseStartTime = scheduleEntry.getExecutionStateChangeDate();

                    scheduleEntry.setExecutionState(ScheduleEntry.STATE_IDLE);
                    dataManager.saveSchedules(Collections.singletonList(scheduleEntry));

                    subscribeStateObservables(scheduleEntry, pauseStartTime);
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
        scheduler.schedule(interval, operation);
    }

    /**
     * Converts a list of generic entries to typed entries.
     *
     * @param entries The list of entries to convert.
     * @return The list of converted entries.
     */
    private List<T> convertEntries(Collection<ScheduleEntry> entries) {
        List<T> schedules = new ArrayList<>();
        for (ScheduleEntry entry : entries) {
            try {
                schedules.add(driver.createSchedule(entry.scheduleId, entry));
            } catch (ParseScheduleException e) {
                Logger.error("Unable to create schedule.", e);
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
    private boolean isScheduleConditionsSatisfied(ScheduleEntry scheduleEntry) {
        if (scheduleEntry.getPendingExecutionDate() > System.currentTimeMillis()) {
            return false;
        }

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

    private class ScheduleExecutorCallback implements AutomationDriver.Callback {

        private final String scheduleId;

        ScheduleExecutorCallback(String scheduleId) {
            this.scheduleId = scheduleId;
        }

        @Override
        public void onFinish() {
            onScheduleFinishedExecuting(scheduleId);
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
        public Analytics analytics;
        private OperationScheduler scheduler;

        /**
         * Sets the schedule limit.
         *
         * @param limit The schedule limit.
         * @return The builder instance.
         */
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
        public Builder<T> setAnalytics(Analytics analytics) {
            this.analytics = analytics;
            return this;
        }

        /**
         * Sets the {@link AutomationDriver<T>}.
         *
         * @param driver The engine's driver.
         * @return The builder instance.
         */
        public Builder<T> setDriver(AutomationDriver<T> driver) {
            this.driver = driver;
            return this;
        }

        /**
         * Sets the {@link AutomationDataManager}.
         *
         * @param dataManager The data manager.
         * @return The builder instance.
         */
        public Builder<T> setDataManager(AutomationDataManager dataManager) {
            this.dataManager = dataManager;
            return this;
        }

        /**
         * Sets the operation scheduler.
         *
         * @param scheduler The operation scheduler.
         * @return The builder instance.
         */
        public Builder<T> setOperationScheduler(OperationScheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Builds the engine.
         *
         * @return An automation engine.
         */
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
