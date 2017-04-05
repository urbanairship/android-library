/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.util.SparseArray;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonMatcher;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class TriggerTest extends BaseTestCase {

    /**
     * Test parsing trigger JSON.
     */
    @Test
    public void testParseJson() throws Exception {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setValueMatcher(ValueMatcher.newIsAbsentMatcher())
                                                                      .build())
                                               .build();

        SparseArray<String> typeArray = new SparseArray<>();
        typeArray.put(Trigger.CUSTOM_EVENT_COUNT, "custom_event_count");
        typeArray.put(Trigger.CUSTOM_EVENT_VALUE, "custom_event_value");
        typeArray.put(Trigger.LIFE_CYCLE_FOREGROUND, "foreground");
        typeArray.put(Trigger.LIFE_CYCLE_BACKGROUND, "background");
        typeArray.put(Trigger.REGION_ENTER, "region_enter");
        typeArray.put(Trigger.REGION_EXIT, "region_exit");
        typeArray.put(Trigger.SCREEN_VIEW, "screen");
        typeArray.put(Trigger.LIFE_CYCLE_APP_INIT, "app_init");

        for(int i = 0; i < typeArray.size(); i++) {
            int key = typeArray.keyAt(i);

            JsonMap triggerJson = JsonMap.newBuilder()
                                         .put("type", typeArray.get(key))
                                         .put("goal", 20.0)
                                         .put("predicate", predicate)
                                         .build();

            Trigger trigger = Trigger.parseJson(triggerJson.toJsonValue());

            // Triggers
            assertEquals(key, trigger.getType());
            assertEquals(20.0, trigger.getGoal());
            assertEquals(predicate, trigger.getPredicate());
        }
    }

    /**
     * Test parsing empty JSON throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseEmptyJson() throws JsonException {
        ActionScheduleInfo.parseJson(JsonValue.NULL);
    }

    /**
     * Test parsing JSON with an unsupported type throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseInvalidType() throws JsonException {
        JsonMap triggerJson = JsonMap.newBuilder()
                                     .put("type", "what")
                                     .put("goal", 100)
                                     .build();

        Trigger.parseJson(triggerJson.toJsonValue());
    }

    /**
     * Test parsing JSON with an invalid goal throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseInvalidGoal() throws JsonException {
        JsonMap triggerJson = JsonMap.newBuilder()
                                     .put("type", "foreground")
                                     .put("goal", -100)
                                     .build();

        Trigger.parseJson(triggerJson.toJsonValue());
    }
}