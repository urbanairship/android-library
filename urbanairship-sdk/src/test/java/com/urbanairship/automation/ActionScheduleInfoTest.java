/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
                                      .put("start", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L)))
                                      .put("end", JsonValue.wrap(DateUtils.createIso8601TimeStamp(15000L)))
                                      .build();

        ActionScheduleInfo info = ActionScheduleInfo.parseJson(scheduleJson.toJsonValue());

        // Schedule Info
        assertEquals(10, info.getLimit());
        assertEquals(actions, JsonValue.wrap(info.getActions()).getMap());
        assertEquals(10000L, info.getStart());
        assertEquals(15000L, info.getEnd());
        assertEquals("group id", info.getGroup());

        // Triggers
        assertEquals(1, info.getTriggers().size());
        assertEquals(Trigger.LIFE_CYCLE_FOREGROUND, info.getTriggers().get(0).getType());
        assertEquals(20.0, info.getTriggers().get(0).getGoal());
    }

    @Test(expected = JsonException.class)
    public void testParseInvalidJson() throws JsonException {
        ActionScheduleInfo.parseJson(JsonValue.NULL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyTriggers() throws JsonException {
        new ActionScheduleInfo.Builder()
                .addAction("cool", JsonValue.wrap("story"))
                .build();
    }
}