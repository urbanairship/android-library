/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.os.Looper;

import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.iam.InAppAutomationScheduler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Subscriber for {@link com.urbanairship.remotedata.RemoteData}.
 *
 * @hide
 */
class InAppRemoteDataObserver {

    private static final String IAM_PAYLOAD_TYPE = "in_app_messages";

    // JSON keys
    private static final String MESSAGES_JSON_KEY = "in_app_messages";
    private static final String CREATED_JSON_KEY = "created";
    private static final String UPDATED_JSON_KEY = "last_updated";

    private static final String LIMIT_KEY = "limit";
    private static final String PRIORITY_KEY = "priority";
    private static final String GROUP_KEY = "group";
    private static final String END_KEY = "end";
    private static final String START_KEY = "start";
    private static final String DELAY_KEY = "delay";
    private static final String TRIGGERS_KEY = "triggers";
    private static final String EDIT_GRACE_PERIOD_KEY = "edit_grace_period";
    private static final String INTERVAL_KEY = "interval";
    private static final String AUDIENCE_KEY = "audience";
    private static final String MESSAGE_KEY = "message";

    // Data store keys
    private static final String LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP";
    private static final String LAST_PAYLOAD_METADATA = "com.urbanairship.iam.data.LAST_PAYLOAD_METADATA";
    private static final String SCHEDULE_NEW_USER_CUTOFF_TIME_KEY = "com.urbanairship.iam.data.NEW_USER_TIME";
    static final String REMOTE_DATA_METADATA = "com.urbanairship.iaa.REMOTE_DATA_METADATA";

    private final PreferenceDataStore preferenceDataStore;
    private final RemoteData remoteData;
    private final List<Listener> listeners = new ArrayList<>();

    private Subscription subscription;

    interface Listener {

        void onSchedulesUpdated();

    }

    /**
     * Default constructor.
     *
     * @param preferenceDataStore The preference data store.
     */
    InAppRemoteDataObserver(@NonNull PreferenceDataStore preferenceDataStore,
                            @NonNull final RemoteData remoteData) {

        this.preferenceDataStore = preferenceDataStore;
        this.remoteData = remoteData;
    }

    /**
     * Adds a listener.
     * <p>
     * Updates will be called on the looper provided in
     * {@link #subscribe(Looper, InAppAutomationScheduler)}.
     *
     * @param listener The listener to add.
     */
    void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener to remove.
     */
    void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Subscribes to remote data.
     *
     * @param looper The looper to process updates and callbacks on.
     * @param scheduler The scheduler.
     */
    void subscribe(@NonNull Looper looper, @NonNull final InAppAutomationScheduler scheduler) {
        cancel();

        this.subscription = remoteData.payloadsForType(IAM_PAYLOAD_TYPE)
                                      .filter(new Predicate<RemoteDataPayload>() {
                                          @Override
                                          public boolean apply(@NonNull RemoteDataPayload payload) {
                                              if (payload.getTimestamp() != preferenceDataStore.getLong(LAST_PAYLOAD_TIMESTAMP_KEY, -1)) {
                                                  return true;
                                              }

                                              return !payload.getMetadata().equals(getLastPayloadMetadata());
                                          }
                                      })
                                      .observeOn(Schedulers.looper(looper))
                                      .subscribe(new Subscriber<RemoteDataPayload>() {
                                          @Override
                                          public void onNext(@NonNull RemoteDataPayload payload) {
                                              try {
                                                  processPayload(payload, scheduler);
                                                  Logger.debug("InAppRemoteDataObserver - Finished processing messages.");
                                              } catch (Exception e) {
                                                  Logger.error(e, "InAppRemoteDataObserver - Failed to process payload: ");
                                              }
                                          }
                                      });
    }

    /**
     * Cancels the subscription.
     */
    void cancel() {
        if (this.subscription != null) {
            this.subscription.cancel();
        }
    }

