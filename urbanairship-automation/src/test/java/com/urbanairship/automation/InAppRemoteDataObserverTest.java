/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.net.Uri;
import android.os.Looper;

import com.urbanairship.PendingResult;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestApplication;
import com.urbanairship.automation.actions.Actions;
import com.urbanairship.automation.deferred.Deferred;
import com.urbanairship.automation.limits.FrequencyConstraint;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subject;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link InAppRemoteDataObserver} tests.
 */
@Config(
        sdk = 28,
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class InAppRemoteDataObserverTest {

    private InAppRemoteDataObserver observer;
    private TestDelegate delegate;
    private RemoteData remoteData;
    private Subject<RemoteDataPayload> updates;
    private Subscription subscription;

    @Before
    public void setup() {
        remoteData = mock(RemoteData.class);
        updates = Subject.create();
        when(remoteData.payloadsForType(anyString())).thenReturn(updates);

        delegate = new TestDelegate();

        observer = new InAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore, remoteData, "1.0.0", Looper.getMainLooper());
        subscription = observer.subscribe(delegate);
    }

    @Test
    public void testSchedule() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        JsonMap expectedMetadata = JsonMap.newBuilder()
                                          .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", metadata)
                                          .build();

        List<String> constraintIds = new ArrayList<>();
        constraintIds.add("foo");
        constraintIds.add("bar");

        JsonValue campaigns = JsonMap.newBuilder()
                                     .put("neat", "campaign")
                                     .build()
                                     .toJsonValue();

        Schedule<InAppMessage> fooSchedule = Schedule.newBuilder(InAppMessage.newBuilder()
                                                                             .setName("foo")
                                                                             .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                             .build())
                                                     .addTrigger(Triggers.newAppInitTriggerBuilder()
                                                                         .setGoal(1)
                                                                         .build())
                                                     .setMetadata(expectedMetadata)
                                                     .setStart(1000)
                                                     .setEnd(3000)
                                                     .setInterval(10, TimeUnit.SECONDS)
                                                     .setAudience(Audience.newBuilder()
                                                                          .setLocationOptIn(true)
                                                                          .build())
                                                     .setDelay(ScheduleDelay.newBuilder()
                                                                            .setSeconds(100)
                                                                            .build())
                                                     .setCampaigns(campaigns)
                                                     .setFrequencyConstraintIds(constraintIds)
                                                     .setId("foo")
                                                     .build();

        Schedule<Deferred> barSchedule = Schedule.newBuilder(new Deferred(Uri.parse("https://neat"), false))
                                                 .addTrigger(Triggers.newActiveSessionTriggerBuilder()
                                                                     .setGoal(1)
                                                                     .build())
                                                 .setId("bar")
                                                 .setMetadata(expectedMetadata)
                                                 .build();

        Schedule<Actions> bazSchedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                                .addTrigger(Triggers.newActiveSessionTriggerBuilder()
                                                                    .setGoal(1)
                                                                    .build())
                                                .setId("baz")
                                                .setMetadata(expectedMetadata)
                                                .build();

        // Create a payload with foo and bar.
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addSchedule(barSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata)
                .build();

        // Notify the observer
        updates.onNext(payload);

        // Verify "foo" and "bar" are scheduled
        assertEquals(fooSchedule, delegate.schedules.get("foo"));
        assertEquals(barSchedule, delegate.schedules.get("bar"));

        // Create another payload with added baz
        payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addSchedule(barSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addSchedule(bazSchedule, TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(2))
                .setTimeStamp(TimeUnit.DAYS.toMillis(2))
                .setMetadata(metadata)
                .build();

        // Notify the observer
        updates.onNext(payload);

        // Verify "baz" is scheduled
        assertEquals(bazSchedule, delegate.schedules.get("baz"));

        // Verify "foo" and "bar" are still scheduled
        assertEquals(fooSchedule, delegate.schedules.get("foo"));
        assertEquals(barSchedule, delegate.schedules.get("bar"));
    }

    @Test
    public void testMinSdkVersion() {
        subscription.cancel();
        observer = new InAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore, remoteData, "1.0.0", Looper.getMainLooper());
        subscription = observer.subscribe(delegate);

        // Create an empty payload
        RemoteDataPayload payload = new TestPayloadBuilder()
                .setTimeStamp(TimeUnit.DAYS.toMillis(100))
                .setMetadata(JsonMap.newBuilder().put("so", "meta").build())
                .build();

        // Notify the observer
        updates.onNext(payload);
        subscription.cancel();

        observer = new InAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore, remoteData, "2.0.0", Looper.getMainLooper());
        subscription = observer.subscribe(delegate);

        JsonMap expectedMetadata = JsonMap.newBuilder()
                                          .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", JsonMap.newBuilder().put("so", "so meta").build())
                                          .build();

        Schedule<InAppMessage> fooSchedule = Schedule.newBuilder(InAppMessage.newBuilder()
                                                                             .setName("foo")
                                                                             .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                             .build())
                                                     .addTrigger(Triggers.newAppInitTriggerBuilder()
                                                                         .setGoal(1)
                                                                         .build())
                                                     .setMetadata(expectedMetadata)
                                                     .setId("foo")
                                                     .build();

        // Create another payload with foo
        payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, TimeUnit.DAYS.toMillis(99), TimeUnit.DAYS.toMillis(99), "2.0.0")
                .setTimeStamp(TimeUnit.DAYS.toMillis(100))
                .setMetadata(JsonMap.newBuilder().put("so", "so meta").build())
                .build();

        // Notify the observer
        updates.onNext(payload);

        // Verify "foo" is scheduled
        assertEquals(fooSchedule, delegate.schedules.get("foo"));
    }

    @Test
    public void testLegacy() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        JsonMap expectedMetadata = JsonMap.newBuilder()
                                          .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", metadata)
                                          .build();

        Schedule<InAppMessage> legacySchedule = Schedule.newBuilder(InAppMessage.newBuilder()
                                                                                .setName("foo")
                                                                                .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                                .build())
                                                        .addTrigger(Triggers.newAppInitTriggerBuilder()
                                                                            .setGoal(1)
                                                                            .build())
                                                        .setMetadata(expectedMetadata)
                                                        .setStart(1000)
                                                        .setEnd(3000)
                                                        .setInterval(10, TimeUnit.SECONDS)
                                                        .setAudience(Audience.newBuilder()
                                                                             .setLocationOptIn(true)
                                                                             .build())
                                                        .setDelay(ScheduleDelay.newBuilder()
                                                                               .setSeconds(100)
                                                                               .build())
                                                        .setId("legacy")
                                                        .build();

        RemoteDataPayload payload = new TestPayloadBuilder()
                .addLegacySchedule(legacySchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata)
                .build();

        // Notify the observer
        updates.onNext(payload);

        // Verify "foo" and "bar" are scheduled
        assertEquals(legacySchedule, delegate.schedules.get("legacy"));
    }

    @Test
    public void testEndMessages() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        JsonMap expectedMetadata = JsonMap.newBuilder()
                                          .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", metadata)
                                          .build();

        Schedule<InAppMessage> fooSchedule = Schedule.newBuilder(InAppMessage.newBuilder()
                                                                             .setName("foo")
                                                                             .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                             .build())
                                                     .addTrigger(Triggers.newAppInitTriggerBuilder()
                                                                         .setGoal(1)
                                                                         .build())
                                                     .setId("foo")
                                                     .setMetadata(expectedMetadata)
                                                     .build();

        Schedule<Deferred> barSchedule = Schedule.newBuilder(new Deferred(Uri.parse("https://neat"), false))
                                                 .addTrigger(Triggers.newActiveSessionTriggerBuilder()
                                                                     .setGoal(1)
                                                                     .build())
                                                 .setId("bar")
                                                 .setMetadata(expectedMetadata)
                                                 .build();

        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, 100, 100)
                .addSchedule(barSchedule, 100, 100)
                .setMetadata(metadata)
                .build();

        updates.onNext(payload);

        // Verify "foo" and "bar" are scheduled
        assertEquals(fooSchedule, delegate.schedules.get("foo"));
        assertEquals(barSchedule, delegate.schedules.get("bar"));

        // Update the message without bar
        payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, 100, 100)
                .setMetadata(metadata)
                .build();

        updates.onNext(payload);

        // Verify "foo" and "bar" are still scheduled
        assertEquals(fooSchedule, delegate.schedules.get("foo"));
        assertEquals(barSchedule, delegate.schedules.get("bar"));

        // Verify "bar" was edited with updated end and start time
        ScheduleEdits<? extends ScheduleData> edits = delegate.getScheduleEdits("bar");
        assertEquals(Long.valueOf(payload.getTimestamp()), edits.getEnd());
        assertEquals(Long.valueOf(payload.getTimestamp()), edits.getStart());
    }

    @Test
    public void testEdit() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        JsonMap expectedMetadata = JsonMap.newBuilder()
                                          .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", metadata)
                                          .build();

        Schedule<InAppMessage> fooSchedule = Schedule.newBuilder(InAppMessage.newBuilder()
                                                                             .setName("foo")
                                                                             .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                             .build())
                                                     .addTrigger(Triggers.newAppInitTriggerBuilder()
                                                                         .setGoal(1)
                                                                         .build())
                                                     .setId("foo")
                                                     .setMetadata(expectedMetadata)
                                                     .build();

        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata)
                .build();

        // Process payload
        updates.onNext(payload);

        // Verify "foo" is scheduled
        assertEquals(fooSchedule, delegate.schedules.get("foo"));

        List<String> constraintIds = new ArrayList<>();
        constraintIds.add("foo");
        constraintIds.add("bar");

        JsonValue campaigns = JsonMap.newBuilder()
                                     .put("neat", "campaign")
                                     .build()
                                     .toJsonValue();

        // Update "foo" as a different type
        Schedule<Actions> newFooSchedule = Schedule.newBuilder(new Actions(JsonMap.EMPTY_MAP))
                                                   .addTrigger(Triggers.newAppInitTriggerBuilder()
                                                                       .setGoal(1)
                                                                       .build())
                                                   .setId("foo")
                                                   .setMetadata(expectedMetadata)
                                                   .setCampaigns(campaigns)
                                                   .setFrequencyConstraintIds(constraintIds)
                                                   .build();

        payload = new TestPayloadBuilder()
                .addSchedule(newFooSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(2))
                .setTimeStamp(TimeUnit.DAYS.toMillis(2))
                .setMetadata(metadata)
                .build();

        // Return pending result for the edit
        updates.onNext(payload);

        // Verify "foo" was edited with the updated message
        ScheduleEdits<? extends ScheduleData> edits = delegate.scheduleEdits.get("foo");
        assertEquals(Schedule.TYPE_ACTION, edits.getType());
        assertEquals(constraintIds, edits.getFrequencyConstraintIds());
        assertEquals(campaigns, edits.getCampaigns());
    }

    @Test
    public void testMetadataChange() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        JsonMap expectedMetadata = JsonMap.newBuilder()
                                          .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", metadata)
                                          .build();

        Schedule<InAppMessage> fooSchedule = Schedule.newBuilder(InAppMessage.newBuilder()
                                                                             .setName("foo")
                                                                             .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                                                             .build())
                                                     .addTrigger(Triggers.newAppInitTriggerBuilder()
                                                                         .setGoal(1)
                                                                         .build())
                                                     .setId("foo")
                                                     .setMetadata(expectedMetadata)
                                                     .build();

        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata)
                .build();

        // Process payload
        updates.onNext(payload);

        JsonMap updatedMetadata = JsonMap.newBuilder().putOpt("fun", "fun").build();

        // Update the metadata
        payload = new TestPayloadBuilder()
                .addSchedule(fooSchedule, TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(updatedMetadata)
                .build();

        updates.onNext(payload);

        // Verify "foo" was edited with the updated message
        ScheduleEdits<? extends ScheduleData> edits = delegate.getScheduleEdits("foo");
        JsonMap expected = JsonMap.newBuilder()
                                  .put("com.urbanairship.iaa.REMOTE_DATA_METADATA", updatedMetadata)
                                  .build();
        assertEquals(expected, edits.getMetadata());
    }

    @Test
    public void testDefaultNewUserCutoffTime() {
        assertEquals(-1, observer.getScheduleNewUserCutOffTime());
    }

    @Test
    public void testEmptyConstraints() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        RemoteDataPayload payload = new TestPayloadBuilder()
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata)
                .build();

        updates.onNext(payload);

        assertTrue(delegate.constraintUpdates.get(0).isEmpty());
        assertEquals(1, delegate.constraintUpdates.size());
    }

    @Test
    public void testConstraints() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        Map<String, Long> rangeMap = new HashMap<>();
        rangeMap.put("seconds", TimeUnit.SECONDS.toMillis(1));
        rangeMap.put("minutes", TimeUnit.MINUTES.toMillis(1));
        rangeMap.put("hours", TimeUnit.HOURS.toMillis(1));
        rangeMap.put("days", TimeUnit.DAYS.toMillis(1));
        rangeMap.put("weeks", 7 * TimeUnit.DAYS.toMillis(1));
        rangeMap.put("months", 30 * TimeUnit.DAYS.toMillis(1));
        rangeMap.put("years", 365 * TimeUnit.DAYS.toMillis(1));

        TestPayloadBuilder builder = new TestPayloadBuilder()
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata);

        List<FrequencyConstraint> expected = new ArrayList<>();

        for (Map.Entry<String, Long> value : rangeMap.entrySet()) {
            builder.addConstraint(JsonMap.newBuilder()
                                         .put("id", value.getKey() + " id")
                                         .put("range", 1)
                                         .put("boundary", 10)
                                         .put("period", value.getKey())
                                         .build());

            expected.add(FrequencyConstraint.newBuilder()
                                            .setCount(10)
                                            .setId(value.getKey() + " id")
                                            .setRange(TimeUnit.MILLISECONDS, value.getValue())
                                            .build());
        }

        updates.onNext(builder.build());

        assertEquals(expected, delegate.constraintUpdates.get(0));
        assertEquals(1, delegate.constraintUpdates.size());
    }

    @Test
    public void testInvalidConstraint() {
        JsonMap metadata = JsonMap.newBuilder()
                                  .putOpt("meta", "data").build();

        RemoteDataPayload payload = new TestPayloadBuilder()
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .setMetadata(metadata)
                .addConstraint(JsonMap.newBuilder()
                                      .put("id", "invalid")
                                      .put("range", 1)
                                      .put("boundary", 10)
                                      .put("period", "lunar cycles")
                                      .build())
                .addConstraint(JsonMap.newBuilder()
                                      .put("id", "valid")
                                      .put("range", 20)
                                      .put("boundary", 10)
                                      .put("period", "days")
                                      .build())
                .build();

        updates.onNext(payload);

        List<FrequencyConstraint> expected = new ArrayList<>();
        expected.add(FrequencyConstraint.newBuilder().setRange(TimeUnit.DAYS, 20)
                                        .setId("valid")
                                        .setCount(10)
                                        .build());

        assertEquals(expected, delegate.constraintUpdates.get(0));
        assertEquals(1, delegate.constraintUpdates.size());
    }

    /**
     * Helper class to generate a in-app message remote data payload.
     */
    private static class TestPayloadBuilder {

        List<JsonSerializable> constraints = new ArrayList<>();

        List<JsonValue> schedules = new ArrayList<>();
        long timeStamp = System.currentTimeMillis();
        JsonMap metadata = JsonMap.EMPTY_MAP;

        public TestPayloadBuilder setTimeStamp(long timeStamp) {
            this.timeStamp = timeStamp;
            return this;
        }

        public TestPayloadBuilder addConstraint(JsonSerializable constraint) {
            constraints.add(constraint);
            return this;
        }

        public TestPayloadBuilder addLegacySchedule(Schedule<InAppMessage> schedule, long created, long updated) {
            JsonMap messageJson = JsonMap.newBuilder()
                                         .putAll(schedule.getData().toJsonValue().optMap())
                                         .put("message_id", schedule.getId())
                                         .put("audience", schedule.getAudience())
                                         .build();

            JsonMap.Builder scheduleJsonBuilder = JsonMap.newBuilder()
                                                         .put("created", DateUtils.createIso8601TimeStamp(created))
                                                         .put("last_updated", DateUtils.createIso8601TimeStamp(updated))
                                                         .put("triggers", JsonValue.wrapOpt(schedule.getTriggers()))
                                                         .put("limit", schedule.getLimit())
                                                         .put("priority", schedule.getPriority())
                                                         .put("interval", TimeUnit.MILLISECONDS.toSeconds(schedule.getInterval()))
                                                         .put("id", schedule.getId())
                                                         .put("start", schedule.getStart() > 0 ? DateUtils.createIso8601TimeStamp(schedule.getStart()) : null)
                                                         .put("end", schedule.getEnd() > 0 ? DateUtils.createIso8601TimeStamp(schedule.getEnd()) : null)
                                                         .put("audience", schedule.getAudience())
                                                         .put("group", schedule.getGroup())
                                                         .put("delay", schedule.getDelay())
                                                         .put("message", messageJson);

            schedules.add(scheduleJsonBuilder.build().toJsonValue());
            return this;
        }

        public TestPayloadBuilder addSchedule(Schedule<? extends ScheduleData> schedule, long created, long updated) {
            return addSchedule(schedule, created, updated, null);
        }

        public TestPayloadBuilder addSchedule(Schedule<? extends ScheduleData> schedule, long created, long updated, String minSdkVersion) {
            JsonMap.Builder scheduleJsonBuilder = JsonMap.newBuilder()
                                                         .put("min_sdk_version", minSdkVersion)
                                                         .put("created", DateUtils.createIso8601TimeStamp(created))
                                                         .put("last_updated", DateUtils.createIso8601TimeStamp(updated))
                                                         .put("triggers", JsonValue.wrapOpt(schedule.getTriggers()))
                                                         .put("limit", schedule.getLimit())
                                                         .put("priority", schedule.getPriority())
                                                         .put("interval", TimeUnit.MILLISECONDS.toSeconds(schedule.getInterval()))
                                                         .put("id", schedule.getId())
                                                         .put("start", schedule.getStart() > 0 ? DateUtils.createIso8601TimeStamp(schedule.getStart()) : null)
                                                         .put("end", schedule.getEnd() > 0 ? DateUtils.createIso8601TimeStamp(schedule.getEnd()) : null)
                                                         .put("audience", schedule.getAudience())
                                                         .put("group", schedule.getGroup())
                                                         .put("delay", schedule.getDelay())
                                                         .put("campaigns", schedule.getCampaigns())
                                                         .putOpt("frequency_constraint_ids", schedule.getFrequencyConstraintIds());

            switch (schedule.getType()) {
                case Schedule.TYPE_ACTION:
                    scheduleJsonBuilder.put("type", "actions")
                                       .put("actions", schedule.getData());
                    break;

                case Schedule.TYPE_DEFERRED:
                    scheduleJsonBuilder.put("type", "deferred")
                                       .put("deferred", schedule.getData());
                    break;

                case Schedule.TYPE_IN_APP_MESSAGE:
                    scheduleJsonBuilder.put("type", "in_app_message")
                                       .put("message", schedule.getData());
                    break;
            }

            schedules.add(scheduleJsonBuilder.build().toJsonValue());
            return this;
        }

        public TestPayloadBuilder setMetadata(@NonNull JsonMap metadata) {
            this.metadata = metadata;
            return this;
        }

        public RemoteDataPayload build() {
            JsonMap data = JsonMap.newBuilder()
                                  .putOpt("frequency_constraints", JsonValue.wrapOpt(constraints))
                                  .putOpt("in_app_messages", JsonValue.wrapOpt(schedules))
                                  .build();

            return RemoteDataPayload.newBuilder()
                                    .setType("in_app_messages")
                                    .setTimeStamp(timeStamp)
                                    .setMetadata(metadata)
                                    .setData(data)
                                    .build();
        }

    }

    private static class TestDelegate implements InAppRemoteDataObserver.Delegate {

        private final Map<String, Schedule<? extends ScheduleData>> schedules = new HashMap<>();
        private final Map<String, ScheduleEdits<? extends ScheduleData>> scheduleEdits = new HashMap<>();
        private final List<Collection<FrequencyConstraint>> constraintUpdates = new ArrayList<>();

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

        @Override
        public Future<Boolean> updateConstraints(@NonNull Collection<FrequencyConstraint> constraints) {
            PendingResult pendingResult = new PendingResult();
            pendingResult.setResult(constraintUpdates.add(constraints));
            return pendingResult;
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

        public ScheduleEdits<? extends ScheduleData> getScheduleEdits(@NonNull String scheduleId) {
            return scheduleEdits.get(scheduleId);
        }

    }

}
