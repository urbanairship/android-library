/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.automation.ScheduleDelay;
import com.urbanairship.automation.Trigger;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;

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