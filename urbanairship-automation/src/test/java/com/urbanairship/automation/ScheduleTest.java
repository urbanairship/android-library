/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

/**
 * {@link com.urbanairship.automation.Schedule} tests
 */
@RunWith(AndroidJUnit4.class)
public class ScheduleTest {

    private InAppMessage message;
    private Trigger trigger;
    private ScheduleDelay delay;
    private JsonMap actions;

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

        actions = JsonMap.newBuilder()
                         .put("cool", "story")
                         .build();
    }

    @Test
    public void testMessage() {
        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
                                    .addTrigger(trigger)
                                    .build();
        assertEquals(message, schedule.getData());
        assertEquals(Schedule.TYPE_IN_APP_MESSAGE, schedule.getType());
    }

    @Test
    public void testActions() {
        Schedule schedule = Schedule.newActionScheduleBuilder(actions)
                                    .addTrigger(trigger)
                                    .build();
        assertEquals(actions, schedule.getData());
        assertEquals(Schedule.TYPE_ACTION, schedule.getType());
    }

    @Test
    public void testBuilder() {
        Schedule schedule = Schedule.newMessageScheduleBuilder(message)
                                    .addTrigger(trigger)
                                    .setStart(1)
                                    .setEnd(2)
                                    .setLimit(3)
                                    .setDelay(delay)
                                    .setPriority(-4)
                                    .setId("schedule id")
                                    .setGroup("group")
                                    .build();

        assertEquals(1, schedule.getTriggers().size());
        assertEquals(trigger, schedule.getTriggers().get(0));
        assertEquals(1, schedule.getStart());
        assertEquals(2, schedule.getEnd());
        assertEquals(3, schedule.getLimit());
        assertEquals(delay, schedule.getDelay());
        assertEquals("group", schedule.getGroup());
        assertEquals(-4, schedule.getPriority());
        assertEquals(message, schedule.getData());
        assertEquals(Schedule.TYPE_IN_APP_MESSAGE, schedule.getType());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testEndBeforeStart() {
        Schedule.newMessageScheduleBuilder(message)
                .addTrigger(trigger)
                .setEnd(100)
                .setStart(1000)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingTrigger() {
        Schedule.newActionScheduleBuilder(actions)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTooManyTriggers() {

        List<Trigger> triggers = new ArrayList<>();
        for (int i = 0; i <= 11; i++) {
            triggers.add(trigger);
        }

        Schedule.newMessageScheduleBuilder(message)
                .addTriggers(triggers)
                .build();
    }

}
