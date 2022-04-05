/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.os.Looper;

import com.urbanairship.AirshipLoopers;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.limits.FrequencyConstraint;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Schedulers;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.VersionUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
    private static final String CONSTRAINTS_JSON_KEY = "frequency_constraints";

    private static final String CONSTRAINTS_PERIOD_KEY = "period";
    private static final String CONSTRAINT_ID_KEY = "id";
    private static final String CONSTRAINT_RANGE_KEY = "range";
    private static final String CONSTRAINT_BOUNDARY_KEY = "boundary";

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
    private static final String TYPE_KEY = "type";
    private static final String SCHEDULE_ID_KEY = "id";
    private static final String LEGACY_MESSAGE_ID_KEY = "message_id";

    private static final String MESSAGE_KEY = "message";
    private static final String DEFERRED_KEY = "deferred";
    private static final String ACTIONS_KEY = "actions";
    private static final String CAMPAIGNS_KEY = "campaigns";
    private static final String REPORTING_CONTEXT_KEY = "reporting_context";
    private static final String FREQUENCY_CONSTRAINT_IDS_KEY = "frequency_constraint_ids";

    // Data store keys
    private static final String LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP";
    private static final String LAST_PAYLOAD_METADATA = "com.urbanairship.iam.data.LAST_PAYLOAD_METADATA";
    private static final String SCHEDULE_NEW_USER_CUTOFF_TIME_KEY = "com.urbanairship.iam.data.NEW_USER_TIME";
    static final String REMOTE_DATA_METADATA = "com.urbanairship.iaa.REMOTE_DATA_METADATA";
    static final String LAST_SDK_VERSION_KEY = "com.urbanairship.iaa.last_sdk_version";

    private static final String MIN_SDK_VERSION_KEY = "min_sdk_version";

    private final PreferenceDataStore preferenceDataStore;
    private final RemoteData remoteData;
    private final List<Listener> listeners = new ArrayList<>();
    private final String sdkVersion;
    private final Looper looper;

    interface Listener {

        void onSchedulesUpdated();

    }

    interface Delegate {

        @NonNull
        PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules();

        @NonNull
        PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits);

        @NonNull
        PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules);

        Future<Boolean> updateConstraints(@NonNull Collection<FrequencyConstraint> constraints);

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
        this.sdkVersion = UAirship.getVersion();
        this.looper = AirshipLoopers.getBackgroundLooper();
    }

    InAppRemoteDataObserver(@NonNull PreferenceDataStore preferenceDataStore,
                            @NonNull final RemoteData remoteData,
                            @NonNull String sdkVersion,
                            @NonNull Looper looper) {

        this.preferenceDataStore = preferenceDataStore;
        this.remoteData = remoteData;
        this.sdkVersion = sdkVersion;
        this.looper = looper;
    }

    /**
     * Adds a listener.
     * <p>
     * Updates will be called on the looper provided in
     * {@link #subscribe(Delegate)}.
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
     * @param delegate The delegate.
     * @return The subcription.
     */
    Subscription subscribe(@NonNull final Delegate delegate) {
        return remoteData.payloadsForType(IAM_PAYLOAD_TYPE)
                         .filter(payload -> {
                             if (payload.getTimestamp() != preferenceDataStore.getLong(LAST_PAYLOAD_TIMESTAMP_KEY, -1)) {
                                 return true;
                             }

                             return !payload.getMetadata().equals(getLastPayloadMetadata());
                         })
                         .observeOn(Schedulers.looper(looper))
                         .subscribeOn(Schedulers.looper(looper))
                         .subscribe(new Subscriber<RemoteDataPayload>() {
                             @Override
                             public void onNext(@NonNull RemoteDataPayload payload) {
                                 try {
                                     processPayload(payload, delegate);
                                     Logger.debug("Finished processing messages.");
                                 } catch (Exception e) {
                                     Logger.error(e, "InAppRemoteDataObserver - Failed to process payload: ");
                                 }
                             }
                         });
    }

    /**
     * Processes a payload.
     *
     * @param payload The remote data payload.
     * @param delegate The delegate.
     */
    private void processPayload(@NonNull RemoteDataPayload payload, @NonNull Delegate delegate) throws ExecutionException, InterruptedException {
        long lastUpdate = preferenceDataStore.getLong(LAST_PAYLOAD_TIMESTAMP_KEY, -1);
        JsonMap lastPayloadMetadata = getLastPayloadMetadata();

        JsonMap scheduleMetadata = JsonMap.newBuilder()
                                          .put(REMOTE_DATA_METADATA, payload.getMetadata())
                                          .build();

        boolean isMetadataUpToDate = payload.getMetadata().equals(lastPayloadMetadata);
        List<Schedule<? extends ScheduleData>> newSchedules = new ArrayList<>();
        List<String> incomingScheduleIds = new ArrayList<>();
        Set<String> scheduledRemoteIds = filterRemoteSchedules(delegate.getSchedules().get());
        Collection<FrequencyConstraint> constraints = parseConstraints(payload.getData().opt(CONSTRAINTS_JSON_KEY).optList());

        // Update constraints
        if (!delegate.updateConstraints(constraints).get()) {
            return;
        }

        String lastSdkVersion = preferenceDataStore.getString(LAST_SDK_VERSION_KEY, null);

        // Parse messages
        for (JsonValue messageJson : payload.getData().opt(MESSAGES_JSON_KEY).optList()) {
            long createdTimeStamp, lastUpdatedTimeStamp;

            try {
                createdTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(CREATED_JSON_KEY).getString());
                lastUpdatedTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(UPDATED_JSON_KEY).getString());
            } catch (ParseException e) {
                Logger.error(e, "Failed to parse in-app message timestamps: %s", messageJson);
                continue;
            }

            String scheduleId = parseScheduleId(messageJson);
            if (UAStringUtil.isEmpty(scheduleId)) {
                Logger.error("Missing schedule ID: %s", messageJson);
                continue;
            }

            incomingScheduleIds.add(scheduleId);

            // Ignore any messages that have not updated since the last payload
            if (isMetadataUpToDate && lastUpdatedTimeStamp <= lastUpdate) {
                continue;
            }
            if (scheduledRemoteIds.contains(scheduleId)) {
                try {
                    ScheduleEdits<?> edits = parseEdits(messageJson, scheduleMetadata);
                    Boolean edited = delegate.editSchedule(scheduleId, edits).get();
                    if (edited != null && edited) {
                        Logger.debug("Updated in-app automation: %s with edits: %s", scheduleId, edits);
                    }
                } catch (JsonException e) {
                    Logger.error(e, "Failed to parse in-app automation edits: %s", scheduleId);
                }
            } else {
                String minSdkVersion = messageJson.optMap().opt(MIN_SDK_VERSION_KEY).optString();
                if (isNewSchedule(minSdkVersion, lastSdkVersion, createdTimeStamp, lastUpdate)) {
                    try {
                        Schedule<? extends ScheduleData> schedule = parseSchedule(scheduleId, messageJson, scheduleMetadata);
                        if (shouldSchedule(schedule, createdTimeStamp)) {
                            newSchedules.add(schedule);
                            Logger.debug("New in-app automation: %s", schedule);
                        }
                    } catch (Exception e) {
                        Logger.error(e, "Failed to parse in-app automation: %s", messageJson);
                    }
                }
            }
        }

        // Schedule new in-app messages
        if (!newSchedules.isEmpty()) {
            delegate.schedule(newSchedules).get();
        }

        // End any messages that are no longer in the listing
        Set<String> schedulesToRemove = new HashSet<>(scheduledRemoteIds);
        schedulesToRemove.removeAll(incomingScheduleIds);

        if (!schedulesToRemove.isEmpty()) {

            // To cancel, we need to set the end time to the payload's last modified timestamp. To avoid
            // validation errors, the start must to be equal to or before the end time. If the schedule
            // comes back, the edits will reapply the start and end times from the schedule edits.
            ScheduleEdits<? extends ScheduleData> edits = ScheduleEdits.newBuilder()
                                                                       .setMetadata(scheduleMetadata)
                                                                       .setStart(payload.getTimestamp())
                                                                       .setEnd(payload.getTimestamp())
                                                                       .build();

            for (String scheduleId : schedulesToRemove) {
                delegate.editSchedule(scheduleId, edits).get();
            }
        }

        // Store data
        preferenceDataStore.put(LAST_PAYLOAD_TIMESTAMP_KEY, payload.getTimestamp());
        preferenceDataStore.put(LAST_PAYLOAD_METADATA, payload.getMetadata());
        preferenceDataStore.put(LAST_SDK_VERSION_KEY, sdkVersion);

        synchronized (listeners) {
            if (!listeners.isEmpty()) {
                List<Listener> listeners = new ArrayList<>(this.listeners);
                for (Listener listener : listeners) {
                    listener.onSchedulesUpdated();
                }
            }
        }
    }

    @NonNull
    private Collection<FrequencyConstraint> parseConstraints(@NonNull JsonList constraintsJson) {
        List<FrequencyConstraint> constraints = new ArrayList<>();
        for (JsonValue value : constraintsJson) {
            try {
                constraints.add(parseConstraint(value.optMap()));
            } catch (JsonException e) {
                Logger.error(e, "Invalid constraint: " + value);
            }
        }
        return constraints;
    }

    @NonNull
    private FrequencyConstraint parseConstraint(@NonNull JsonMap constraintJson) throws JsonException {
        FrequencyConstraint.Builder builder = FrequencyConstraint.newBuilder()
                                                                 .setId(constraintJson.opt(CONSTRAINT_ID_KEY).getString())
                                                                 .setCount(constraintJson.opt(CONSTRAINT_BOUNDARY_KEY).getInt(0));

        long range = constraintJson.opt(CONSTRAINT_RANGE_KEY).getLong(0);
        String period = constraintJson.opt(CONSTRAINTS_PERIOD_KEY).optString();

        switch (period) {
            case "seconds":
                builder.setRange(TimeUnit.SECONDS, range);
                break;
            case "minutes":
                builder.setRange(TimeUnit.MINUTES, range);
                break;
            case "hours":
                builder.setRange(TimeUnit.HOURS, range);
                break;
            case "days":
                builder.setRange(TimeUnit.DAYS, range);
                break;
            case "weeks":
                builder.setRange(TimeUnit.DAYS, range * 7);
                break;
            case "months":
                builder.setRange(TimeUnit.DAYS, range * 30);
                break;
            case "years":
                builder.setRange(TimeUnit.DAYS, range * 365);
                break;
            default:
                throw new JsonException("Invalid period: " + period);
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid constraint: " + constraintJson, e);
        }
    }

    @Nullable
    private static String parseScheduleId(JsonValue json) {
        String scheduleId = json.optMap().opt(SCHEDULE_ID_KEY).getString();
        if (scheduleId == null) {
            // LEGACY
            scheduleId = json.optMap().opt(MESSAGE_KEY).optMap().opt(LEGACY_MESSAGE_ID_KEY).getString();
        }
        return scheduleId;
    }

    @Nullable
    private static Audience parseAudience(@NonNull JsonValue jsonValue) throws JsonException {
        JsonValue audienceJson = jsonValue.optMap().get(AUDIENCE_KEY);
        if (audienceJson == null) {
            // Legacy
            audienceJson = jsonValue.optMap().opt(MESSAGE_KEY).optMap().get(AUDIENCE_KEY);
        }

        return audienceJson == null ? null : Audience.fromJson(audienceJson);
    }

    /**
     * Helper method to check if the message should be scheduled.
     *
     * @param schedule The schedule info.
     * @param createdTimeStamp The created times stamp.
     * @return {@code true} if the message should be scheduled, otherwise {@code false}.
     */
    private boolean shouldSchedule(Schedule<? extends ScheduleData> schedule, long createdTimeStamp) {
        Context context = UAirship.getApplicationContext();
        Audience audience = schedule.getAudience();
        boolean allowNewUser = createdTimeStamp <= getScheduleNewUserCutOffTime();
        return AudienceChecks.checkAudienceForScheduling(context, audience, allowNewUser);
    }

    private boolean isNewSchedule(@Nullable String minSdkVersion,
                                  @Nullable String lastSdkVersion,
                                  long createdTimeStamp,
                                  long lastUpdateTimeStamp) {

        if (createdTimeStamp > lastUpdateTimeStamp) {
            return true;
        }

        if (UAStringUtil.isEmpty(minSdkVersion)) {
            return false;
        }

        // We can skip checking if the min_sdk_version is newer than the current SDK version since
        // remote-data will filter them out. This flag is only a hint to the SDK to treat a schedule with
        // an older created timestamp as a new schedule.

        if (UAStringUtil.isEmpty(lastSdkVersion)) {
            // If we do not have a last SDK version, then we are coming from an SDK older than
            // 16.2.0. Check for a min SDK version newer or equal to 16.2.0.
            return VersionUtils.isVersionNewerOrEqualTo("16.2.0", minSdkVersion);
        } else {
            // Check versions to see if minSdkVersion is newer than lastSdkVersion
            return VersionUtils.isVersionNewer(lastSdkVersion, minSdkVersion);
        }
    }

    @NonNull
    private Set<String> filterRemoteSchedules(@Nullable Collection<Schedule<? extends ScheduleData>> schedules) {
        if (schedules == null) {
            return Collections.emptySet();
        }

        HashSet<String> scheduleIds = new HashSet<>();

        for (Schedule<? extends ScheduleData> schedule : schedules) {
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
     * @param scheduleId The schedule ID.
     * @param value The schedule JSON.
     * @param metadata The schedule metadata.
     * @return A schedule info.
     * @throws JsonException If the json value contains an invalid schedule info.
     */
    public static Schedule<? extends ScheduleData> parseSchedule(@NonNull String scheduleId, @NonNull JsonValue value, @NonNull JsonMap metadata) throws JsonException {
        JsonMap jsonMap = value.optMap();

        // Fallback to in-app message to support legacy messages
        String type = jsonMap.opt(TYPE_KEY).getString(Schedule.TYPE_IN_APP_MESSAGE);

        Schedule.Builder<? extends ScheduleData> builder;
        switch (type) {
            case Schedule.TYPE_ACTION:
                JsonMap actionsMap = jsonMap.opt(ACTIONS_KEY).getMap();
                if (actionsMap == null) {
                    throw new JsonException("Missing actions payload");
                }
                builder = Schedule.newBuilder(new Actions(actionsMap));
                break;
            case Schedule.TYPE_IN_APP_MESSAGE:
                InAppMessage message = InAppMessage.fromJson(jsonMap.opt(MESSAGE_KEY), InAppMessage.SOURCE_REMOTE_DATA);
                builder = Schedule.newBuilder(message);
                break;
            case Schedule.TYPE_DEFERRED:
                Deferred deferred = Deferred.fromJson(jsonMap.opt(DEFERRED_KEY));
                builder = Schedule.newBuilder(deferred);
                break;
            default:
                throw new JsonException("Unexpected type: " + type);
        }

        builder.setId(scheduleId)
               .setMetadata(metadata)
               .setGroup(jsonMap.opt(GROUP_KEY).getString())
               .setLimit(jsonMap.opt(LIMIT_KEY).getInt(1))
               .setPriority(jsonMap.opt(PRIORITY_KEY).getInt(0))
               .setCampaigns(jsonMap.opt(CAMPAIGNS_KEY))
               .setReportingContext(jsonMap.opt(REPORTING_CONTEXT_KEY))
               .setAudience(parseAudience(value))
               .setEditGracePeriod(jsonMap.opt(EDIT_GRACE_PERIOD_KEY).getLong(0), TimeUnit.DAYS)
               .setInterval(jsonMap.opt(INTERVAL_KEY).getLong(0), TimeUnit.SECONDS)
               .setStart(parseTimeStamp(jsonMap.opt(START_KEY).getString()))
               .setEnd(parseTimeStamp(jsonMap.opt(END_KEY).getString()))
               .setFrequencyConstraintIds(parseConstraintIds(jsonMap.opt(FREQUENCY_CONSTRAINT_IDS_KEY).optList()));

        for (JsonValue triggerJson : jsonMap.opt(TRIGGERS_KEY).optList()) {
            builder.addTrigger(Trigger.fromJson(triggerJson));
        }

        if (jsonMap.containsKey(DELAY_KEY)) {
            builder.setDelay(ScheduleDelay.fromJson(jsonMap.opt(DELAY_KEY)));
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
     * @param scheduleMetadata The metadata.
     * @return The edit info.
     * @throws JsonException If the json is invalid.
     */
    @NonNull
    public static ScheduleEdits<? extends ScheduleData> parseEdits(@NonNull JsonValue value, @Nullable JsonMap scheduleMetadata) throws JsonException {
        JsonMap jsonMap = value.optMap();

        // Fallback to in-app message to support legacy messages
        String type = jsonMap.opt(TYPE_KEY).getString(Schedule.TYPE_IN_APP_MESSAGE);
        ScheduleEdits.Builder<? extends ScheduleData> builder;
        switch (type) {
            case Schedule.TYPE_ACTION:
                JsonMap actionsMap = jsonMap.opt(ACTIONS_KEY).getMap();
                if (actionsMap == null) {
                    throw new JsonException("Missing actions payload");
                }
                builder = ScheduleEdits.newBuilder(new Actions(actionsMap));
                break;
            case Schedule.TYPE_IN_APP_MESSAGE:
                InAppMessage message = InAppMessage.fromJson(jsonMap.opt(MESSAGE_KEY), InAppMessage.SOURCE_REMOTE_DATA);
                builder = ScheduleEdits.newBuilder(message);
                break;
            case Schedule.TYPE_DEFERRED:
                Deferred deferred = Deferred.fromJson(jsonMap.opt(DEFERRED_KEY));
                builder = ScheduleEdits.newBuilder(deferred);
                break;
            default:
                throw new JsonException("Unexpected schedule type: " + type);
        }

        builder.setMetadata(scheduleMetadata)
               .setLimit(jsonMap.opt(LIMIT_KEY).getInt(1))
               .setPriority(jsonMap.opt(PRIORITY_KEY).getInt(0))
               .setEditGracePeriod(jsonMap.opt(EDIT_GRACE_PERIOD_KEY).getLong(0), TimeUnit.DAYS)
               .setInterval(jsonMap.opt(INTERVAL_KEY).getLong(0), TimeUnit.SECONDS)
               .setAudience(parseAudience(value))
               .setCampaigns(jsonMap.opt(CAMPAIGNS_KEY))
               .setReportingContext(jsonMap.opt(REPORTING_CONTEXT_KEY))
               .setStart(parseTimeStamp(jsonMap.opt(START_KEY).getString()))
               .setEnd(parseTimeStamp(jsonMap.opt(END_KEY).getString()))
               .setFrequencyConstraintIds(parseConstraintIds(jsonMap.opt(FREQUENCY_CONSTRAINT_IDS_KEY).optList()));

        return builder.build();
    }

    private static long parseTimeStamp(@Nullable String timeStamp) throws JsonException {
        if (timeStamp == null) {
            return -1;
        }

        try {
            return DateUtils.parseIso8601(timeStamp);
        } catch (ParseException e) {
            throw new JsonException("Invalid timestamp: " + timeStamp, e);
        }
    }

    @NonNull
    private static List<String> parseConstraintIds(@NonNull JsonList optList) throws JsonException {
        List<String> constraintIds = new ArrayList<>();
        for (JsonValue value : optList) {
            if (!value.isString()) {
                throw new JsonException("Invalid constraint ID: " + value);
            }
            constraintIds.add(value.optString());
        }
        return constraintIds;
    }

    /**
     * Checks to see if the the observer has processed all updates from remote-data.
     *
     * @return {@code true} if the observer is up to date, otherwise {@code false}.
     */
    private boolean isUpToDate() {
        return remoteData.isMetadataCurrent(getLastPayloadMetadata());
    }

    /**
     * Checks to see if the schedule is valid.
     *
     * @param schedule The schedule.
     * @return {@code true} if the schedule valid, otherwise {@code false}.
     */
    public boolean isScheduleValid(@NonNull Schedule<? extends ScheduleData> schedule) {
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
    public boolean isRemoteSchedule(@NonNull Schedule<? extends ScheduleData> schedule) {
        if (schedule.getMetadata().containsKey(REMOTE_DATA_METADATA)) {
            return true;
        }

        if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
            InAppMessage message = schedule.coerceType();
            return InAppMessage.SOURCE_REMOTE_DATA.equals(message.getSource());
        }

        return false;
    }

    public void attemptRefresh(@NonNull Runnable onComplete) {
        remoteData.refresh().addResultCallback(result -> {
            if (result == null || !result) {
                Logger.debug("Failed to refresh remote-data.");
            }

            if (isUpToDate()) {
                onComplete.run();
            } else {
                addListener(new Listener() {
                    @Override
                    public void onSchedulesUpdated() {
                        removeListener(this);
                        onComplete.run();
                    }
                });
            }
        });
    }
}
