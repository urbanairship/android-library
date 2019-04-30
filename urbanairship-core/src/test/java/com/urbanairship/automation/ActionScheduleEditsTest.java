/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;

/**
 * {@link ActionScheduleEdits} tests.
 */
public class ActionScheduleEditsTest extends BaseTestCase {

    @Test
    public void testFromJson() throws JsonException {
        JsonMap actions = JsonMap.newBuilder()
                                 .put("tag_action", "cat")
                                 .build();

        JsonMap editJson = JsonMap.newBuilder()
                                  .put("actions", actions)
                                  .put("group", "group id")
                                  .put("limit", 10)
                                  .put("priority", 1)
                                  .put("start", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L)))
                                  .put("end", JsonValue.wrap(DateUtils.createIso8601TimeStamp(15000L)))
                                  .put("edit_grace_period", 10)
                                  .put("interval", 20)
                                  .build();

        ActionScheduleEdits edits = ActionScheduleEdits.fromJson(editJson.toJsonValue());

        assertEquals(actions.getMap(), edits.getActions());
        assertEquals(actions.toJsonValue(), edits.getData());
        assertEquals(15000L, edits.getEnd().longValue());
        assertEquals(10000L, edits.getStart().longValue());
        assertEquals(10, edits.getLimit().longValue());
        assertEquals(1, edits.getPriority().intValue());
        assertEquals(TimeUnit.DAYS.toMillis(10), edits.getEditGracePeriod().longValue());
        assertEquals(TimeUnit.SECONDS.toMillis(20), edits.getInterval().longValue());
    }

}