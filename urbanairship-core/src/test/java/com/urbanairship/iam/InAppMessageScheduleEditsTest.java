/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;

/**
 * {@link InAppMessageScheduleEdits} tests.
 */
public class InAppMessageScheduleEditsTest extends BaseTestCase {

    @Test
    public void testFromJson() throws JsonException {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("message id")
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();
        JsonMap editJson = JsonMap.newBuilder()
                                  .put("message", message)
                                  .put("group", "group id")
                                  .put("limit", 10)
                                  .put("priority", 1)
                                  .put("start", JsonValue.wrap(DateUtils.createIso8601TimeStamp(10000L)))
                                  .put("end", JsonValue.wrap(DateUtils.createIso8601TimeStamp(15000L)))
                                  .put("edit_grace_period", 10)
                                  .put("interval", 20)
                                  .build();

        InAppMessageScheduleEdits edits = InAppMessageScheduleEdits.fromJson(editJson.toJsonValue());

        assertEquals(message, edits.getMessage());
        assertEquals(message, edits.getData());
        assertEquals(15000L, edits.getEnd().longValue());
        assertEquals(10000L, edits.getStart().longValue());
        assertEquals(10, edits.getLimit().longValue());
        assertEquals(1, edits.getPriority().intValue());
        assertEquals(TimeUnit.DAYS.toMillis(10), edits.getEditGracePeriod().longValue());
        assertEquals(TimeUnit.SECONDS.toMillis(20), edits.getInterval().longValue());

    }

}