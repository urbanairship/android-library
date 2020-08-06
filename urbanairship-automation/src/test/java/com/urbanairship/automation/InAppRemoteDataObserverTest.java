/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Looper;

import com.urbanairship.PendingResult;
import com.urbanairship.TestApplication;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.iam.InAppAutomationScheduler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link InAppRemoteDataObserver} tests.
 */
@RunWith(AndroidJUnit4.class)
public class InAppRemoteDataObserverTest {

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

        observer = new InAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore, remoteData);
        observer.subscribe(Looper.getMainLooper(), scheduler);
    }

    @Test
    public void testSchedule() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();
        JsonMap expectedMetadata = JsonMap.newBuilder()
                                  .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", metadata)
                                  .build();
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
        assertTrue(scheduler.isScheduled("foo", expectedMetadata));
        assertTrue(scheduler.isScheduled("bar", expectedMetadata));

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
        assertTrue(scheduler.isScheduled("baz", expectedMetadata));

        // Verify "foo" and "bar" are still scheduled
        assertTrue(scheduler.isScheduled("foo", expectedMetadata));
        assertTrue(scheduler.isScheduled("bar", expectedMetadata));
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
        assertTrue(scheduler.isScheduled("foo"));
        assertTrue(scheduler.isScheduled("bar"));

        // Update the message without bar
        payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", 100, 100)
                .build();

        updates.onNext(payload);

        // Verify "foo" and "bar" are still scheduled
        assertTrue(scheduler.isScheduled("foo"));
        assertTrue(scheduler.isScheduled("bar"));

        // Verify "bar" was edited with updated end and start time
        ScheduleEdits<? extends ScheduleData> edits = scheduler.getScheduleEdits("bar");
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
        assertTrue(scheduler.isScheduled("foo"));

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
        ScheduleEdits<? extends ScheduleData> edits = scheduler.getScheduleEdits("foo");
        assertEquals(message, edits.getData());
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
        ScheduleEdits<? extends ScheduleData> edits = scheduler.getScheduleEdits("foo");
        JsonMap expected = JsonMap.newBuilder()
                                  .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", updatedMetadata)
                                  .build();
        assertEquals(expected, edits.getMetadata());
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

    private static class TestScheduler implements InAppAutomationScheduler {

        private final Map<String, Schedule<? extends ScheduleData>> schedules = new HashMap<>();
        private final Map<String, ScheduleEdits<? extends ScheduleData>> scheduleEdits = new HashMap<>();

        @NonNull
        @Override
        public PendingResult<Boolean> schedule(@NonNull Schedule<? extends ScheduleData> schedule) {
            schedules.put(schedule.getId(), schedule);

            PendingResult<Boolean> scheduleResult = new PendingResult<>();
            scheduleResult.setResult(true);
            return scheduleResult;
        }

        @NonNull
        @Override
        public PendingResult<Boolean> cancelSchedule(@NonNull String scheduleId) {
            PendingResult<Boolean> cancelResult = new PendingResult<>();
            Schedule<? extends ScheduleData> schedule = this.schedules.remove(scheduleId);
            cancelResult.setResult(schedule != null);
            return cancelResult;
        }

        @NonNull
        @Override
        public PendingResult<Boolean> cancelScheduleGroup(@NonNull String group) {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Collection<Schedule<Actions>>> getActionScheduleGroup(@NonNull String group) {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Schedule<Actions>> getActionSchedule(@NonNull String scheduleId) {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Collection<Schedule<Actions>>> getActionSchedules() {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Collection<Schedule<InAppMessage>>> getMessageScheduleGroup(@NonNull String group) {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Schedule<InAppMessage>> getMessageSchedule(@NonNull String scheduleId) {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Collection<Schedule<InAppMessage>>> getMessageSchedules() {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Boolean> schedule(@NonNull List<Schedule<? extends ScheduleData>> schedules) {
            for (Schedule<? extends ScheduleData> schedule : schedules) {
                this.schedules.put(schedule.getId(), schedule);
            }

            PendingResult<Boolean> scheduleResult = new PendingResult<>();
            scheduleResult.setResult(true);
            return scheduleResult;
        }


        @NonNull
        public PendingResult<Schedule<? extends ScheduleData>> getSchedule(@NonNull String scheduleId) {
            throw new IllegalArgumentException();
        }

        @NonNull
        @Override
        public PendingResult<Collection<Schedule<? extends ScheduleData>>> getSchedules() {
            PendingResult<Collection<Schedule<? extends ScheduleData>>> pendingResult = new PendingResult<>();
            pendingResult.setResult(schedules.values());
            return pendingResult;
        }

        @NonNull
        @Override
        public PendingResult<Boolean> editSchedule(@NonNull String scheduleId, @NonNull ScheduleEdits<? extends ScheduleData> edits) {
            PendingResult<Boolean> result = new PendingResult<>();
            Schedule<?> schedule = schedules.get(scheduleId);

            if (schedule != null) {
                scheduleEdits.put(scheduleId, edits);
                result.setResult(true);
            } else {
                result.setResult(false);
            }

            return result;
        }

        public boolean isScheduled(@NonNull String scheduleId) {
            return schedules.containsKey(scheduleId);
        }

        public boolean isScheduled(@NonNull String scheduleId, JsonMap metadata) {
            Schedule<?> schedule = schedules.get(scheduleId);
            if (schedule != null) {
                return schedule.getMetadata().equals(metadata);
            } else {
                return false;
            }
        }

        public ScheduleEdits<? extends ScheduleData> getScheduleEdits(@NonNull String scheduleId) {
            return scheduleEdits.get(scheduleId);
        }

    }

}
