/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.Logger;
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
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationEngine<T extends Schedule> {

    private final List<Integer> COMPOUND_TRIGGER_TYPES = Arrays.asList(Trigger.ACTIVE_SESSION);

    private final AutomationDataManager dataManager;
    private final ActivityMonitor activityMonitor;
    private final AutomationDriver<T> driver;
    private final Analytics analytics;
    private final long scheduleLimit;
    private boolean isStarted;
    private Handler backgroundHandler;
    private Handler mainHandler;
    private ScheduleExpiryListener<T> expiryListener;
    private AtomicBoolean isPaused = new AtomicBoolean(false);

    @VisibleForTesting
    HandlerThread backgroundThread;
    private List<ScheduleRunnable> delayedRunnables = new ArrayList<>();

    private String screen;
    private String regionId;

    private Subject<TriggerUpdate> stateObservableUpdates;
    private Subscription compoundTriggerSubscription;
    ;

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
    private AutomationEngine(Builder builder) {
        this.dataManager = builder.dataManager;
        this.activityMonitor = builder.activityMonitor;
        this.analytics = builder.analytics;
        this.driver = builder.driver;
        this.scheduleLimit = builder.limit;

        this.backgroundThread = new HandlerThread("delayed");
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Performs setup and starts listening for events.
     */
    public void start() {
        if (isStarted) {
            return;
        }

        this.backgroundThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());

        activityMonitor.addListener(activityListener);
        analytics.addAnalyticsListener(analyticsListener);

        resetExecutingSchedules();
        rescheduleDelays();
        onEventAdded(JsonValue.NULL, Trigger.LIFE_CYCLE_APP_INIT, 1.00);
        onScheduleConditionsChanged();

        restoreCompoundTriggers();

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
        cancelAllDelays();
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
                if (dataManager.getScheduleCount() >= scheduleLimit) {
                    Logger.error("AutomationDataManager - unable to insert schedule due to schedule exceeded limit.");
                    pendingResult.setResult(null);
                    return;
                }

                String scheduleId = UUID.randomUUID().toString();
                ScheduleEntry entry = new ScheduleEntry(scheduleId, scheduleInfo);

                List<ScheduleEntry> entries = Collections.singletonList(entry);
                dataManager.saveSchedules(entries);
                subscribeStateObservables(entries);
                pendingResult.setResult(convertEntries(entries).get(0));
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
                if (dataManager.getScheduleCount() + scheduleInfos.size() > scheduleLimit) {
                    Logger.error("AutomationDataManager - unable to insert schedule due to schedule exceeded limit.");
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
                pendingResult.setResult(convertEntries(entries));
            }
        });

        return pendingResult;
    }

    /**
     * Cancels schedules.
     *
     * @param ids List of schedule Ids to cancel.
     * @return A pending result.
     */
    public PendingResult<Void> cancel(@NonNull final List<String> ids) {
        final PendingResult<Void> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                dataManager.deleteSchedules(ids);
                cancelScheduleDelays(ids);
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
                cancelGroupDelays(group);
                pendingResult.setResult(dataManager.deleteGroup(group));
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
                cancelAllDelays();
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
     * @param scheduleIds The list of schedule IDs.
     * @return A pending result.
     */
    public PendingResult<List<T>> getSchedules(@NonNull final Set<String> scheduleIds) {
        final PendingResult<List<T>> pendingResult = new PendingResult<>();

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
    public PendingResult<List<T>> getSchedules(final String group) {
        final PendingResult<List<T>> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                pendingResult.setResult(convertEntries(dataManager.getScheduleEntries(group)));
            }
        });

        return pendingResult;
    }

    /**
     * Triggers the engine to recheck all pending schedules.
     */
    public void checkPendingSchedules() {
        onScheduleConditionsChanged();
    }

    /**
     * Gets all schedules.
     *
     * @return A pending result.
     */
    public PendingResult<List<T>> getSchedules() {
        final PendingResult<List<T>> pendingResult = new PendingResult<>();

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
            default:
                return Observable.empty();
        }
    }

    /**
     * Restores compound triggers for all schedule entries.
     */
    private void restoreCompoundTriggers() {
        final List<Observable<TriggerUpdate>> eventObservables = new ArrayList<>();

        for (final @Trigger.TriggerType int type : COMPOUND_TRIGGER_TYPES) {
            eventObservables.add(createEventObservable(type).observeOn(Schedulers.looper(backgroundThread.getLooper()))
                                                            .map(new Function<JsonSerializable, TriggerUpdate>() {
                                                                @Override
                                                                public TriggerUpdate apply(JsonSerializable json) {
                                                                    return new TriggerUpdate(dataManager.getActiveTriggerEntries(type), json, 1.0);
                                                                }
                                                            }));
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
                List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries();
                subscribeStateObservables(scheduleEntries);
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
            Observable.from(COMPOUND_TRIGGER_TYPES)
                      .filter(new Predicate<Integer>() {
                          @Override
                          public boolean apply(Integer triggerType) {
                              for (TriggerEntry triggerEntry : scheduleEntry.triggerEntries) {
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
                              return createStateObservable(type).map(new Function<JsonSerializable, TriggerUpdate>() {
                                  @Override
                                  public TriggerUpdate apply(JsonSerializable json) {
                                      return new TriggerUpdate(dataManager.getActiveTriggerEntries(type, scheduleEntry.scheduleId), json, 1.0);
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
    }

    /**
     * Resets the schedules that were executing back to pending execution.
     */
    private void resetExecutingSchedules() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                List<ScheduleEntry> entries = dataManager.getScheduleEntries(ScheduleEntry.STATE_EXECUTING);
                if (entries.isEmpty()) {
                    return;
                }

                for (ScheduleEntry entry : entries) {
                    entry.setExecutionState(ScheduleEntry.STATE_PENDING_EXECUTION);
                }

                dataManager.saveSchedules(entries);
            }
        });
    }

    /**
     * Cancel delayed schedule runnables.
     *
     * @param scheduleIds A set of identifiers to cancel.
     */
    @WorkerThread
    private void cancelScheduleDelays(Collection<String> scheduleIds) {
        for (ScheduleRunnable runnable : new ArrayList<>(delayedRunnables)) {
            if (scheduleIds.contains(runnable.scheduleId)) {
                backgroundHandler.removeCallbacksAndMessages(runnable.scheduleId);
                delayedRunnables.remove(runnable);
            }
        }
    }

    /**
     * Cancel delay schedule handler by a group.
     *
     * @param group A schedule identifier.
     */
    private void cancelGroupDelays(String group) {
        for (ScheduleRunnable runnable : new ArrayList<>(delayedRunnables)) {
            if (group.equals(runnable.group)) {
                backgroundHandler.removeCallbacksAndMessages(runnable.scheduleId);
                delayedRunnables.remove(runnable);
            }
        }
    }

    /**
     * Cancels all delayed schedule runnables.
     */
    private void cancelAllDelays() {
        for (ScheduleRunnable runnable : delayedRunnables) {
            backgroundHandler.removeCallbacksAndMessages(runnable.scheduleId);
        }

        delayedRunnables.clear();
    }

    /**
     * Reschedule all delay schedule runnables.
     */
    private void rescheduleDelays() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {

                List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(ScheduleEntry.STATE_PENDING_EXECUTION);
                if (scheduleEntries.isEmpty()) {
                    return;
                }

                List<ScheduleEntry> schedulesToUpdate = new ArrayList<>();

                for (ScheduleEntry scheduleEntry : schedulesToUpdate) {
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

                    startDelayTimer(scheduleEntry, remainingDelay);
                }
            }
        });
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
                Logger.debug("Automation - updating triggers with type: " + type);
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
                        if (trigger.isCancellation) {
                            cancelledSchedules.add(trigger.scheduleId);
                            cancelScheduleDelays(Collections.singletonList(trigger.scheduleId));
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
            // Delete expired schedules.
            if (scheduleEntry.end > 0 && scheduleEntry.end < System.currentTimeMillis()) {
                expiredScheduleEntries.add(scheduleEntry);
                schedulesToDelete.add(scheduleEntry.scheduleId);
                continue;
            }

            // Ignore already triggered schedules
            if (scheduleEntry.getExecutionState() == ScheduleEntry.STATE_PENDING_EXECUTION && scheduleEntry.getPendingExecutionDate() > System.currentTimeMillis()) {
                continue;
            }

            // Handle schedules with delays
            if (scheduleEntry.getExecutionState() == ScheduleEntry.STATE_IDLE && scheduleEntry.seconds > 0) {
                for (TriggerEntry triggerEntry : scheduleEntry.triggerEntries) {
                    if (triggerEntry.isCancellation) {
                        triggerEntry.setProgress(0);
                    }
                }

                scheduleEntry.setExecutionState(ScheduleEntry.STATE_PENDING_EXECUTION);
                scheduleEntry.setPendingExecutionDate(TimeUnit.SECONDS.toMillis(scheduleEntry.seconds) + System.currentTimeMillis());
                startDelayTimer(scheduleEntry, TimeUnit.SECONDS.toMillis(scheduleEntry.seconds));
                schedulesToUpdate.add(scheduleEntry);
                continue;
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
            } else if (runnable.result) {
                scheduleEntry.setExecutionState(ScheduleEntry.STATE_EXECUTING);
                schedulesToUpdate.add(scheduleEntry);
            } else if (scheduleEntry.getExecutionState() == ScheduleEntry.STATE_IDLE) {
                for (TriggerEntry triggerEntry : scheduleEntry.triggerEntries) {
                    if (triggerEntry.isCancellation) {
                        triggerEntry.setProgress(0);
                    }
                }

                scheduleEntry.setExecutionState(ScheduleEntry.STATE_PENDING_EXECUTION);
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

                scheduleEntry.setExecutionState(ScheduleEntry.STATE_IDLE);
                scheduleEntry.setPendingExecutionDate(-1);
                scheduleEntry.setCount(scheduleEntry.getCount() + 1);

                if (scheduleEntry.getCount() >= scheduleEntry.limit) {
                    List<String> scheduleIdList = Collections.singletonList(scheduleId);
                    dataManager.deleteSchedules(scheduleIdList);
                } else {
                    dataManager.saveSchedules(Collections.singletonList(scheduleEntry));
                }
            }
        });
    }

    /**
     * Starts a delay timer for a schedule entry.
     *
     * @param scheduleEntry The schedule entry.
     * @param delay The delay in milliseconds.
     */
    private void startDelayTimer(ScheduleEntry scheduleEntry, long delay) {
        ScheduleRunnable runnable = new ScheduleRunnable(scheduleEntry.scheduleId, scheduleEntry.group) {
            @Override
            public void run() {
                // Update before execution
                Set<String> hashSet = new HashSet<>();
                hashSet.add(scheduleId);
                List<ScheduleEntry> scheduleEntries = dataManager.getScheduleEntries(hashSet);

                handleTriggeredSchedules(scheduleEntries);
            }
        };

        this.backgroundHandler.postAtTime(runnable, scheduleEntry.scheduleId, SystemClock.uptimeMillis() + delay);
        this.delayedRunnables.add(runnable);
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

        public ScheduleExecutorCallback(String scheduleId) {
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
        private AutomationDriver driver;
        private AutomationDataManager dataManager;
        public Analytics analytics;

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
         * Sets the {@link AutomationEngine}.
         *
         * @param dataManager The data manager.
         * @return The builder instance.
         */
        public Builder<T> setDataManager(AutomationDataManager dataManager) {
            this.dataManager = dataManager;
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
            Checks.checkArgument(limit > 0, "Missing schedule limit");

            return new AutomationEngine<>(this);
        }
    }
}
