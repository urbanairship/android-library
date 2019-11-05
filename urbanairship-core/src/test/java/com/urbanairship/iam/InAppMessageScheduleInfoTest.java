/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.automation.ScheduleDelay;
import com.urbanairship.automation.Trigger;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * {@link InAppMessageScheduleInfo} tests
 */
public class InAppMessageScheduleInfoTest extends BaseTestCase {

    InAppMessage message;
    Trigger trigger;
    ScheduleDelay delay;

    @Before
    public void setup() {
        message = InAppMessage.newBuilder()
                              .setId("message id")
                              .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                              .build();

        trigger = Triggers.newForegroundTriggerBuilder()
                          .setGoal(1)
                          .build();

        delay = ScheduleDelay.newBuilder()
                             .setScreen("screen")
                             .setSeconds(100)
                             .build();
    }

    @Test
    public void testFromJson() throws Exception {
        List<JsonMap> triggersJson = new ArrayList<>();
        triggersJson.add(JsonMap.newBuilder()
                                .put("type", "foreground")
                                .put("goal", 20.0)
                                .build());

        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("some-id")
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        JsonMap scheduleJson = JsonMap.newBuilder()
                                      .put("message", message)
                                      .put("triggers", JsonValue.wrapOpt(triggersJson))
                                      .put("limit", 10)
                                      .put("priority", 1)
                                      .put("start", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L)))
                                      .put("end", JsonValue.wrap(DateUtils.createIso8601TimeStamp(15000L)))
                                      .put("edit_grace_period", 10)
                                      .build();

        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.fromJson(scheduleJson.toJsonValue());

        // Schedule Info
        assertEquals(10, info.getLimit());
        assertEquals(1, info.getPriority());
        assertEquals(message, info.getInAppMessage());
        assertEquals(10000L, info.getStart());
        assertEquals(15000L, info.getEnd());
        assertEquals("some-id", info.getGroup());
        assertEquals(TimeUnit.DAYS.toMillis(10), info.getEditGracePeriod());

        // Triggers
        assertEquals(1, info.getTriggers().size());
        assertEquals(Trigger.LIFE_CYCLE_FOREGROUND, info.getTriggers().get(0).getType());
        assertEquals(20.0, info.getTriggers().get(0).getGoal());
    }

    @Test
    public void testParseMessageId() throws Exception {
        List<JsonMap> triggersJson = new ArrayList<>();
        triggersJson.add(JsonMap.newBuilder()
                                .put("type", "foreground")
                                .put("goal", 20.0)
                                .build());

        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("some-id")
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        JsonMap scheduleJson = JsonMap.newBuilder()
                                      .put("message", message)
                                      .put("triggers", JsonValue.wrapOpt(triggersJson))
                                      .put("limit", 10)
                                      .put("priority", 1)
                                      .put("start", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L)))
                                      .put("end", JsonValue.wrap(DateUtils.createIso8601TimeStamp(15000L)))
                                      .build();

        assertEquals("some-id", InAppMessageScheduleInfo.parseMessageId(scheduleJson.toJsonValue()));
    }

    @Test
    public void testBuilder() {
        InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                        .addTrigger(trigger)
                                                                        .setStart(1)
                                                                        .setEnd(2)
                                                                        .setLimit(3)
                                                                        .setDelay(delay)
                                                                        .setPriority(-4)
                                                                        .setMessage(message)
                                                                        .build();

        assertEquals(1, scheduleInfo.getTriggers().size());
        assertEquals(trigger, scheduleInfo.getTriggers().get(0));
        assertEquals(1, scheduleInfo.getStart());
        assertEquals(2, scheduleInfo.getEnd());
        assertEquals(3, scheduleInfo.getLimit());
        assertEquals(delay, scheduleInfo.getDelay());
        assertEquals("message id", scheduleInfo.getGroup());
        assertEquals(-4, scheduleInfo.getPriority());
        assertEquals(message, scheduleInfo.getInAppMessage());
        assertEquals(message, scheduleInfo.getData());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testEndBeforeStart() {
        InAppMessageScheduleInfo.newBuilder()
                                .addTrigger(trigger)
                                .setEnd(100)
                                .setStart(1000)
                                .setMessage(message)
                                .build();
    }

    @Test
    public void testEndEqualsStart() {
        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(trigger)
                                                                .setStart(1000)
                                                                .setEnd(1000)
                                                                .setMessage(message)
                                                                .build();

        assertNotNull(info);
    }

    @Test
    public void testStartNoEnd() {
        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(trigger)
                                                                .setStart(1000)
                                                                .setMessage(message)
                                                                .build();

        assertNotNull(info);
    }

    @Test
    public void testEndNoStart() {
        InAppMessageScheduleInfo info = InAppMessageScheduleInfo.newBuilder()
                                                                .addTrigger(trigger)
                                                                .setEnd(1000)
                                                                .setMessage(message)
                                                                .build();

        assertNotNull(info);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingMessage() {
        InAppMessageScheduleInfo.newBuilder()
                                .addTrigger(trigger)
                                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingTrigger() {
        InAppMessageScheduleInfo.newBuilder()
                                .setMessage(message)
                                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTooManyTriggers() {

        List<Trigger> triggers = new ArrayList<>();
        for (int i = 0; i <= 11; i++) {
            triggers.add(trigger);
        }

        InAppMessageScheduleInfo.newBuilder()
                                .setMessage(message)
                                .addTriggers(triggers)
                                .build();
    }

}