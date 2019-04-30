/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.os.Parcel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;

public class ActionScheduleInfoTest extends BaseTestCase {

    @Test
    public void testParseJson() throws Exception {
        List<JsonMap> triggersJson = new ArrayList<>();
        triggersJson.add(JsonMap.newBuilder()
                                .put("type", "foreground")
                                .put("goal", 20.0)
                                .build());

        JsonMap actions = JsonMap.newBuilder()
                                 .put("tag_action", "cat")
                                 .build();

        JsonMap scheduleJson = JsonMap.newBuilder()
                                      .put("actions", actions)
                                      .put("triggers", JsonValue.wrapOpt(triggersJson))
                                      .put("group", "group id")
                                      .put("limit", 10)
                                      .put("priority", 1)
                                      .put("start", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L)))
                                      .put("end", JsonValue.wrap(DateUtils.createIso8601TimeStamp(15000L)))
                                      .put("edit_grace_period", 10)
                                      .build();

        ActionScheduleInfo info = ActionScheduleInfo.fromJson(scheduleJson.toJsonValue());

        // Schedule Info
        assertEquals(10, info.getLimit());
        assertEquals(1, info.getPriority());
        assertEquals(actions, JsonValue.wrap(info.getActions()).getMap());
        assertEquals(10000L, info.getStart());
        assertEquals(15000L, info.getEnd());
        assertEquals("group id", info.getGroup());
        assertEquals(TimeUnit.DAYS.toMillis(10), info.getEditGracePeriod());

        // Triggers
        assertEquals(1, info.getTriggers().size());
        assertEquals(Trigger.LIFE_CYCLE_FOREGROUND, info.getTriggers().get(0).getType());
        assertEquals(20.0, info.getTriggers().get(0).getGoal());
    }

    @Test(expected = JsonException.class)
    public void testParseInvalidJson() throws JsonException {
        ActionScheduleInfo.fromJson(JsonValue.NULL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyTriggers() throws JsonException {
        new ActionScheduleInfo.Builder()
                .addAction("cool", JsonValue.wrap("story"))
                .build();
    }

    @Test
    public void testParcelable() {
        ActionScheduleInfo info = new ActionScheduleInfo.Builder()
                .addAction("cool", JsonValue.wrap("story"))
                .addTrigger(Triggers.newAppInitTriggerBuilder().setGoal(1).build())
                .setDelay(ScheduleDelay.newBuilder().setScreen("cool")
                                       .build())
                .setEnd(100)
                .setStart(0)
                .setGroup("group")
                .setEditGracePeriod(100, TimeUnit.DAYS)
                .setLimit(1)
                .setPriority(100)
                .build();

        // Write the push message to a parcel
        Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the schedule from the parcel
        ActionScheduleInfo fromParcel = ActionScheduleInfo.CREATOR.createFromParcel(parcel);

        // Validate the data
        assertEquals(info, fromParcel);
    }

}