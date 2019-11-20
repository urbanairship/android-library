/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.TestApplication;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import edu.emory.mathcs.backport.java.util.Collections;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InAppRemoteDataObserver} tests.
 */
public class InAppRemoteDataObserverTest extends BaseTestCase {

    private InAppRemoteDataObserver observer;
    private TestScheduler scheduler;
    private RemoteData remoteData;
    private Subject<RemoteDataPayload> updates;

    @Before
    public void setup() {
        remoteData = mock(RemoteData.class);
        updates = Subject.create();
        when(remoteData.payloadsForType(anyString())).thenReturn(updates);

        scheduler = new TestScheduler();

        observer = new InAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore);
        observer.subscribe(remoteData, Looper.getMainLooper(), scheduler);
    }

    @Test
    public void testSchedule() {
        JsonMap metadata = JsonMap.newBuilder().putOpt("meta", "data").build();

        // Create a payload with foo and bar.
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addScheduleInfo("bar", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata)
                .build();

        // Notify the observer
        updates.onNext(payload);

        // Verify "foo" and "bar" are scheduled
        assertTrue(scheduler.isMessageScheduled("foo", metadata));
        assertTrue(scheduler.isMessageScheduled("bar", metadata));

        // Create another payload with added baz
        payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addScheduleInfo("bar", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addScheduleInfo("baz", TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(2))
                .setTimeStamp(TimeUnit.DAYS.toMillis(2))
                .setMetadata(metadata)
                .build();

        // Notify the observer
        updates.onNext(payload);

        // Verify "baz" is scheduled
        assertTrue(scheduler.isMessageScheduled("baz", metadata));

        // Verify "foo" and "bar" are still scheduled
        assertTrue(scheduler.isMessageScheduled("foo", metadata));
        assertTrue(scheduler.isMessageScheduled("bar", metadata));
    }

    @Test
    public void testEndMessages() {
        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", 100, 100)
                .addScheduleInfo("bar", 100, 100)
                .build();

        updates.onNext(payload);

        // Verify "foo" and "bar" are scheduled
        assertTrue(scheduler.isMessageScheduled("foo"));
        assertTrue(scheduler.isMessageScheduled("bar"));

        // Update the message without bar
        payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", 100, 100)
                .build();


        updates.onNext(payload);

        // Verify "foo" and "bar" are still scheduled
        assertTrue(scheduler.isMessageScheduled("foo"));
        assertTrue(scheduler.isMessageScheduled("bar"));

        // Verify "bar" was edited with updated end and start time
        InAppMessageScheduleEdits edits = scheduler.getMessageEdits("bar");
        assertEquals(Long.valueOf(payload.getTimestamp()), edits.getEnd());
        assertEquals(Long.valueOf(payload.getTimestamp()), edits.getStart());
    }

    @Test
    public void testEdit() {
        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .build();

        // Process payload
        updates.onNext(payload);

        // Verify "foo" is scheduled
        assertTrue(scheduler.isMessageScheduled("foo"));

        // Update the message with newer foo
        final InAppMessage message = InAppMessage.newBuilder()
                                                 .setDisplayContent(new CustomDisplayContent(JsonValue.wrapOpt("COOL")))
                                                 .setId("foo")
                                                 .build();

        payload = new TestPayloadBuilder()
                .addScheduleInfo(message, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(2))
                .setTimeStamp(TimeUnit.DAYS.toMillis(2))
                .build();

        // Return pending result for the edit
        updates.onNext(payload);

        // Verify "foo" was edited with the updated message
        InAppMessageScheduleEdits edits = scheduler.getMessageEdits("foo");
        assertEquals(message, edits.getMessage());
    }

    @Test
    public void testMetadataChange() {
        final InAppMessage message = InAppMessage.newBuilder()
                                                 .setDisplayContent(new CustomDisplayContent(JsonValue.wrapOpt("COOL")))
                                                 .setId("foo")
                                                 .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                                 .build();

        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo(message, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(JsonMap.newBuilder().putOpt("cool", "story").build())
                .build();

        // Process payload
        updates.onNext(payload);

        JsonMap updatedMetadata = JsonMap.newBuilder().putOpt("fun", "fun").build();

        // Update the metadata
        payload = new TestPayloadBuilder()
                .addScheduleInfo(message, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(updatedMetadata)
                .build();

        updates.onNext(payload);

        // Verify "foo" was edited with the updated message
        InAppMessageScheduleEdits edits = scheduler.getMessageEdits("foo");
        assertEquals(updatedMetadata, edits.getMetadata());
    }

    @Test
    public void testDefaultNewUserCutoffTime() {
        assertEquals(-1, observer.getScheduleNewUserCutOffTime());
    }


    /**
     * Helper class to generate a in-app message remote data payload.
     */
    private static class TestPayloadBuilder {

        List<JsonValue> schedules = new ArrayList<>();
        long timeStamp = System.currentTimeMillis();
        JsonMap metadata = JsonMap.EMPTY_MAP;

        public TestPayloadBuilder setTimeStamp(long timeStamp) {
            this.timeStamp = timeStamp;
            return this;
        }

        public TestPayloadBuilder addScheduleInfo(InAppMessage message, long created, long updated) {
            List<JsonMap> triggersJson = new ArrayList<>();
            triggersJson.add(JsonMap.newBuilder()
                                    .put("type", "foreground")
                                    .put("goal", 20.0)
                                    .build());

            JsonMap scheduleJson = JsonMap.newBuilder()
                                          .put("created", DateUtils.createIso8601TimeStamp(created))
                                          .put("last_updated", DateUtils.createIso8601TimeStamp(updated))
                                          .put("message", message)
                                          .put("triggers", JsonValue.wrapOpt(triggersJson))
                                          .put("limit", 10)
                                          .put("priority", 1)
                                          .put("start", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L)))
                                          .put("end", JsonValue.wrap(DateUtils.createIso8601TimeStamp(15000L)))
                                          .build();

            schedules.add(scheduleJson.toJsonValue());
            return this;
        }

        public TestPayloadBuilder setMetadata(@NonNull JsonMap metadata) {
            this.metadata = metadata;
            return this;
        }

        public TestPayloadBuilder addScheduleInfo(String messageId, long created, long updated) {
            InAppMessage message = createMessage(messageId);
            return addScheduleInfo(message, created, updated);
        }

        public RemoteDataPayload build() {
            JsonMap data = JsonMap.newBuilder().putOpt("in_app_messages", JsonValue.wrapOpt(schedules)).build();

            return RemoteDataPayload.newBuilder()
                                    .setType("in_app_messages")
                                    .setTimeStamp(timeStamp)
                                    .setMetadata(metadata)
                                    .setData(data)
                                    .build();
        }
    }

    private static InAppMessage createMessage(String messageId) {
        return InAppMessage.newBuilder()
                           .setId(messageId)
                           .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                           .build();

    }

    private static class TestScheduler implements InAppMessageScheduler {

        private final Map<String, InAppMessageSchedule> schedules = new HashMap<>();
        private final Map<String, InAppMessageScheduleEdits> scheduleEdits = new HashMap<>();
        private final Map<String, String> messageIdToScheduleIdMap = new HashMap<>();


        @NonNull
        @Override
        public PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo) {
            return scheduleMessage(messageScheduleInfo, JsonMap.EMPTY_MAP);
        }

        @NonNull
        @Override
        public PendingResult<InAppMessageSchedule> scheduleMessage(@NonNull InAppMessageScheduleInfo messageScheduleInfo, @NonNull JsonMap metadata) {
            InAppMessageSchedule schedule = new InAppMessageSchedule(UUID.randomUUID().toString(), metadata, messageScheduleInfo);
            schedules.put(schedule.getId(), schedule);
            messageIdToScheduleIdMap.put(messageScheduleInfo.getInAppMessage().getId(), schedule.getId());

            PendingResult<InAppMessageSchedule> scheduleResult = new PendingResult<>();
            scheduleResult.setResult(schedule);
            return scheduleResult;
        }

        @NonNull
        @Override
        public PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos) {
            return schedule(scheduleInfos, JsonMap.EMPTY_MAP);
        }

        @NonNull
        @Override
        public PendingResult<List<InAppMessageSchedule>> schedule(@NonNull List<InAppMessageScheduleInfo> scheduleInfos, @NonNull JsonMap metadata) {
            List<InAppMessageSchedule> result = new ArrayList<>();
            for (InAppMessageScheduleInfo info : scheduleInfos) {
                InAppMessageSchedule schedule = new InAppMessageSchedule(UUID.randomUUID().toString(), metadata, info);
                result.add(schedule);

                schedules.put(schedule.getId(), schedule);
                messageIdToScheduleIdMap.put(info.getInAppMessage().getId(), schedule.getId());
            }


            PendingResult<List<InAppMessageSchedule>> scheduleResult = new PendingResult<>();
            scheduleResult.setResult(result);
            return scheduleResult;
        }

        @NonNull
        @Override
        public PendingResult<Void> cancelSchedule(@NonNull String scheduleId) {
            PendingResult<Void> cancelResult = new PendingResult<>();
            cancelResult.setResult(null);

            InAppMessageSchedule schedule = this.schedules.remove(scheduleId);
            if (schedule != null) {
                messageIdToScheduleIdMap.remove(schedule.getInfo().getInAppMessage().getId());
            }

            return cancelResult;
        }

        @NonNull
        @Override
        public PendingResult<Boolean> cancelMessage(@NonNull String messageId) {
            PendingResult<Boolean> cancelResult = new PendingResult<>();

            if (messageIdToScheduleIdMap.containsKey(messageId)) {
                this.schedules.remove(messageIdToScheduleIdMap.remove(messageId));
                cancelResult.setResult(true);
            } else {
                cancelResult.setResult(false);
            }

            return cancelResult;
        }

        @NonNull
        @Override
        public PendingResult<Void> cancelMessages(@NonNull Collection<String> messageIds) {
            PendingResult<Void> cancelResult = new PendingResult<>();
            cancelResult.setResult(null);
            for (String id : messageIds) {
                cancelMessage(id);
            }

            return cancelResult;
        }

        @NonNull
        @Override
        public PendingResult<Collection<InAppMessageSchedule>> getSchedules(@NonNull String messageId) {
            PendingResult<Collection<InAppMessageSchedule>> result = new PendingResult<>();

            if (messageIdToScheduleIdMap.containsKey(messageId)) {
                result.setResult(java.util.Collections.singleton(schedules.get(messageIdToScheduleIdMap.get(messageId))));
            } else {
                result.setResult(null);
            }

            return result;
        }

        @NonNull
        @Override
        public PendingResult<InAppMessageSchedule> getSchedule(@NonNull String scheduleId) {
            PendingResult<InAppMessageSchedule> result = new PendingResult<>();
            result.setResult(schedules.get(scheduleId));
            return result;
        }

        @NonNull
        @Override
        public PendingResult<Collection<InAppMessageSchedule>> getSchedules() {
            PendingResult<Collection<InAppMessageSchedule>> result = new PendingResult<>();
            result.setResult(schedules.values());
            return result;
        }

        @NonNull
        @Override
        public PendingResult<InAppMessageSchedule> editSchedule(@NonNull String scheduleId, @NonNull InAppMessageScheduleEdits edits) {
            PendingResult<InAppMessageSchedule> result = new PendingResult<>();
            InAppMessageSchedule schedule = schedules.get(scheduleId);
            result.setResult(schedule);

            if (schedule != null) {
                scheduleEdits.put(scheduleId, edits);
            }

            return result;
        }


        public boolean isMessageScheduled(@NonNull String messageId) {
            return isMessageScheduled(messageId, null);
        }

        public boolean isMessageScheduled(@NonNull String messageId, @Nullable JsonMap metadata) {
            String scheduleId = messageIdToScheduleIdMap.get(messageId);
            if (scheduleId == null) {
                return false;
            }

            InAppMessageSchedule schedule = schedules.get(scheduleId);
            if (schedule == null) {
                return false;
            }

            if (metadata != null) {
                return schedule.getMetadata().equals(metadata);
            } else {
                return true;
            }
        }

        public InAppMessageScheduleEdits getMessageEdits(@NonNull String messageId) {
            String scheduleId = messageIdToScheduleIdMap.get(messageId);
            if (scheduleId == null) {
                return null;
            }

            return scheduleEdits.get(scheduleId);
        }


    }

}