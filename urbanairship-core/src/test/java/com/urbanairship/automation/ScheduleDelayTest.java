package com.urbanairship.automation;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.Arrays;

import static junit.framework.Assert.assertEquals;

public class ScheduleDelayTest extends BaseTestCase {

    /**
     * Test parsing schedule delay JSON.
     */
    @Test
    public void testParseJson() throws Exception {
        JsonMap triggerJson = JsonMap.newBuilder()
                                     .put("type", "background")
                                     .put("goal", 1.0)
                                     .build();

        JsonValue screensArray = JsonValue.wrapOpt(Arrays.asList("some screen", "some other screen"));

        JsonMap.Builder mapBuilder = JsonMap.newBuilder()
                                            .put("seconds", 1)
                                            .put("app_state", "foreground")
                                            .put("region_id", "some region")
                                            .put("cancellation_triggers", JsonValue.wrapOpt(Arrays.asList(triggerJson)));

        JsonMap singleScreenMap = mapBuilder.put("screen", "some screen").build();
        JsonMap multiScreenMap = mapBuilder.put("screen", screensArray).build();

        ScheduleDelay singleScreenDelay = ScheduleDelay.fromJson(singleScreenMap.toJsonValue());
        ScheduleDelay multiScreenDelay = ScheduleDelay.fromJson(multiScreenMap.toJsonValue());

        for (ScheduleDelay delay : Arrays.asList(singleScreenDelay, multiScreenDelay)) {
            assertEquals(delay.getSeconds(), 1);
            assertEquals(delay.getAppState(), ScheduleDelay.APP_STATE_FOREGROUND);
            assertEquals(delay.getRegionId(), "some region");

            Trigger trigger = delay.getCancellationTriggers().get(0);
            Trigger otherTrigger = Trigger.fromJson(triggerJson.toJsonValue());

            assertEquals(trigger.getType(), otherTrigger.getType());
            assertEquals(trigger.getGoal(), otherTrigger.getGoal());
        }
    }

    /**
     * Test parsing JSON with an unsupported app state throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseInvalidAppState() throws JsonException {
        ScheduleDelay.fromJson(JsonMap.newBuilder()
                                      .put("seconds", 1)
                                      .put("app_state", "not an app state")
                                      .build()
                                      .toJsonValue());
    }

    /**
     * Test parsing JSON with missing app state defaults to "any".
     */
    @Test
    public void testParseMissingAppStateDefaultsToAny() throws Exception {
        ScheduleDelay delay = ScheduleDelay.fromJson(JsonMap.newBuilder().put("seconds", 1)
                                                            .build()
                                                            .toJsonValue());

        assertEquals(delay.getAppState(), ScheduleDelay.APP_STATE_ANY);
    }

}
