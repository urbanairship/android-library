/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Subscriber for {@link com.urbanairship.remotedata.RemoteData}.
 */
class InAppRemoteDataObserver {

    private static final String IAM_PAYLOAD_TYPE = "in_app_messages";

    // JSON keys
    private static final String MESSAGES_JSON_KEY = "in_app_messages";
    private static final String CREATED_JSON_KEY = "created";
    private static final String UPDATED_JSON_KEY = "last_updated";

    // Data store keys
    private static final String LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP";
    private static final String SCHEDULED_MESSAGES_KEY = "com.urbanairship.iam.data.SCHEDULED_MESSAGES";
    private static final String SCHEDULE_NEW_USER_CUTOFF_TIME_KEY = "com.urbanairship.iam.data.NEW_USER_TIME";

    private final PreferenceDataStore preferenceDataStore;
    private Subscription subscription;

    /**
     * Default constructor.
     *
     * @param preferenceDataStore The preference data store.
     */
    InAppRemoteDataObserver(@NonNull PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;
    }

    /**
     * Subscribes to remote data.
     *
     * @param remoteData The remote data.
     * @param scheduler Scheduler.
     */
    void subscribe(final RemoteData remoteData, final InAppMessageScheduler scheduler) {
        cancel();

        this.subscription = remoteData.payloadsForType(IAM_PAYLOAD_TYPE)
                                      .filter(new Predicate<RemoteDataPayload>() {
                                          @Override
                                          public boolean apply(RemoteDataPayload payload) {
                                              return payload.getTimestamp() != preferenceDataStore.getLong(LAST_PAYLOAD_TIMESTAMP_KEY, -1);
                                          }
                                      })
                                      .subscribe(new Subscriber<RemoteDataPayload>() {
                                          @Override
                                          public void onNext(RemoteDataPayload payload) {
                                              try {
                                                  processPayload(payload, scheduler);
                                              } catch (ExecutionException e) {
                                                  e.printStackTrace();
                                              } catch (InterruptedException e) {
                                                  e.printStackTrace();
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
     * @param payload The remote data payload.
     * @param scheduler The scheduler.
     */
    @WorkerThread
    private void processPayload(RemoteDataPayload payload, InAppMessageScheduler scheduler) throws ExecutionException, InterruptedException {
        long lastUpdate = preferenceDataStore.getLong(LAST_PAYLOAD_TIMESTAMP_KEY, -1);

        List<String> messageIds = new ArrayList<>();

        List<InAppMessageScheduleInfo> newSchedules = new ArrayList<>();

        for (JsonValue messageJson : payload.getData().opt(MESSAGES_JSON_KEY).optList()) {
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
                if (!scheduler.getMessages(messageId).get().isEmpty()) {
                    continue;
                }

                try {
                    InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.fromJson(messageJson);
                    if (checkSchedule(scheduleInfo, createdTimeStamp)) {
                        newSchedules.add(scheduleInfo);
                    }
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
            scheduler.cancelMessages(deletedMessageIds).get();
        }

        if (!newSchedules.isEmpty()) {
            scheduler.schedule(newSchedules).get();
        }

        setScheduledMessageIds(messageIds);
        preferenceDataStore.put(LAST_PAYLOAD_TIMESTAMP_KEY, payload.getTimestamp());
    }

    private boolean checkSchedule(InAppMessageScheduleInfo scheduleInfo, long createdTimeStamp) {
        Context context = UAirship.getApplicationContext();
        Audience audience = scheduleInfo.getInAppMessage().getAudience();
        boolean allowNewUser = createdTimeStamp <= getScheduleNewUserCutOffTime();
        return AudienceChecks.checkAudience(context, audience, allowNewUser);
    }

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
}
