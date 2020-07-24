/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.automation.storage.FullSchedule;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class ScheduleConvertersTest {
    @Test
    public void testConvert() throws JsonException {
        List<Schedule> scheduleList = new ArrayList<>();

        // Action schedule
        JsonMap actions = JsonMap.newBuilder()
                                 .put("cool", "story")
                                 .build();
        scheduleList.add(Schedule.newActionScheduleBuilder(actions)
                                 .setId("actions!")
                                 .setGroup("some-group")
                                 .setLimit(100)
                                 .setPriority(2)
                                 .setInterval(100, TimeUnit.SECONDS)
                                 .setEnd(1000)
                                 .setStart(1)
                                 .setMetadata(JsonMap.newBuilder()
                                                     .put("some-key", "some-value")
                                                     .build())
                                 .setEditGracePeriod(1000, TimeUnit.DAYS)
                                 .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                 .build());

        // Message schedule
        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("some-id")
                                           .setDisplayContent(new CustomDisplayContent(JsonMap.EMPTY_MAP.toJsonValue()))
                                           .build();
        scheduleList.add(Schedule.newMessageScheduleBuilder(message)
                                 .setId("message!")
                                 .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                 .build());

        List<FullSchedule> storageSchedules = ScheduleConverters.convertSchedules(scheduleList);
        assertEquals(2, storageSchedules.size());
        assertEquals(scheduleList.get(0), ScheduleConverters.convert(storageSchedules.get(0)));
        assertEquals(scheduleList.get(1), ScheduleConverters.convert(storageSchedules.get(1)));
    }
}
