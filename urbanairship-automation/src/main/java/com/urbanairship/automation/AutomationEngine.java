/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.SparseArray;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.Predicate;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.AnalyticsListener;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.analytics.location.RegionEvent;
import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.SimpleActivityListener;
import com.urbanairship.automation.alarms.AlarmOperationScheduler;
import com.urbanairship.automation.alarms.OperationScheduler;
import com.urbanairship.automation.storage.AutomationDao;
import com.urbanairship.automation.storage.AutomationDaoWrapper;
import com.urbanairship.automation.storage.AutomationDatabase;
import com.urbanairship.automation.storage.FullSchedule;
import com.urbanairship.automation.storage.LegacyDataMigrator;
import com.urbanairship.automation.storage.ScheduleEntity;
import com.urbanairship.automation.storage.ScheduleState;
import com.urbanairship.automation.storage.TriggerEntity;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.iam.InAppActivityMonitor;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Function;
import com.urbanairship.reactive.Observable;
import com.urbanairship.reactive.Scheduler;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subject;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.util.AirshipHandlerThread;
import com.urbanairship.util.Network;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core automation engine.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AutomationEngine {

    private long SCHEDULE_LIMIT = 1000;
    private final List<Integer> COMPOUND_TRIGGER_TYPES = Arrays.asList(Trigger.ACTIVE_SESSION, Trigger.VERSION);

    /**
     * Used to sort schedule priority.
     */
    private final Comparator<FullSchedule> SCHEDULE_PRIORITY_COMPARATOR = new Comparator<FullSchedule>() {
        @Override
        public int compare(@NonNull FullSchedule lh, @NonNull FullSchedule rh) {
            if (lh.schedule.priority == rh.schedule.priority) {
                return 0;
            }
            return lh.schedule.priority > rh.schedule.priority ? 1 : -1;
        }
    };

    private final ActivityMonitor activityMonitor;
    private AutomationDriver driver;
    private final Analytics analytics;
    private final OperationScheduler scheduler;
    private volatile boolean isStarted;
    private Handler backgroundHandler;
    private final Handler mainHandler;
    private ScheduleListener scheduleListener;

    private final LegacyDataMigrator legacyDataMigrator;
    private long startTime;
    private final SparseArray<Long> stateChangeTimeStamps = new SparseArray<>();
    private NetworkMonitor networkMonitor;

    @VisibleForTesting
    HandlerThread backgroundThread;
    private final List<ScheduleOperation> pendingAlarmOperations = new ArrayList<>();

    private String screen;
    private String regionId;

    private Subject<TriggerUpdate> stateObservableUpdates;
    private Subscription compoundTriggerSubscription;
    private Scheduler backgroundScheduler;
    private final AutomationDao dao;

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

    private final ActivityListener activityListener = new SimpleActivityListener() {
        @Override
        public void onActivityResumed(@NonNull Activity activity) {
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

    private final NetworkMonitor.ConnectionListener connectionListener = isConnected -> {
        if (isConnected) {
            checkPendingSchedules();
        }
    };

    private final PausedManager pausedManager;

    class PausedManager {
        private final AtomicBoolean isPaused = new AtomicBoolean(false);
        private final List<Consumer<Boolean>> consumers = new CopyOnWriteArrayList<>();

        public boolean isPaused() {
            return isPaused.get();
        }

        public void setPaused(boolean isPaused) {
            if (this.isPaused.compareAndSet(!isPaused, isPaused)) {
                for (Consumer<Boolean> consumer : consumers) {
                    consumer.accept(isPaused);
                }
            }
        }

        public void addConsumer(Consumer<Boolean> consumer) {
            consumers.add(consumer);
        }

        public void removeConsumer(Consumer<Boolean> consumer) {
            consumers.remove(consumer);
        }
    }

    /**
     * Schedule listener.
     */
    public interface ScheduleListener {

        /**
         * Called when a schedule is expired.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onScheduleExpired(@NonNull Schedule<? extends ScheduleData> schedule);

        /**
         * Called when a schedule is cancelled.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onScheduleCancelled(@NonNull Schedule<? extends ScheduleData> schedule);

        /**
         * Called when a schedule's limit is reached.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onScheduleLimitReached(@NonNull Schedule<? extends ScheduleData> schedule);

        /**
         * Called when a new schedule is available.
         *
         * @param schedule The schedule.
         */
        @MainThread
        void onNewSchedule(@NonNull Schedule<? extends ScheduleData> schedule);

    }

    AutomationEngine(@NonNull Context context,
                     @NonNull AirshipRuntimeConfig runtimeConfig,
                     @NonNull Analytics analytics,
                     @NonNull PreferenceDataStore dataStore) {
        this(analytics,
                InAppActivityMonitor.shared(context),
                AlarmOperationScheduler.shared(context),
                new AutomationDaoWrapper(AutomationDatabase.createDatabase(context, runtimeConfig).getScheduleDao()),
                new LegacyDataMigrator(context, runtimeConfig, dataStore));
    }

    @VisibleForTesting
    AutomationEngine(@NonNull Analytics analytics,
                     @NonNull ActivityMonitor activityMonitor,
                     @NonNull OperationScheduler scheduler,
                     @NonNull AutomationDao dao,
                     @NonNull LegacyDataMigrator legacyDataMigrator) {
        this.analytics = analytics;
        this.activityMonitor = activityMonitor;
        this.scheduler = scheduler;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.dao = dao;
        this.legacyDataMigrator = legacyDataMigrator;
        this.pausedManager = new PausedManager();
    }

    /**
     * Performs setup and starts listening for events.
     */
    public void start(@NonNull AutomationDriver driver) {
        if (isStarted) {
            return;
        }

        this.driver = driver;
        this.startTime = System.currentTimeMillis();
        this.backgroundThread = new AirshipHandlerThread("automation");
        this.backgroundThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());
        this.backgroundScheduler = Schedulers.looper(backgroundThread.getLooper());

        this.networkMonitor = new NetworkMonitor();
        networkMonitor.setConnectionListener(connectionListener);

        activityMonitor.addApplicationListener(applicationListener);
        activityMonitor.addActivityListener(activityListener);
        analytics.addAnalyticsListener(analyticsListener);

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                legacyDataMigrator.migrateData(dao);
                finishExecutingSchedules();
                cleanSchedules();
                resetWaitingSchedules();
                restoreDelayAlarms();
                restoreIntervalAlarms();
                prepareSchedules(dao.getSchedulesWithStates(ScheduleState.PREPARING_SCHEDULE));
            }
        });

        restoreCompoundTriggers();
        onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_APP_INIT, 1.00);

        isStarted = true;
        onScheduleConditionsChanged();
    }


    /**
     * Pauses processing any triggers or executing schedules. Any events will be dropped and
     * not counted towards a trigger's goal.
     *
     * @param isPaused {@code true} to pause the engine, otherwise {@code false}.
     */
    public void setPaused(boolean isPaused) {
        pausedManager.setPaused(isPaused);

        if (!isPaused && isStarted) {
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
        networkMonitor.teardown();
        cancelAlarms();
        backgroundThread.quit();
        backgroundThread = null;
        isStarted = false;
    }

    /**
     * Schedules a single action schedule.
     *
     * @param schedule The schedule.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Boolean> schedule(@NonNull final Schedule<? extends ScheduleData> schedule) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();

                if (dao.getScheduleCount() >= SCHEDULE_LIMIT) {
                    Logger.error("AutomationEngine - Unable to insert schedule due to schedule exceeded limit.");
                    pendingResult.setResult(false);
                    return;
                }

                FullSchedule entry = ScheduleConverters.convert(schedule);
                dao.insert(entry);
                subscribeStateObservables(Collections.singletonList(entry));

                notifyNewSchedule(Collections.<Schedule<? extends ScheduleData>>singletonList(schedule));

                Logger.verbose("Scheduled entries: %s", schedule);
                pendingResult.setResult(true);
            }
        });

        return pendingResult;
    }

    /**
     * Schedules a list of action schedules.
     *
     * @param schedules A list of {@link Schedule}.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<Boolean> schedule(@NonNull final List<Schedule<? extends ScheduleData>> schedules) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();

                if (dao.getScheduleCount() + schedules.size() > SCHEDULE_LIMIT) {
                    Logger.error("AutomationDataManager - Unable to insert schedule due to schedule exceeded limit.");
                    pendingResult.setResult(false);
                    return;
                }

                List<FullSchedule> entries = ScheduleConverters.convertSchedules(schedules);
                if (entries.isEmpty()) {
                    pendingResult.setResult(false);
                    return;
                }

                dao.insert(entries);
                subscribeStateObservables(entries);

                Collection<Schedule<? extends ScheduleData>> result = convertSchedulesUnknownTypes(entries);
                notifyNewSchedule(result);

                Logger.verbose("Scheduled entries: %s", result);
                pendingResult.setResult(true);
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
    public PendingResult<Boolean> cancel(@NonNull final Collection<String> ids) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                List<FullSchedule> entries = dao.getSchedules(ids);
                if (entries.isEmpty()) {
                    pendingResult.setResult(false);
                    return;
                }

                Logger.verbose("Cancelled schedules: %s", ids);

                dao.deleteSchedules(entries);
                notifyCancelledSchedule(entries);
                cancelScheduleAlarms(ids);
                pendingResult.setResult(true);
            }
        });

        return pendingResult;
    }

    @NonNull
    public PendingResult<Boolean> cancelByType(@NonNull @Schedule.Type final String type) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                List<FullSchedule> entries = dao.getSchedulesByType(type);
                if (entries.isEmpty()) {
                    pendingResult.setResult(false);
                    return;
                }

                List<String> ids = new ArrayList<>();
                for (FullSchedule entry : entries) {
                    ids.add(entry.schedule.scheduleId);
                }

                Logger.verbose("Cancelled schedules: %s", ids);
                dao.deleteSchedules(entries);
                notifyCancelledSchedule(entries);
                cancelScheduleAlarms(ids);
                pendingResult.setResult(true);
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
                List<FullSchedule> entries = dao.getSchedulesWithGroup(group);

                if (entries.isEmpty()) {
                    Logger.verbose("Failed to cancel schedule group: %s", group);
                    pendingResult.setResult(false);
                } else {
                    dao.deleteSchedules(entries);
                    cancelGroupAlarms(Collections.singletonList(group));
                    notifyCancelledSchedule(entries);
                }
            }
        });

        return pendingResult;
    }

    /**
     * Gets all schedules by type.
     *
     * @param type The schedule type.
     * @return A pending result.
     */
    @NonNull
    public <T extends ScheduleData> PendingResult<Collection<Schedule<T>>> getSchedulesByType(@Schedule.Type final String type) {
        final PendingResult<Collection<Schedule<T>>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                List<FullSchedule> entries = dao.getSchedulesByType(type);
                Collection<Schedule<T>> schedules = convertSchedules(entries);
                pendingResult.setResult(schedules);
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
    public PendingResult<Schedule<? extends ScheduleData>> getSchedule(@NonNull final String scheduleId) {
        final PendingResult<Schedule<? extends ScheduleData>> pendingResult = new PendingResult<>();
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                Schedule<? extends ScheduleData> result = convert(dao.getSchedule(scheduleId));
                pendingResult.setResult(result);
            }
        });

        return pendingResult;
    }

    /**
     * Gets a schedule for the given schedule ID.
     *
     * @param scheduleId The schedule ID.
     * @param type The type.
     * @return A pending result.
     */
    @NonNull
    public <T extends ScheduleData> PendingResult<Schedule<T>> getSchedule(@NonNull final String scheduleId, @Schedule.Type final String type) {
        final PendingResult<Schedule<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                Schedule<T> result = convert(dao.getSchedule(scheduleId, type));
                pendingResult.setResult(result);
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
    public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules(@NonNull final Set<String> scheduleIds) {
        final PendingResult<Collection<Schedule<? extends ScheduleData>>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                pendingResult.setResult(convertSchedulesUnknownTypes(dao.getSchedules(scheduleIds)));
            }
        });

        return pendingResult;
    }

    /**
     * Gets all schedules for the specified group.
     *
     * @param group The schedule group.
     * @param type The type.
     * @return A pending result.
     */
    @NonNull
    public <T extends ScheduleData> PendingResult<Collection<Schedule<T>>> getSchedules(@NonNull final String group, @NonNull @Schedule.Type final String type) {
        final PendingResult<Collection<Schedule<T>>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                cleanSchedules();
                Collection<Schedule<T>> schedules = convertSchedules(dao.getSchedulesWithGroup(group, type));
                pendingResult.setResult(schedules);
            }
        });

        return pendingResult;
    }

    /**
     * Edits a schedule.
     *
     * @param scheduleId The schedule ID.
     * @param edits The schedule edits.
     * @return Pending result with the result.
     */
    @NonNull
    public PendingResult<Boolean> editSchedule(@NonNull final String scheduleId, @NonNull final ScheduleEdits<? extends ScheduleData> edits) {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                FullSchedule entry = dao.getSchedule(scheduleId);

                if (entry == null) {
                    Logger.error("AutomationEngine - Schedule no longer exists. Unable to edit: %s", scheduleId);
                    pendingResult.setResult(false);
                    return;
                }

                applyEdits(entry, edits);

                boolean subscribeForStateChanges = false;
                long stateChangeTimeStamp = -1;

                boolean isOverLimit = isOverLimit(entry);
                boolean isExpired = isExpired(entry);

                // Check if the schedule needs to be rehabilitated or finished due to the edits
                if (entry.schedule.executionState == ScheduleState.FINISHED && !isOverLimit && !isExpired) {
                    subscribeForStateChanges = true;
                    stateChangeTimeStamp = entry.schedule.executionStateChangeDate;
                    updateExecutionState(entry, ScheduleState.IDLE);
                } else if (entry.schedule.executionState != ScheduleState.FINISHED && (isOverLimit || isExpired)) {
                    updateExecutionState(entry, ScheduleState.FINISHED);

                    if (isOverLimit) {
                        notifyScheduleLimitReached(entry);
                    } else {
                        notifyExpiredSchedules(Collections.singleton(entry));
                    }
                }

                dao.update(entry);

                if (subscribeForStateChanges) {
                    subscribeStateObservables(entry, stateChangeTimeStamp);
                }

                Logger.verbose("Updated schedule: %s", scheduleId);
                pendingResult.setResult(true);
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
    public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules() {
        final PendingResult<Collection<Schedule<? extends ScheduleData>>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                pendingResult.setResult(convertSchedulesUnknownTypes(dao.getSchedules()));
            }
        });

        return pendingResult;
    }

    /**
     * Sets the schedule listener.
     *
     * @param scheduleListener The listener.
     */
    public void setScheduleListener(@Nullable ScheduleListener scheduleListener) {
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
                return TriggerObservables.newSession(activityMonitor, pausedManager);
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
                                                                                      return new TriggerUpdate(dao.getActiveTriggers(type), json, 1.0);
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
                                                             updateTriggers(update.triggerEntities, update.json, update.value);
                                                         }
                                                     });

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                subscribeStateObservables(dao.getSchedules());
            }
        });

    }

    /**
     * Sorts a list of schedule entries by priority.
     *
     * @param entries The schedule entries.
     */
    @WorkerThread
    private void sortSchedulesByPriority(@NonNull List<FullSchedule> entries) {
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
    private void subscribeStateObservables(@NonNull List<FullSchedule> entries) {
        sortSchedulesByPriority(entries);

        for (final FullSchedule entity : entries) {
            subscribeStateObservables(entity, -1);
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
    private void subscribeStateObservables(@NonNull final FullSchedule entry, final long lastStateChangeTime) {
        Observable.from(COMPOUND_TRIGGER_TYPES)
                  .filter(new Predicate<Integer>() {
                      @Override
                      public boolean apply(Integer triggerType) {
                          if (stateChangeTimeStamps.get(triggerType, startTime) <= lastStateChangeTime) {
                              return false;
                          }

                          for (TriggerEntity triggerEntity : entry.triggers) {
                              if (triggerEntity.triggerType == triggerType) {
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
                                          return new TriggerUpdate(dao.getActiveTriggers(type, entry.schedule.scheduleId), json, 1.0);
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
     * Notifies the driver on any schedule that was executing.
     */
    @WorkerThread
    private void finishExecutingSchedules() {
        List<FullSchedule> entries = dao.getSchedulesWithStates(ScheduleState.EXECUTING);
        for (FullSchedule entry : entries) {
            driver.onScheduleExecutionInterrupted(convert(entry));
            onScheduleFinishedExecuting(entry);
        }
    }

    /**
     * Resets the schedules that were waiting for state conditions back to pending.
     */
    @WorkerThread
    private void resetWaitingSchedules() {
        List<FullSchedule> entries = dao.getSchedulesWithStates(ScheduleState.WAITING_SCHEDULE_CONDITIONS);

        if (entries.isEmpty()) {
            return;
        }

        for (FullSchedule entry : entries) {
            updateExecutionState(entry, ScheduleState.PREPARING_SCHEDULE);
        }

        dao.updateSchedules(entries);
        Logger.verbose("AutomationEngine: Schedules reset state to STATE_PREPARING_SCHEDULE: %s", entries);
    }

    /**
     * Expires active schedules past their end date and deletes finished schedules past the edit
     * grace period.
     */
    @WorkerThread
    private void cleanSchedules() {
        List<FullSchedule> expired = dao.getActiveExpiredSchedules();
        List<FullSchedule> finished = dao.getSchedulesWithStates(ScheduleState.FINISHED);

        handleExpiredEntries(expired);

        Set<FullSchedule> schedulesToDelete = new HashSet<>();
        for (FullSchedule entry : finished) {
            long finishDate;

            // If grace period is unset - use the executionStateChangeDate as finishDate to avoid unnecessarily keeping schedules around
            if (entry.schedule.editGracePeriod == 0) {
                finishDate = entry.schedule.executionStateChangeDate;
            } else if (entry.schedule.scheduleEnd >= 0) {
                finishDate = entry.schedule.scheduleEnd + entry.schedule.editGracePeriod;
            } else {
                // no end date, keep it around for edits
                continue;
            }

            if (System.currentTimeMillis() >= finishDate) {
                schedulesToDelete.add(entry);
            }
        }

        if (!schedulesToDelete.isEmpty()) {
            Logger.verbose("Deleting finished schedules: %s", schedulesToDelete);
            dao.deleteSchedules(schedulesToDelete);
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
        List<FullSchedule> entries = dao.getSchedulesWithStates(ScheduleState.TIME_DELAYED);
        if (entries.isEmpty()) {
            return;
        }

        List<FullSchedule> schedulesToUpdate = new ArrayList<>();

        for (FullSchedule entry : entries) {
            // No delay, mark it to be executed
            if (entry.schedule.seconds == 0) {
                continue;
            }

            long delay = TimeUnit.SECONDS.toMillis(entry.schedule.seconds);
            long remainingDelay = Math.min(delay, System.currentTimeMillis() - entry.schedule.executionStateChangeDate);

            if (remainingDelay <= 0) {
                updateExecutionState(entry, ScheduleState.PREPARING_SCHEDULE);
                schedulesToUpdate.add(entry);
                continue;
            }

            scheduleDelayAlarm(entry, remainingDelay);
        }

        dao.updateSchedules(schedulesToUpdate);
    }

    /**
     * Reschedule interval operations.
     */
    @WorkerThread
    private void restoreIntervalAlarms() {
        List<FullSchedule> entries = dao.getSchedulesWithStates(ScheduleState.PAUSED);
        if (entries.isEmpty()) {
            return;
        }

        List<FullSchedule> schedulesToUpdate = new ArrayList<>();

        for (FullSchedule entry : entries) {
            long pausedTime = System.currentTimeMillis() - entry.schedule.executionStateChangeDate;
            long remaining = entry.schedule.interval - pausedTime;

            if (remaining > 0) {
                scheduleIntervalAlarm(entry, remaining);
            } else {
                updateExecutionState(entry, ScheduleState.IDLE);
                schedulesToUpdate.add(entry);
            }
        }

        dao.updateSchedules(schedulesToUpdate);
    }

    /**
     * Called when one of the schedule conditions changes.
     */
    private void onScheduleConditionsChanged() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                List<FullSchedule> entries = dao.getSchedulesWithStates(ScheduleState.WAITING_SCHEDULE_CONDITIONS);
                if (entries.isEmpty()) {
                    return;
                }

                sortSchedulesByPriority(entries);
                for (FullSchedule entry : entries) {
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
                Logger.debug("Updating triggers with type: %s", type);
                List<TriggerEntity> triggerEntities = dao.getActiveTriggers(type);
                if (triggerEntities.isEmpty()) {
                    return;
                }
                updateTriggers(triggerEntities, json, value);
            }
        });
    }

    /**
     * Iterates through a list of triggers that need to respond to an event or state. If a trigger goal
     * is achieved, the correlated schedule is retrieved and the action is applied. The trigger progress
     * and schedule count will then either be incremented or reset / removed.
     *
     * @param triggerEntities The triggers
     * @param json The relevant event or state data.
     * @param value The trigger value to increment by.
     */
    private void updateTriggers(@NonNull final List<TriggerEntity> triggerEntities, @NonNull final JsonSerializable json, final double value) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (pausedManager.isPaused() || triggerEntities.isEmpty()) {
                    return;
                }

                Set<String> triggeredSchedules = new HashSet<>();
                Set<String> cancelledSchedules = new HashSet<>();
                Map<String, TriggerContext> triggerContextMap = new HashMap<>();

                List<TriggerEntity> triggersToUpdate = new ArrayList<>();

                for (TriggerEntity trigger : triggerEntities) {
                    if (trigger.jsonPredicate != null && !trigger.jsonPredicate.apply(json)) {
                        continue;
                    }

                    triggersToUpdate.add(trigger);
                    trigger.progress += value;

                    if (trigger.progress >= trigger.goal) {
                        trigger.progress = 0;

                        if (trigger.isCancellation) {
                            cancelledSchedules.add(trigger.parentScheduleId);
                            cancelScheduleAlarms(Collections.singletonList(trigger.parentScheduleId));
                        } else {
                            triggeredSchedules.add(trigger.parentScheduleId);
                            triggerContextMap.put(trigger.parentScheduleId, new TriggerContext(ScheduleConverters.convert(trigger), json.toJsonValue()));
                        }
                    }
                }

                dao.updateTriggers(triggersToUpdate);

                if (!cancelledSchedules.isEmpty()) {
                    handleCancelledSchedules(dao.getSchedules(cancelledSchedules));
                }

                if (!triggeredSchedules.isEmpty()) {
                    handleTriggeredSchedules(dao.getSchedules(triggeredSchedules), triggerContextMap);
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
    private void handleCancelledSchedules(@NonNull final List<FullSchedule> scheduleEntries) {
        if (scheduleEntries.isEmpty()) {
            return;
        }

        for (FullSchedule entry : scheduleEntries) {
            updateExecutionState(entry, ScheduleState.IDLE);
        }

        dao.updateSchedules(scheduleEntries);
    }

    /**
     * Processes a list of triggered schedule entries.
     *
     * @param scheduleEntries A list of triggered schedule entries.
     * @param triggerContextMap The map of schedule Id to trigger context.
     */
    @WorkerThread
    private void handleTriggeredSchedules(@NonNull final List<FullSchedule> scheduleEntries, Map<String, TriggerContext> triggerContextMap) {
        if (pausedManager.isPaused() || scheduleEntries.isEmpty()) {
            return;
        }

        final List<FullSchedule> schedulesToUpdate = new ArrayList<>();
        final List<FullSchedule> expiredSchedules = new ArrayList<>();
        final List<FullSchedule> schedulesToPrepare = new ArrayList<>();

        for (final FullSchedule entry : scheduleEntries) {
            if (entry.schedule.executionState != ScheduleState.IDLE) {
                continue;
            }

            schedulesToUpdate.add(entry);

            entry.schedule.triggerContext = triggerContextMap.get(entry.schedule.scheduleId);

            // Expired schedules
            if (isExpired(entry)) {
                expiredSchedules.add(entry);
                continue;
            }

            // Reset cancellation triggers
            for (TriggerEntity trigger : entry.triggers) {
                if (trigger.isCancellation) {
                    trigger.progress = 0;
                }
            }

            // Check for delays
            if (entry.schedule.seconds > 0) {
                updateExecutionState(entry, ScheduleState.TIME_DELAYED);
                scheduleDelayAlarm(entry, TimeUnit.SECONDS.toMillis(entry.schedule.seconds));
                continue;
            }

            // IDLE -> PREPARE
            updateExecutionState(entry, ScheduleState.PREPARING_SCHEDULE);
            schedulesToPrepare.add(entry);
        }

        dao.updateSchedules(schedulesToUpdate);
        prepareSchedules(schedulesToPrepare);
        handleExpiredEntries(expiredSchedules);
    }

    /**
     * Called to prepare the schedules after they have been triggered.
     *
     * @param entries The entries to prepare.
     */
    @WorkerThread
    private void prepareSchedules(@Nullable final List<FullSchedule> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        sortSchedulesByPriority(entries);
        for (FullSchedule entry : entries) {
            Schedule<? extends ScheduleData> schedule = convert(entry);
            if (schedule == null) {
                continue;
            }

            final String scheduleId = schedule.getId();
            driver.onPrepareSchedule(schedule, entry.schedule.triggerContext, new AutomationDriver.PrepareScheduleCallback() {
                @Override
                public void onFinish(@AutomationDriver.PrepareResult final int result) {
                    backgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {

                            // Grab the updated entry
                            FullSchedule entry = dao.getSchedule(scheduleId);

                            // Make sure we are still suppose to be preparing the schedule
                            if (entry == null || entry.schedule.executionState != ScheduleState.PREPARING_SCHEDULE) {
                                return;
                            }

                            // Verify the schedule is not expired
                            if (isExpired(entry)) {
                                handleExpiredEntry(entry);
                                return;
                            }

                            switch (result) {
                                case AutomationDriver.PREPARE_RESULT_CANCEL:
                                    dao.delete(entry);
                                    notifyCancelledSchedule(Collections.singleton(entry));
                                    break;

                                case AutomationDriver.PREPARE_RESULT_CONTINUE:
                                    updateExecutionState(entry, ScheduleState.WAITING_SCHEDULE_CONDITIONS);
                                    dao.update(entry);
                                    attemptExecution(entry);
                                    break;

                                case AutomationDriver.PREPARE_RESULT_SKIP:
                                    updateExecutionState(entry, ScheduleState.IDLE);
                                    dao.update(entry);
                                    break;

                                case AutomationDriver.PREPARE_RESULT_PENALIZE:
                                    onScheduleFinishedExecuting(entry);
                                    break;

                                case AutomationDriver.PREPARE_RESULT_INVALIDATE:
                                    prepareSchedules(Collections.singletonList(entry));
                                    break;
                            }
                        }
                    });
                }
            });
        }
    }

    @Nullable
    private <T extends ScheduleData> Schedule<T> convert(@Nullable FullSchedule entry) {
        if (entry == null) {
            return null;
        }

        try {
            return ScheduleConverters.convert(entry);
        } catch (ClassCastException e) {
            Logger.error(e, "Exception converting entity to schedule %s", entry.schedule.scheduleId);
        } catch (Exception e) {
            Logger.error(e, "Exception converting entity to schedule %s. Cancelling.", entry.schedule.scheduleId);
            cancel(Collections.singleton(entry.schedule.scheduleId));
        }
        return null;
    }

    @NonNull
    private Collection<Schedule<? extends ScheduleData>> convertSchedulesUnknownTypes(@NonNull Collection<FullSchedule> entries) {
        Collection<Schedule<? extends ScheduleData>> schedules = new ArrayList<>();
        for (FullSchedule entry : entries) {
            Schedule<? extends ScheduleData> schedule = convert(entry);
            if (schedule != null) {
                schedules.add(schedule);
            }
        }
        return schedules;
    }

    @NonNull
    private <T extends ScheduleData> Collection<Schedule<T>> convertSchedules(@NonNull Collection<FullSchedule> entries) {
        List<Schedule<T>> schedules = new ArrayList<>();
        for (FullSchedule entry : entries) {
            Schedule<T> schedule = convert(entry);
            if (schedule != null) {
                schedules.add(schedule);
            }
        }
        return schedules;
    }

    /**
     * Called to attempt executing a schedule entry.
     *
     * @param entry The schedule entry.
     */
    @WorkerThread
    private void attemptExecution(@NonNull final FullSchedule entry) {
        if (entry.schedule.executionState != ScheduleState.WAITING_SCHEDULE_CONDITIONS) {
            Logger.error("Unable to execute schedule when state is %s scheduleID: %s", entry.schedule.executionState, entry.schedule.scheduleId);
            return;
        }

        // Verify the schedule is not expired
        if (isExpired(entry)) {
            handleExpiredEntry(entry);
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        ScheduleRunnable<Integer> runnable = new ScheduleRunnable<Integer>(entry.schedule.scheduleId, entry.schedule.group) {
            @Override
            public void run() {
                Schedule<? extends ScheduleData> schedule = null;
                result = AutomationDriver.READY_RESULT_NOT_READY;

                if (pausedManager.isPaused()) {
                    return;
                }

                if (isScheduleConditionsSatisfied(entry)) {
                    try {
                        schedule = ScheduleConverters.convert(entry);
                        result = driver.onCheckExecutionReadiness(schedule);
                    } catch (Exception e) {
                        Logger.error(e, "Unable to create schedule.");
                        this.exception = e;
                    }
                }
                latch.countDown();

                if (AutomationDriver.READY_RESULT_CONTINUE == result && schedule != null) {
                    driver.onExecuteTriggeredSchedule(schedule, new ScheduleExecutorCallback(entry.schedule.scheduleId));
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
            Logger.error("Failed to check conditions. Deleting schedule: %s", entry.schedule.scheduleId);
            dao.delete(entry);
            notifyCancelledSchedule(Collections.singleton(entry));
        } else {
            int result = runnable.result == null ? AutomationDriver.READY_RESULT_NOT_READY : runnable.result;
            switch (result) {
                case AutomationDriver.READY_RESULT_INVALIDATE:
                    Logger.verbose("Schedule invalidated: %s", entry.schedule.scheduleId);
                    updateExecutionState(entry, ScheduleState.PREPARING_SCHEDULE);
                    dao.update(entry);
                    prepareSchedules(Collections.singletonList(dao.getSchedule(entry.schedule.scheduleId)));
                    break;

                case AutomationDriver.READY_RESULT_CONTINUE:
                    Logger.verbose("Schedule executing: %s", entry.schedule.scheduleId);
                    updateExecutionState(entry, ScheduleState.EXECUTING);
                    dao.update(entry);
                    break;

                case AutomationDriver.READY_RESULT_NOT_READY:
                    Logger.verbose("Schedule not ready for execution: %s", entry.schedule.scheduleId);
                    break;

                case AutomationDriver.READY_RESULT_SKIP:
                    Logger.verbose("Schedule execution skipped: %s", entry.schedule.scheduleId);
                    updateExecutionState(entry, ScheduleState.IDLE);
                    dao.update(entry);
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
    private void notifyExpiredSchedules(@NonNull Collection<FullSchedule> entries) {
        notifyHelper(convertSchedulesUnknownTypes(entries), new NotifySchedule() {
            @Override
            public void notify(@NonNull ScheduleListener listener, @NonNull Schedule<? extends ScheduleData> schedule) {
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
    private void notifyCancelledSchedule(@NonNull Collection<FullSchedule> entries) {
        notifyHelper(convertSchedulesUnknownTypes(entries), new NotifySchedule() {
            @Override
            public void notify(@NonNull ScheduleListener listener, @NonNull Schedule<? extends ScheduleData> schedule) {
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
    private void notifyScheduleLimitReached(@NonNull FullSchedule entry) {
        notifyHelper(convertSchedulesUnknownTypes(Collections.singleton(entry)), new NotifySchedule() {
            @Override
            public void notify(@NonNull ScheduleListener listener, @NonNull Schedule<? extends ScheduleData> schedule) {
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
    private void notifyNewSchedule(@NonNull final Collection<Schedule<? extends ScheduleData>> schedules) {
        notifyHelper(schedules, new NotifySchedule() {
            @Override
            public void notify(@NonNull ScheduleListener listener, @NonNull Schedule schedule) {
                listener.onNewSchedule(schedule);
            }
        });
    }

    /**
     * Called to notify the schedule listener.
     */
    private interface NotifySchedule {

        void notify(@NonNull ScheduleListener listener, @NonNull Schedule<? extends ScheduleData> schedule);

    }

    /**
     * Notify helper. Calls the listener on the main thread and handles any null checks.
     *
     * @param entries The entries.
     * @param notify The notify method.
     */
    @WorkerThread
    private void notifyHelper(@NonNull final Collection<Schedule<? extends ScheduleData>> entries, @NonNull final NotifySchedule notify) {
        if (this.scheduleListener == null || entries.isEmpty()) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Schedule<? extends ScheduleData> schedule : entries) {
                    ScheduleListener listener = AutomationEngine.this.scheduleListener;
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
     * @param entry The schedule entry.
     */
    @WorkerThread
    private void onScheduleFinishedExecuting(@Nullable final FullSchedule entry) {
        if (entry == null) {
            return;
        }

        Logger.verbose("Schedule finished: %s", entry.schedule.scheduleId);

        entry.schedule.count++;
        boolean isOverLimit = isOverLimit(entry);

        // Expired
        if (isExpired(entry)) {
            handleExpiredEntry(entry);
            return;
        }

        if (isOverLimit) {
            // At limit
            updateExecutionState(entry, ScheduleState.FINISHED);
            notifyScheduleLimitReached(entry);

            // Delete the schedule if its finished and no edit grace period is defined
            if (entry.schedule.editGracePeriod <= 0) {
                dao.delete(entry);
                return;
            }

        } else if (entry.schedule.interval > 0) {
            // Execution interval
            updateExecutionState(entry, ScheduleState.PAUSED);
            scheduleIntervalAlarm(entry, entry.schedule.interval);
        } else {
            // Back to idle
            updateExecutionState(entry, ScheduleState.IDLE);
        }

        dao.update(entry);
    }

    /**
     * Schedules a delay for a schedule entry.
     *
     * @param entry The schedule entry.
     * @param delay The delay in milliseconds.
     */
    private void scheduleDelayAlarm(@NonNull final FullSchedule entry, long delay) {
        final ScheduleOperation operation = new ScheduleOperation(entry.schedule.scheduleId, entry.schedule.group) {
            @Override
            protected void onRun() {
                FullSchedule entry = dao.getSchedule(scheduleId);
                if (entry != null && entry.schedule.executionState == ScheduleState.TIME_DELAYED) {

                    // Expired
                    if (isExpired(entry)) {
                        handleExpiredEntry(entry);
                        return;
                    }

                    // Delayed => Preparing
                    updateExecutionState(entry, ScheduleState.PREPARING_SCHEDULE);
                    dao.update(entry);

                    prepareSchedules(Collections.singletonList(entry));
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
     * @param entry The schedule entry.
     * @param interval The interval in milliseconds.
     */
    @WorkerThread
    private void scheduleIntervalAlarm(@NonNull FullSchedule entry, long interval) {
        final ScheduleOperation operation = new ScheduleOperation(entry.schedule.scheduleId, entry.schedule.group) {
            @Override
            protected void onRun() {
                FullSchedule entry = dao.getSchedule(scheduleId);
                if (entry == null || entry.schedule.executionState != ScheduleState.PAUSED) {
                    return;
                }

                // Expired
                if (isExpired(entry)) {
                    handleExpiredEntry(entry);
                    return;
                }

                long pauseStartTime = entry.schedule.executionStateChangeDate;

                // Paused => Idle
                updateExecutionState(entry, ScheduleState.IDLE);
                dao.update(entry);
                subscribeStateObservables(entry, pauseStartTime);
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
     * Checks if the schedule entry's conditions are met.
     *
     * @param entry The schedule entry.
     * @return {@code true} if the conditions are met, otherwise {@code false}.
     */
    @MainThread
    private boolean isScheduleConditionsSatisfied(@NonNull FullSchedule entry) {
        if (entry.schedule.screens != null && !entry.schedule.screens.isEmpty()) {
            if (!entry.schedule.screens.contains(screen)) {
                return false;
            }
        }

        if (entry.schedule.regionId != null && !entry.schedule.regionId.equals(regionId)) {
            return false;
        }

        switch (entry.schedule.appState) {
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
     * @param entity The expired schedule entry.
     */
    private void handleExpiredEntry(@NonNull FullSchedule entity) {
        handleExpiredEntries(Collections.singleton(entity));
    }

    /**
     * Sets the execution state, saves the schedule, and notifies any listeners of the
     * expired schedules.
     *
     * @param entries The expired schedule entries.
     */
    private void handleExpiredEntries(@NonNull Collection<FullSchedule> entries) {
        if (entries.isEmpty()) {
            return;
        }
        List<FullSchedule> schedulesToDelete = new ArrayList<>();
        List<FullSchedule> schedulesToUpdate = new ArrayList<>();

        for (FullSchedule entry : entries) {
            updateExecutionState(entry, ScheduleState.FINISHED);
            if (entry.schedule.editGracePeriod > 0) {
                schedulesToUpdate.add(entry);
            } else {
                schedulesToDelete.add(entry);
            }
        }

        dao.updateSchedules(schedulesToUpdate);
        dao.deleteSchedules(schedulesToDelete);
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

    private static abstract class ScheduleRunnable<T> implements Runnable {

        final String scheduleId;
        final String group;
        T result;
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
                    onScheduleFinishedExecuting(dao.getSchedule(scheduleId));
                }
            });
        }

    }

    /**
     * Edits the schedule entry.
     *
     * @param edits The schedule edits.
     */
    public void applyEdits(@NonNull FullSchedule entry, @NonNull ScheduleEdits edits) {
        ScheduleEntity scheduleEntity = entry.schedule;
        scheduleEntity.scheduleStart = edits.getStart() == null ? scheduleEntity.scheduleStart : edits.getStart();
        scheduleEntity.scheduleEnd = edits.getEnd() == null ? scheduleEntity.scheduleEnd : edits.getEnd();
        scheduleEntity.limit = edits.getLimit() == null ? scheduleEntity.limit : edits.getLimit();
        scheduleEntity.data = edits.getData() == null ? scheduleEntity.data : edits.getData().toJsonValue();
        scheduleEntity.priority = edits.getPriority() == null ? scheduleEntity.priority : edits.getPriority();
        scheduleEntity.interval = edits.getInterval() == null ? scheduleEntity.interval : edits.getInterval();
        scheduleEntity.editGracePeriod = edits.getEditGracePeriod() == null ? scheduleEntity.editGracePeriod : edits.getEditGracePeriod();
        scheduleEntity.metadata = edits.getMetadata() == null ? scheduleEntity.metadata : edits.getMetadata();
        scheduleEntity.scheduleType = edits.getType() == null ? scheduleEntity.scheduleType : edits.getType();
        scheduleEntity.audience = edits.getAudience() == null ? scheduleEntity.audience : edits.getAudience();
        scheduleEntity.campaigns = edits.getCampaigns() == null ? scheduleEntity.campaigns : edits.getCampaigns();
        scheduleEntity.reportingContext = edits.getReportingContext() == null ? scheduleEntity.reportingContext : edits.getReportingContext();
        scheduleEntity.frequencyConstraintIds = edits.getFrequencyConstraintIds() == null ? scheduleEntity.frequencyConstraintIds : edits.getFrequencyConstraintIds();
    }

    private boolean isExpired(@NonNull FullSchedule entry) {
        return entry.schedule.scheduleEnd >= 0 && entry.schedule.scheduleEnd < System.currentTimeMillis();
    }

    private boolean isOverLimit(@NonNull FullSchedule entry) {
        return entry.schedule.limit > 0 && entry.schedule.count >= entry.schedule.limit;
    }

    private void updateExecutionState(@NonNull FullSchedule schedule, int executionState) {
        if (schedule.schedule.executionState != executionState) {
            schedule.schedule.executionState = executionState;
            schedule.schedule.executionStateChangeDate = System.currentTimeMillis();
        }
    }

    /**
     * Model object representing trigger update data.
     */
    private static class TriggerUpdate {

        final List<TriggerEntity> triggerEntities;
        final JsonSerializable json;
        final double value;

        TriggerUpdate(@NonNull final List<TriggerEntity> triggerEntities, @NonNull final JsonSerializable json, final double value) {
            this.triggerEntities = triggerEntities;
            this.json = json;
            this.value = value;
        }

    }

}
