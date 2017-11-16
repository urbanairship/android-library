/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.remotedata.RemoteDataPayload;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * {@link InAppRemoteDataObserver} tests.
 */
public class InAppRemoteDataObserverTest extends BaseTestCase {

    private InAppRemoteDataObserver observer;
    private InAppRemoteDataObserver.Callback callback;

    @Before
    public void setup() {
        callback = mock(InAppRemoteDataObserver.Callback.class);
        observer = new InAppRemoteDataObserver(TestApplication.getApplication().preferenceDataStore, callback);
    }

    @Test
    public void testSchedule() {
        // Create a payload with foo and bar.
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .addScheduleInfo("bar", TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1))
                .setTimeStamp(TimeUnit.DAYS.toMillis(1))
                .build();

        // Notify the observer
        observer.onNext(payload);

        // Verify we get a callback to schedule foo and bar
        verify(callback).onSchedule(Mockito.argThat(new ArgumentMatcher<List<InAppMessageScheduleInfo>>() {
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

        // Notify the observer
        observer.onNext(payload);

        // Verify we get a callback with to schedule baz
        verify(callback).onSchedule(Mockito.argThat(new ArgumentMatcher<List<InAppMessageScheduleInfo>>() {
            @Override
            public boolean matches(List<InAppMessageScheduleInfo> argument) {
                if (argument.size() != 1) {
                    return false;
                }
                return argument.get(0).getInAppMessage().getId().equals("baz");
            }
        }));

        verifyNoMoreInteractions(callback);

    }

    @Test
    public void testCancel() {
        // Schedule messages
        RemoteDataPayload payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", 100, 100)
                .addScheduleInfo("bar", 100, 100)
                .build();

        observer.onNext(payload);

        // Update the message without bar
        payload = new TestPayloadBuilder()
                .addScheduleInfo("foo", 100, 100)
                .build();

        observer.onNext(payload);

        // Verify callback is called to cancel bar
        verify(callback).onCancel(Mockito.argThat(new ArgumentMatcher<List<String>>() {
            @Override
            public boolean matches(List<String> argument) {
                if (argument.size() != 1) {
                    return false;
                }
                return argument.contains("bar");
            }
        }));
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

        public TestPayloadBuilder addScheduleInfo(String messageId, long created, long updated) {
            List<JsonMap> triggersJson = new ArrayList<>();
            triggersJson.add(JsonMap.newBuilder()
                                    .put("type", "foreground")
                                    .put("goal", 20.0)
                                    .build());

            InAppMessage message = InAppMessage.newBuilder()
                                               .setId(messageId)
                                               .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                               .build();


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

        public RemoteDataPayload build() {
            JsonMap data = JsonMap.newBuilder().putOpt("in_app_messages", JsonValue.wrapOpt(schedules)).build();
            return new RemoteDataPayload("in_app_messages", timeStamp, data);
        }
    }
}