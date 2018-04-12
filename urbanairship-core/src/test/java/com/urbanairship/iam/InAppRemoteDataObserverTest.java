/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

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
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import edu.emory.mathcs.backport.java.util.Collections;

import static junit.framework.Assert.assertEquals;
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
    private InAppMessageScheduler scheduler;
    private RemoteData remoteData;
    private Subject<RemoteDataPayload> updates;

    @Before
    public void setup() {
        remoteData = mock(RemoteData.class);
        updates = Subject.create();
        when(remoteData.payloadsForType(anyString())).thenReturn(updates);

        scheduler = mock(InAppMessageScheduler.class);

        PendingResult<List<InAppMessageSchedule>> scheduleResult = new PendingResult<>();
        scheduleResult.setResult(Collections.emptyList());
        when(scheduler.schedule(ArgumentMatchers.<InAppMessageScheduleInfo>anyList())).thenReturn(scheduleResult);

        PendingResult<Void> cancelResult = new PendingResult<>();
        cancelResult.setResult(null);
        when(scheduler.cancelMessages(ArgumentMatchers.<String>anyCollection())).thenReturn(cancelResult);

        observer = new InAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore);
        observer.subscribe(remoteData, scheduler);
    }

    @Test
    public void testSchedule() {
        // Create a payload with foo and bar.
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addScheduleInfo("bar", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .build();


        // Return empty pending results when the message is requested
        when(scheduler.getSchedules("foo")).thenReturn(createMessagesPendingResult());
        when(scheduler.getSchedules("bar")).thenReturn(createMessagesPendingResult());

        // Notify the observer
        updates.onNext(payload);

        // Verify we get a callback to schedule foo and bar
        verify(scheduler).schedule(Mockito.argThat(new ArgumentMatcher<List<InAppMessageScheduleInfo>>() {
            @Override
            public boolean matches(List<InAppMessageScheduleInfo> argument) {
                if (argument.size() != 2) {
                    return false;
                }

                List<String> ids = Arrays.asList(argument.get(0).getInAppMessage().getId(), argument.get(1).getInAppMessage().getId());
                return ids.contains("foo") && ids.contains("bar");
            }
        }));

        // Create another payload with added baz
        payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addScheduleInfo("bar", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addScheduleInfo("baz", TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(2))
                .setTimeStamp(TimeUnit.DAYS.toMillis(2))
                .build();

        when(scheduler.getSchedules("baz")).thenReturn(createMessagesPendingResult());

        // Notify the observer
        updates.onNext(payload);

        // Verify we get a callback with to schedule baz
        verify(scheduler).schedule(Mockito.argThat(new ArgumentMatcher<List<InAppMessageScheduleInfo>>() {
            @Override
            public boolean matches(List<InAppMessageScheduleInfo> argument) {
                if (argument.size() != 1) {
                    return false;
                }
                return argument.get(0).getInAppMessage().getId().equals("baz");
            }
        }));
    }

    @Test
    public void testEndMessages() {
        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", 100, 100)
                .addScheduleInfo("bar", 100, 100)
                .build();


        // Return empty pending results when the message is requested
        PendingResult<Collection<InAppMessageSchedule>> barPendingResult = createMessagesPendingResult("bar");
        when(scheduler.getSchedules("bar")).thenReturn(barPendingResult);

        // Return empty pending results when the message is requested
        when(scheduler.getSchedules("foo")).thenReturn(createMessagesPendingResult("foo"));

        updates.onNext(payload);

        // Update the message without bar
        payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", 100, 100)
                .build();


        String scheduleId = barPendingResult.getResult().iterator().next().getId();
        InAppMessage message = barPendingResult.getResult().iterator().next().getInfo().getInAppMessage();

        // Return pending result for the edit
        PendingResult<InAppMessageSchedule> editPendingResult = createMessagePendingResult(message, scheduleId);
        when(scheduler.editSchedule(eq(scheduleId), any(InAppMessageScheduleEdits.class)))
                .thenReturn(editPendingResult);

        updates.onNext(payload);

        // Verify callback is called to end bar
        verify(scheduler).editSchedule(eq(scheduleId), Mockito.argThat(new ArgumentMatcher<InAppMessageScheduleEdits>() {
            @Override
            public boolean matches(InAppMessageScheduleEdits argument) {
                return argument.getEnd() == 0 && argument.getStart() == -1;
            }
        }));
    }

    @Test
    public void testEdit() {
        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .build();


        // Return empty pending results when the message is requested
        PendingResult<Collection<InAppMessageSchedule>> pendingResult = createMessagesPendingResult("foo");
        String scheduleId = pendingResult.getResult().iterator().next().getId();
        when(scheduler.getSchedules("foo")).thenReturn(pendingResult);

        // Process payload
        updates.onNext(payload);


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
        PendingResult<InAppMessageSchedule> editPendingResult = createMessagePendingResult(message, scheduleId);
        when(scheduler.editSchedule(eq(scheduleId), any(InAppMessageScheduleEdits.class)))
                .thenReturn(editPendingResult);

        updates.onNext(payload);

        // Verify callback is called to cancel bar
        verify(scheduler).editSchedule(eq(scheduleId), Mockito.argThat(new ArgumentMatcher<InAppMessageScheduleEdits>() {
            @Override
            public boolean matches(InAppMessageScheduleEdits argument) {
                return argument.getMessage().equals(message);
            }
        }));
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

        public TestPayloadBuilder addScheduleInfo(String messageId, long created, long updated) {
            InAppMessage message = createMessage(messageId);
            return addScheduleInfo(message, created, updated);
        }

        public RemoteDataPayload build() {
            JsonMap data = JsonMap.newBuilder().putOpt("in_app_messages", JsonValue.wrapOpt(schedules)).build();
            return new RemoteDataPayload("in_app_messages", timeStamp, data);
        }
    }

    private static PendingResult<InAppMessageSchedule> createMessagePendingResult(InAppMessage message, String scheduleId) {
        PendingResult<InAppMessageSchedule> pendingResult = new PendingResult<>();

        InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                        .setMessage(message)
                                                                        .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                        .build();

        pendingResult.setResult(new InAppMessageSchedule(scheduleId, scheduleInfo));
        return pendingResult;
    }


    private static PendingResult<Collection<InAppMessageSchedule>> createMessagesPendingResult(String... ids) {
        PendingResult<Collection<InAppMessageSchedule>> pendingResult = new PendingResult<>();

        Collection<InAppMessageSchedule> collection = new HashSet<>();

        if (ids != null) {
            for (String id : ids) {
                InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                                .setMessage(createMessage(id))
                                                                                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                                                                .build();

                collection.add(new InAppMessageSchedule(UUID.randomUUID().toString(), scheduleInfo));
            }
        }

        pendingResult.setResult(collection);
        return pendingResult;
    }

    private static InAppMessage createMessage(String messageId) {
        return InAppMessage.newBuilder()
                           .setId(messageId)
                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                           .build();

    }
}