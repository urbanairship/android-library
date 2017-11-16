/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Observer;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Observer for {@link com.urbanairship.remotedata.RemoteData}.
 */
class InAppRemoteDataObserver implements Observer<RemoteDataPayload> {

    public static final String IAM_PAYLOAD_TYPE = "in_app_messages";

    // JSON keys
    private static final String MESSAGES_JSON_KEY = "in_app_messages";
    private static final String CREATED_JSON_KEY = "created";
    private static final String UPDATED_JSON_KEY = "last_updated";

    // Data store keys
    private static final String LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP_";
    private static final String SCHEDULED_MESSAGES_KEY = "com.urbanairship.iam.data.SCHEDULED_MESSAGES";

    private final PreferenceDataStore preferenceDataStore;
    private Callback callback;

    /**
     * Observer callbacks.
     */
    interface Callback {

        /**
         * New schedules that need to be scheduled.
         *
         * @param scheduleInfos The list of schedule infos.
         */
        void onSchedule(List<InAppMessageScheduleInfo> scheduleInfos);

        /**
         * Message Ids that need to be cancelled.
         *
         * @param messageIds The list of message IDs.
         */
        void onCancel(List<String> messageIds);
    }

    /**
     * Default constructor.
     *
     * @param preferenceDataStore The preference data store.
     * @param callback The observer callback.
     */
    InAppRemoteDataObserver(@NonNull PreferenceDataStore preferenceDataStore, @NonNull Callback callback) {
        this.preferenceDataStore = preferenceDataStore;
        this.callback = callback;
    }

    @Override
    public void onNext(RemoteDataPayload payload) {
        long lastUpdate = preferenceDataStore.getLong(LAST_PAYLOAD_TIMESTAMP_KEY, -1);
        if (payload.getTimestamp() == lastUpdate) {
            return;
        }

        preferenceDataStore.put(LAST_PAYLOAD_TIMESTAMP_KEY, payload.getTimestamp());
        JsonList messages = payload.getData().opt(MESSAGES_JSON_KEY).optList();

        List<String> messageIds = new ArrayList<>();
        List<InAppMessageScheduleInfo> newSchedules = new ArrayList<>();

        for (JsonValue messageJson : messages) {
            long createdTimeStamp, lastUpdatedTimeStamp;

            try {
                createdTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(CREATED_JSON_KEY).getString());
                lastUpdatedTimeStamp = DateUtils.parseIso8601(messageJson.optMap().opt(UPDATED_JSON_KEY).getString());
            } catch (ParseException e) {
                Logger.error("Failed to parse in-app message timestamps: " + messageJson, e);
                continue;
            }

            String messageId = InAppMessageScheduleInfo.parseMessageId(messageJson);
            if (UAStringUtil.isEmpty(messageId)) {
                Logger.error("Missing in-app message ID: " + messageJson);
                continue;
            }

            messageIds.add(messageId);

            if (createdTimeStamp > lastUpdate) {
                try {
                    InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.fromJson(messageJson);
                    newSchedules.add(scheduleInfo);
                } catch (JsonException e) {
                    Logger.error("Failed to parse in-app message: " + messageJson, e);
                }
            } else if (lastUpdatedTimeStamp > lastUpdate) {
                // TODO: updates
            }
        }

        List<String> deletedMessageIds = getScheduledMessageIds();
        deletedMessageIds.removeAll(messageIds);

        if (!deletedMessageIds.isEmpty()) {
            callback.onCancel(deletedMessageIds);
        }

        if (!newSchedules.isEmpty()) {
            callback.onSchedule(newSchedules);
        }

        setScheduledMessageIds(messageIds);
    }

    @Override
    public void onCompleted() {}

    @Override
    public void onError(Exception e) {}

    /**
     * Gets the stored scheduled message IDs.
     *
     * @return The scheduled message IDs.
     */
    private List<String> getScheduledMessageIds() {
        JsonList jsonList = preferenceDataStore.getJsonValue(SCHEDULED_MESSAGES_KEY).optList();

        List<String> messageIds = new ArrayList<>();
        for (JsonValue value : jsonList) {
            if (value.isString()) {
                messageIds.add(value.getString());
            }
        }

        return messageIds;
    }

    /**
     * Sets the scheduled message IDs.
     *
     * @param messageIds The list of message IDs.
     */
    private void setScheduledMessageIds(List<String> messageIds) {
        preferenceDataStore.put(SCHEDULED_MESSAGES_KEY, JsonValue.wrapOpt(messageIds));
    }

}