    /**
     * Processes a payload.
     *
     * @param payload The remote data payload.
     * @param scheduler The scheduler.
     */
    private void processPayload(@NonNull RemoteDataPayload payload, @NonNull InAppAutomationScheduler scheduler) throws ExecutionException, InterruptedException {
        long lastUpdate = preferenceDataStore.getLong(LAST_PAYLOAD_TIMESTAMP_KEY, -1);
        JsonMap lastPayloadMetadata = getLastPayloadMetadata();

        JsonMap scheduleMetadata = JsonMap.newBuilder()
                                         .put(REMOTE_DATA_METADATA, payload.getMetadata())
                                         .build();

        boolean isMetadataUpToDate = payload.getMetadata().equals(lastPayloadMetadata);

        List<Schedule> newSchedules = new ArrayList<>();

        Set<String> scheduledRemoteIds = filterRemoteSchedules(scheduler.getSchedules().get());

        List<String> incomingScheduleIds = new ArrayList<>();

        for (JsonValue messageJson : payload.getData().opt(MESSAGES_JSON_KEY).optList()) {
            long createdTimeStamp, lastUpdatedTimeStamp;

            try {
                createdTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(CREATED_JSON_KEY).getString());
                lastUpdatedTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(UPDATED_JSON_KEY).getString());
            } catch (ParseException e) {
                Logger.error(e, "Failed to parse in-app message timestamps: %s", messageJson);
                continue;
            }

            String scheduleId = messageJson.optMap().opt("message").optMap().opt("message_id").getString();
            if (UAStringUtil.isEmpty(scheduleId)) {
                Logger.error("Missing schedule ID: %s", messageJson);
                continue;
            }

            incomingScheduleIds.add(scheduleId);

            // Ignore any messages that have not updated since the last payload
            if (isMetadataUpToDate && lastUpdatedTimeStamp <= lastUpdate) {
                continue;
            }

