/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.automation.actions.Actions;
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
        List<Schedule<? extends ScheduleData>> scheduleList = new ArrayList<>();

        // Action schedule
        JsonMap actionsMap = JsonMap.newBuilder()
                                 .put("cool", "story")
                                 .build();
        scheduleList.add(Schedule.newBuilder(new Actions(actionsMap))
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
                                 .setProductId("product-id")
                                 .build());

        // Message schedule
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(new CustomDisplayContent(JsonMap.EMPTY_MAP.toJsonValue()))
                                           .build();
        scheduleList.add(Schedule.newBuilder(message)
                                 .setId("message!")
                                 .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                                 .build());

        List<FullSchedule> storageSchedules = ScheduleConverters.convertSchedules(scheduleList);
        assertEquals(2, storageSchedules.size());
        assertEquals(scheduleList.get(0), ScheduleConverters.convert(storageSchedules.get(0)));
        assertEquals(scheduleList.get(1), ScheduleConverters.convert(storageSchedules.get(1)));
    }
}
