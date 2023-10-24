/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;

import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UALog;
import com.urbanairship.UAirship;
import com.urbanairship.audience.AudienceSelector;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.limits.FrequencyConstraint;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataInfo;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.remotedata.RemoteDataSource;
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
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.ObjectsCompat;

/**
 * Subscriber for {@link com.urbanairship.remotedata.RemoteData}.
 *
 * @hide
 */
class InAppRemoteDataObserver {

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
    private static final String MESSAGE_TYPE_KEY = "message_type";
    private static final String BYPASS_HOLDOUT_GROUP_KEY = "bypass_holdout_groups";
    private static final String PRODUCT_ID_KEY = "product_id";

    // Data store keys
    private static final String APP_LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP";
    private static final String APP_LAST_PAYLOAD_INFO = "com.urbanairship.iam.data.last_payload_info";
    private static final String APP_LAST_SDK_VERSION_KEY = "com.urbanairship.iaa.last_sdk_version";

    private static final String CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.contact_last_payload_timestamp";
    private static final String CONTACT_LAST_PAYLOAD_INFO = "com.urbanairship.iam.data.contact_last_payload_info";
    private static final String CONTACT_LAST_SDK_VERSION_KEY = "com.urbanairship.iaa.contact_last_sdk_version";

    private static final String LAST_PAYLOAD_METADATA = "com.urbanairship.iam.data.LAST_PAYLOAD_METADATA";

    static final String REMOTE_DATA_METADATA = "com.urbanairship.iaa.REMOTE_DATA_METADATA";
    static final String REMOTE_DATA_INFO = "com.urbanairship.iaa.REMOTE_DATA_INFO";


    private static final String MIN_SDK_VERSION_KEY = "min_sdk_version";

    private final PreferenceDataStore preferenceDataStore;
    private final RemoteDataAccess remoteData;
    private final String sdkVersion;

    interface Delegate {

        @NonNull
        PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules();

        @NonNull
        PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits);