            if (createdTimeStamp > lastUpdate) {
                try {
                    Schedule schedule = parseSchedule(messageJson, scheduleMetadata);
                    if (checkSchedule(schedule, createdTimeStamp)) {
                        newSchedules.add(schedule);
                        Logger.debug("New in-app message: %s", schedule);
                    }
                } catch (JsonException e) {
                    Logger.error(e, "Failed to parse in-app message: %s", messageJson);
                }
            } else if (scheduledRemoteIds.contains(scheduleId)) {
                try {
                    ScheduleEdits originalEdits = parseEdits(messageJson);
                    ScheduleEdits edits = ScheduleEdits.newBuilder(originalEdits)
                                                       .setMetadata(scheduleMetadata)
                                                       // Since we cancel a schedule by setting the end and start time to the payload,
                                                       // clear them (-1) if the edits are null.
                                                       .setEnd(originalEdits.getEnd() == null ? -1 : originalEdits.getEnd())
                                                       .setStart(originalEdits.getStart() == null ? -1 : originalEdits.getStart())
                                                       .build();

                    Schedule schedule = scheduler.editSchedule(scheduleId, edits).get();
                    if (schedule != null) {
                        Logger.debug("Updated in-app message: %s with edits: %s", scheduleId, edits);
                    }
                } catch (JsonException e) {
                    Logger.error(e, "Failed to parse in-app message edits: %s", scheduleId);
                }
            }
        }

        // Schedule new in-app messages
        if (!newSchedules.isEmpty()) {
            scheduler.schedule(newSchedules).get();
        }

        // End any messages that are no longer in the listing
        Set<String> schedulesToRemove = new HashSet<>(scheduledRemoteIds);
        schedulesToRemove.removeAll(incomingScheduleIds);

        if (!schedulesToRemove.isEmpty()) {

            // To cancel, we need to set the end time to the payload's last modified timestamp. To avoid
            // validation errors, the start must to be equal to or before the end time. If the schedule
            // comes back, the edits will reapply the start and end times from the schedule edits.
            ScheduleEdits edits = ScheduleEdits.newBuilder()
                                               .setMetadata(scheduleMetadata)
                                               .setStart(payload.getTimestamp())
                                               .setEnd(payload.getTimestamp())
                                               .build();

            for (String scheduleId : schedulesToRemove) {
                scheduler.editSchedule(scheduleId, edits).get();
            }
        }

        // Store data
        preferenceDataStore.put(LAST_PAYLOAD_TIMESTAMP_KEY, payload.getTimestamp());
        preferenceDataStore.put(LAST_PAYLOAD_METADATA, payload.getMetadata());

        synchronized (listeners) {
            if (!listeners.isEmpty()) {
                List<Listener> listeners = new ArrayList<>(this.listeners);
                for (Listener listener : listeners) {
                    listener.onSchedulesUpdated();
                }
            }
        }
    }

    /**
     * Helper method to check if the message should be scheduled.
     *
     * @param schedule The schedule info.
     * @param createdTimeStamp The created times stamp.
     * @return {@code true} if the message should be scheduled, otherwise {@code false}.
     */
    private boolean checkSchedule(Schedule schedule, long createdTimeStamp) {
        Context context = UAirship.getApplicationContext();
        Audience audience = schedule.getAudience();
        boolean allowNewUser = createdTimeStamp <= getScheduleNewUserCutOffTime();
        return AudienceChecks.checkAudienceForScheduling(context, audience, allowNewUser);
    }

    @NonNull
    private Set<String> filterRemoteSchedules(@Nullable Collection<Schedule> schedules) {
        if (schedules == null) {
            return Collections.emptySet();
        }

        HashSet<String> scheduleIds = new HashSet<>();

        for (Schedule schedule : schedules) {
            if (isRemoteSchedule(schedule)) {
                scheduleIds.add(schedule.getId());
            }
        }

        return scheduleIds;
    }

    /**
     * Gets the schedule new user audience check cut off time.
     *
     * @return The new user cut off time.
     */
    long getScheduleNewUserCutOffTime() {
        return preferenceDataStore.getLong(SCHEDULE_NEW_USER_CUTOFF_TIME_KEY, -1);
    }

    /**
     * Sets the schedule new user cut off time. Any schedules that have
     * a new user condition will be dropped if the schedule create time is after the
     * cut off time.
     *
     * @param time The schedule new user cut off time.
     */
    void setScheduleNewUserCutOffTime(long time) {
        preferenceDataStore.put(SCHEDULE_NEW_USER_CUTOFF_TIME_KEY, time);
    }

    /**
     * Gets the last payload metadata.
     *
     * @return The last payload metadata.
     */
    private JsonMap getLastPayloadMetadata() {
        return preferenceDataStore.getJsonValue(LAST_PAYLOAD_METADATA).optMap();
    }

    /**
     * Creates a schedule info from a json value.
     *
     * @param value The json value.
     * @return A schedule info.
     * @throws JsonException If the json value contains an invalid schedule info.
     */
    public static Schedule parseSchedule(@NonNull JsonValue value, @Nullable JsonMap metadata) throws JsonException {
        JsonMap jsonMap = value.optMap();

        InAppMessage message = InAppMessage.fromJson(jsonMap.opt(MESSAGE_KEY), InAppMessage.SOURCE_REMOTE_DATA);

        Schedule.Builder builder = Schedule.newMessageScheduleBuilder(message)
                                           .setId(message.getId())
                                           .setMetadata(metadata)
                                           .setGroup(jsonMap.opt(GROUP_KEY).getString())
                                           .setLimit(jsonMap.opt(LIMIT_KEY).getInt(1))
                                           .setPriority(jsonMap.opt(PRIORITY_KEY).getInt(0));

        if (jsonMap.containsKey(END_KEY)) {
            try {
                builder.setEnd(DateUtils.parseIso8601(jsonMap.opt(END_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule end time", e);
            }
        }

        if (jsonMap.containsKey(START_KEY)) {
            try {
                builder.setStart(DateUtils.parseIso8601(jsonMap.opt(START_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule start time", e);
            }
        }

        for (JsonValue triggerJson : jsonMap.opt(TRIGGERS_KEY).optList()) {
            builder.addTrigger(Trigger.fromJson(triggerJson));
        }

        if (jsonMap.containsKey(DELAY_KEY)) {
            builder.setDelay(ScheduleDelay.fromJson(jsonMap.opt(DELAY_KEY)));
        }

        if (jsonMap.containsKey(EDIT_GRACE_PERIOD_KEY)) {
            builder.setEditGracePeriod(jsonMap.opt(EDIT_GRACE_PERIOD_KEY).getLong(0), TimeUnit.DAYS);
        }

        if (jsonMap.containsKey(INTERVAL_KEY)) {
            builder.setInterval(jsonMap.opt(INTERVAL_KEY).getLong(0), TimeUnit.SECONDS);
        }

        JsonValue audienceJson = jsonMap.opt(MESSAGE_KEY).optMap().get(AUDIENCE_KEY);
        if (audienceJson != null) {
            builder.setAudience(Audience.fromJson(audienceJson));
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid schedule", e);
        }
    }

    /**
     * Parses a json value for schedule edits.
     *
     * @param value The json value.
     * @return The edit info.
     * @throws JsonException If the json is invalid.
     */
    @NonNull
    public static ScheduleEdits parseEdits(@NonNull JsonValue value) throws JsonException {
        JsonMap jsonMap = value.optMap();

        ScheduleEdits.Builder builder = ScheduleEdits.newBuilder();

        if (jsonMap.containsKey(LIMIT_KEY)) {
            builder.setLimit(jsonMap.opt(LIMIT_KEY).getInt(1));
        }

        if (jsonMap.containsKey(PRIORITY_KEY)) {
            builder.setPriority(jsonMap.opt(PRIORITY_KEY).getInt(0));
        }

        if (jsonMap.containsKey(END_KEY)) {
            try {
                builder.setEnd(DateUtils.parseIso8601(jsonMap.opt(END_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule end time", e);
            }
        }

        if (jsonMap.containsKey(START_KEY)) {
            try {
                builder.setStart(DateUtils.parseIso8601(jsonMap.opt(START_KEY).getString()));
            } catch (ParseException e) {
                throw new JsonException("Invalid schedule start time", e);
            }
        }

        if (jsonMap.containsKey(EDIT_GRACE_PERIOD_KEY)) {
            builder.setEditGracePeriod(jsonMap.opt(EDIT_GRACE_PERIOD_KEY).getLong(0), TimeUnit.DAYS);
        }

        if (jsonMap.containsKey(INTERVAL_KEY)) {
            builder.setInterval(jsonMap.opt(INTERVAL_KEY).getLong(0), TimeUnit.SECONDS);
        }

        InAppMessage message = InAppMessage.fromJson(jsonMap.opt(MESSAGE_KEY), InAppMessage.SOURCE_REMOTE_DATA);
        builder.setData(message);

        return builder.build();
    }

    /**
     * Checks to see if the the observer has processed all updates from remote-data.
     *
     * @return {@code true} if the observer is up to date, otherwise {@code false}.
     */
    public boolean isUpToDate() {
        return remoteData.isMetadataCurrent(getLastPayloadMetadata());
    }

    /**
     * Checks to see if the schedule is from remote-data and is up-to-date.
     *
     * @param schedule The schedule.
     * @return {@code true} if the schedule valid, otherwise {@code false}.
     */
    public boolean isScheduleValid(@NonNull Schedule schedule) {
        return remoteData.isMetadataCurrent(schedule.getMetadata()
                                                    .opt(InAppRemoteDataObserver.REMOTE_DATA_METADATA)
                                                    .optMap());
    }

    /**
     * Checks to see if the schedule is from remote-data.
     *
     * @param schedule The schedule.
     * @return {@code true} if the schedule is from remote-data, otherwise {@code false}.
     */
    boolean isRemoteSchedule(@NonNull Schedule schedule) {
        if (schedule.getMetadata().containsKey(REMOTE_DATA_METADATA)) {
            return true;
        }

        // Legacy way of determining a remote-data schedule
        if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
            InAppMessage message = schedule.requireData();
            if (InAppMessage.SOURCE_REMOTE_DATA.equals(message.getSource())) {
                return true;
            }
        }

        return false;
    }

}
