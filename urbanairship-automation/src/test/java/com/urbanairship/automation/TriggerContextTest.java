package com.urbanairship.automation;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class TriggerContextTest {

    @Test
    public void testJson() throws JsonException {
        Trigger trigger = Triggers.newActiveSessionTriggerBuilder().setGoal(10).build();
        JsonValue event = JsonMap.newBuilder()
               .put("cool", "story")
               .build()
               .toJsonValue();

        TriggerContext context = new TriggerContext(trigger, event);
        TriggerContext fromJson = TriggerContext.fromJson(context.toJsonValue());

        assertEquals(context, fromJson);
    }

}