        @NonNull
        PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules);

        Future<Boolean> updateConstraints(@NonNull Collection<FrequencyConstraint> constraints);

    }

    InAppRemoteDataObserver(
            @NonNull Context context,
            @NonNull PreferenceDataStore preferenceDataStore,
            @NonNull final RemoteData remoteData
    ) {

        this.preferenceDataStore = preferenceDataStore;
        this.remoteData = new RemoteDataAccess(context, remoteData);
        this.sdkVersion = UAirship.getVersion();
    }

    @VisibleForTesting
    InAppRemoteDataObserver(@NonNull PreferenceDataStore preferenceDataStore,
                            @NonNull final RemoteDataAccess remoteDataAccess,
                            @NonNull String sdkVersion) {

        this.preferenceDataStore = preferenceDataStore;
        this.remoteData = remoteDataAccess;
        this.sdkVersion = sdkVersion;
    }

    /**
     * Subscribes to remote data.
     *
     * @param delegate The delegate.
     * @return A cancelable to cancel the subscription.
     */
    Cancelable subscribe(@NonNull final Delegate delegate) {
        return remoteData.subscribe((payloads) -> {
            try {
                processPayloads(payloads, delegate);
                UALog.d("Finished processing messages.");
            } catch (Exception e) {
                UALog.e(e, "InAppRemoteDataObserver - Failed to process payload: ");
            }
        });
    }

    @Nullable
    private RemoteDataPayload findPayload(@NonNull List<RemoteDataPayload> payloads, RemoteDataSource source) {
        for (RemoteDataPayload payload : payloads) {
            if (payload.getRemoteDataInfo() == null) {
                if (source == RemoteDataSource.APP) {
                    return payload;
                }
            } else if (payload.getRemoteDataInfo().getSource() == source) {
                return payload;
            }
        }
        return null;
    }
    private void processPayloads(@NonNull List<RemoteDataPayload> payloads, @NonNull Delegate delegate) throws ExecutionException, InterruptedException {
        // Fixes issue with 17.x -> 16.x -> 17.x
        if (this.preferenceDataStore.isSet(LAST_PAYLOAD_METADATA)) {
            this.preferenceDataStore.remove(LAST_PAYLOAD_METADATA);
            this.preferenceDataStore.remove(APP_LAST_PAYLOAD_INFO);
            this.preferenceDataStore.remove(CONTACT_LAST_PAYLOAD_INFO);
        }

        if (payloads.isEmpty()) {
            return;
        }

        processAppPayload(findPayload(payloads, RemoteDataSource.APP), delegate);
        processContactPayload(findPayload(payloads, RemoteDataSource.CONTACT), delegate);
    }

    private void processAppPayload(@Nullable RemoteDataPayload payload, @NonNull Delegate delegate) throws ExecutionException, InterruptedException {
        if (payload == null) {
            stopAll(RemoteDataSource.APP, delegate);
            preferenceDataStore.remove(APP_LAST_PAYLOAD_INFO);
            return;
        }

        long lastUpdate = preferenceDataStore.getLong(APP_LAST_PAYLOAD_TIMESTAMP_KEY, -1);
        RemoteDataInfo lastPayloadRemoteInfo = getLastPayloadRemoteInfo(APP_LAST_PAYLOAD_INFO);
        String lastSdkVersion = preferenceDataStore.getString(APP_LAST_SDK_VERSION_KEY, null);

        boolean processed =  processPayload(payload, delegate, lastPayloadRemoteInfo, lastUpdate, lastSdkVersion, RemoteDataSource.APP);

        // Store data
        if (processed) {
            preferenceDataStore.put(APP_LAST_PAYLOAD_TIMESTAMP_KEY, payload.getTimestamp());
            preferenceDataStore.put(APP_LAST_PAYLOAD_INFO, payload.getRemoteDataInfo());
            preferenceDataStore.put(APP_LAST_SDK_VERSION_KEY, sdkVersion);
        }
    }

    private void processContactPayload(@Nullable RemoteDataPayload payload, @NonNull Delegate delegate) throws ExecutionException, InterruptedException {
        if (payload == null) {
            stopAll(RemoteDataSource.CONTACT, delegate);
            preferenceDataStore.remove(CONTACT_LAST_PAYLOAD_INFO);
            return;
        }

        String contactId = "";
        if (payload.getRemoteDataInfo() != null && payload.getRemoteDataInfo().getContactId()  != null) {
            contactId = payload.getRemoteDataInfo().getContactId();
        }

        // We store the last update and the last SDK version with the contact ID in the key so we can
        // probably detect new schedules across contact ID changes. We continue to store the last payload
        // info without the contact Id so we can detect when we should process the listing again
        long lastUpdate = preferenceDataStore.getLong(CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY + contactId, -1);
        String lastSdkVersion = preferenceDataStore.getString(CONTACT_LAST_SDK_VERSION_KEY + contactId, null);

        RemoteDataInfo lastPayloadRemoteInfo = getLastPayloadRemoteInfo(CONTACT_LAST_PAYLOAD_INFO);

        boolean processed =  processPayload(payload, delegate, lastPayloadRemoteInfo, lastUpdate, lastSdkVersion, RemoteDataSource.CONTACT);

        // Store data
        if (processed) {
            preferenceDataStore.put(CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY + contactId, payload.getTimestamp());
            preferenceDataStore.put(CONTACT_LAST_SDK_VERSION_KEY + contactId, sdkVersion);
            preferenceDataStore.put(CONTACT_LAST_PAYLOAD_INFO, payload.getRemoteDataInfo());
        }
    }

    private Boolean processPayload(
            @NonNull RemoteDataPayload payload,
            @NonNull Delegate delegate,
            @Nullable RemoteDataInfo lastPayloadRemoteInfo,
            long lastUpdate,
            @Nullable String lastSdkVersion,
            @NonNull RemoteDataSource source
    ) throws ExecutionException, InterruptedException {
        boolean isMetadataUpToDate = ObjectsCompat.equals(payload.getRemoteDataInfo(), lastPayloadRemoteInfo);

        if (lastUpdate == payload.getTimestamp() && isMetadataUpToDate) {
            return false;
        }

        JsonMap scheduleMetadata = JsonMap.newBuilder()
                                          .put(REMOTE_DATA_INFO, payload.getRemoteDataInfo())
                                          .putOpt(REMOTE_DATA_METADATA, JsonMap.EMPTY_MAP) // for downgrades
                                          .build();


        List<Schedule<? extends ScheduleData>> newSchedules = new ArrayList<>();
        List<String> incomingScheduleIds = new ArrayList<>();
        Set<String> scheduledRemoteIds = filterRemoteSchedules(delegate.getSchedules().get(), source);
        Collection<FrequencyConstraint> constraints = parseConstraints(payload.getData().opt(CONSTRAINTS_JSON_KEY).optList());

        // Update constraints
        if (!delegate.updateConstraints(constraints).get()) {
            return false;
        }


        // Parse messages
        for (JsonValue messageJson : payload.getData().opt(MESSAGES_JSON_KEY).optList()) {
            long createdTimeStamp, lastUpdatedTimeStamp;

            try {
                createdTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(CREATED_JSON_KEY).getString());
                lastUpdatedTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(UPDATED_JSON_KEY).getString());
            } catch (ParseException e) {
                UALog.e(e, "Failed to parse in-app message timestamps: %s", messageJson);
                continue;
            }

            String scheduleId = parseScheduleId(messageJson);
            if (UAStringUtil.isEmpty(scheduleId)) {
                UALog.e("Missing schedule ID: %s", messageJson);
                continue;
            }

            incomingScheduleIds.add(scheduleId);

            // Ignore any messages that have not updated since the last payload
            if (isMetadataUpToDate && lastUpdatedTimeStamp <= lastUpdate) {
                continue;
            }

            if (scheduledRemoteIds.contains(scheduleId)) {
                try {
                    ScheduleEdits<?> edits = parseEdits(messageJson, scheduleMetadata, createdTimeStamp);
                    Boolean edited = delegate.editSchedule(scheduleId, edits).get();
                    if (edited != null && edited) {
                        UALog.d("Updated in-app automation: %s with edits: %s", scheduleId, edits);
                    }
                } catch (JsonException e) {
                    UALog.e(e, "Failed to parse in-app automation edits: %s", scheduleId);
                }
            } else {
                String minSdkVersion = messageJson.optMap().opt(MIN_SDK_VERSION_KEY).optString();
                if (isNewSchedule(minSdkVersion, lastSdkVersion, createdTimeStamp, lastUpdate)) {
                    try {
                        Schedule<? extends ScheduleData> schedule = parseSchedule(scheduleId, messageJson, scheduleMetadata, createdTimeStamp);
                        newSchedules.add(schedule);
                        UALog.d("New in-app automation: %s", schedule);
                    } catch (Exception e) {
                        UALog.e(e, "Failed to parse in-app automation: %s", messageJson);
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

        return true;
    }

    private void stopAll(RemoteDataSource source, @NonNull Delegate delegate) throws ExecutionException, InterruptedException {
        Set<String> scheduledRemoteIds = filterRemoteSchedules(delegate.getSchedules().get(), source);
        if (scheduledRemoteIds.isEmpty()) {
            return;
        }

        long time = System.currentTimeMillis();
        ScheduleEdits<? extends ScheduleData> edits = ScheduleEdits.newBuilder()
                                                                   .setStart(time)
                                                                   .setEnd(time)
                                                                   .build();

        for (String scheduleId : scheduledRemoteIds) {
            delegate.editSchedule(scheduleId, edits).get();
        }
    }

    @NonNull
    private Collection<FrequencyConstraint> parseConstraints(@NonNull JsonList constraintsJson) {
        List<FrequencyConstraint> constraints = new ArrayList<>();
        for (JsonValue value : constraintsJson) {
            try {
                constraints.add(parseConstraint(value.optMap()));
            } catch (JsonException e) {
                UALog.e(e, "Invalid constraint: " + value);
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
    private static AudienceSelector parseAudience(@NonNull JsonValue jsonValue) throws JsonException {
        JsonValue audienceJson = jsonValue.optMap().get(AUDIENCE_KEY);
        if (audienceJson == null) {
            // Legacy
            audienceJson = jsonValue.optMap().opt(MESSAGE_KEY).optMap().get(AUDIENCE_KEY);
        }

        return audienceJson == null ? null : AudienceSelector.Companion.fromJson(audienceJson);
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
    private Set<String> filterRemoteSchedules(@Nullable Collection<Schedule<? extends ScheduleData>> schedules, RemoteDataSource source) {
        if (schedules == null) {
            return Collections.emptySet();
        }

        HashSet<String> scheduleIds = new HashSet<>();

        for (Schedule<? extends ScheduleData> schedule : schedules) {
            if (!isRemoteSchedule(schedule)) {
                continue;
            }

            RemoteDataInfo info = parseRemoteDataInfo(schedule);
            if (info == null && source == RemoteDataSource.APP) {
                scheduleIds.add(schedule.getId());
            } else if (info != null && source == info.getSource()) {
                scheduleIds.add(schedule.getId());
            }
        }

        return scheduleIds;
    }

    @Nullable
    private RemoteDataInfo getLastPayloadRemoteInfo(@NonNull String key) {
        JsonValue jsonValue = preferenceDataStore.getJsonValue(key);
        if (jsonValue.isNull()) {
            return null;
        }

        try {
            return new RemoteDataInfo(jsonValue);
        } catch (JsonException e) {
            UALog.e(e, "Failed to parse remote info.");
            return null;
        }
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
    public static Schedule<? extends ScheduleData> parseSchedule(
            @NonNull String scheduleId,
            @NonNull JsonValue value,
            @NonNull JsonMap metadata,
            long createdDate) throws JsonException {
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
               .setFrequencyConstraintIds(parseConstraintIds(jsonMap.opt(FREQUENCY_CONSTRAINT_IDS_KEY).optList()))
               .setMessageType(jsonMap.opt(MESSAGE_TYPE_KEY).getString())
               .setBypassHoldoutGroups(jsonMap.opt(BYPASS_HOLDOUT_GROUP_KEY).getBoolean())
               .setNewUserEvaluationDate(createdDate)
               .setProductId(jsonMap.opt(PRODUCT_ID_KEY).getString());

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
    public static ScheduleEdits<? extends ScheduleData> parseEdits(
            @NonNull JsonValue value,
            @Nullable JsonMap scheduleMetadata,
            long createdDate) throws JsonException {
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
               .setFrequencyConstraintIds(parseConstraintIds(jsonMap.opt(FREQUENCY_CONSTRAINT_IDS_KEY).optList()))
               .setMessageType(jsonMap.opt(MESSAGE_TYPE_KEY).getString())
               .setBypassHoldoutGroup(jsonMap.opt(BYPASS_HOLDOUT_GROUP_KEY).getBoolean())
               .setNewUserEvaluationDate(createdDate);

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
     * Checks to see if the schedule is valid.
     *
     * @param schedule The schedule.
     * @return {@code true} if the schedule valid, otherwise {@code false}.
     */
    public boolean isScheduleValid(@NonNull Schedule<? extends ScheduleData> schedule) {
        if (!isRemoteSchedule(schedule)) {
            return true;
        }

        RemoteDataInfo remoteDataInfo = parseRemoteDataInfo(schedule);
        if (remoteDataInfo == null) {
            return false;
        }

        return remoteData.isCurrent(remoteDataInfo);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Nullable
    public RemoteDataInfo parseRemoteDataInfo(@NonNull Schedule<? extends ScheduleData> schedule) {
        JsonValue value = schedule.getMetadata().get(InAppRemoteDataObserver.REMOTE_DATA_INFO);
        if (value == null) {
            return null;
        }

        try {
            return new RemoteDataInfo(value);
        } catch (JsonException e) {
            UALog.e(e, "Failed to parse remote info.");
            return null;
        }
    }

    /**
     * Checks to see if the schedule is from remote-data.
     *
     * @param schedule The schedule.
     * @return {@code true} if the schedule is from remote-data, otherwise {@code false}.
     */
    public boolean isRemoteSchedule(@NonNull Schedule<? extends ScheduleData> schedule) {
        // 17+
        if (schedule.getMetadata().containsKey(REMOTE_DATA_INFO)) {
            return true;
        }

        // Older
        if (schedule.getMetadata().containsKey(REMOTE_DATA_METADATA)) {
            return true;
        }

        // Fallback
        if (Schedule.TYPE_IN_APP_MESSAGE.equals(schedule.getType())) {
            InAppMessage message = schedule.coerceType();
            return InAppMessage.SOURCE_REMOTE_DATA.equals(message.getSource());
        }

        return false;
    }


    public boolean requiresRefresh(@NonNull Schedule<? extends ScheduleData> schedule) {
        if (!isRemoteSchedule(schedule)) {
            return false;
        }

        RemoteDataInfo remoteDataInfo = parseRemoteDataInfo(schedule);
        return remoteData.requiresRefresh(remoteDataInfo);
    }

    @WorkerThread
    public void waitFullRefresh(@NonNull Schedule<? extends ScheduleData> schedule, @NonNull Runnable runnable) {
        RemoteDataInfo remoteDataInfo = parseRemoteDataInfo(schedule);
        remoteData.waitFullRefresh(remoteDataInfo, runnable);
    }

    @WorkerThread
    public void notifyOutdated(@NonNull Schedule<? extends ScheduleData> schedule) {
        RemoteDataInfo remoteDataInfo = parseRemoteDataInfo(schedule);
        remoteData.notifyOutdated(remoteDataInfo);
    }

    @WorkerThread
    public boolean bestEffortRefresh(@NonNull Schedule<? extends ScheduleData> schedule) {
        if (!isRemoteSchedule(schedule)) {
            return true;
        }

        RemoteDataInfo remoteDataInfo = parseRemoteDataInfo(schedule);
        return remoteData.bestEffortRefresh(remoteDataInfo);
    }
}
