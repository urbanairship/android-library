package com.urbanairship.automation.deferred;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class StateOverridesTest {

    @Test
    public void testStateOverrides() {
        Locale locale = new Locale("en", "US");
        StateOverrides stateOverrides = new StateOverrides(23, "1.0.0", true, locale);

        JsonValue expected = JsonMap.newBuilder()
                .put("app_version", 23)
                .put("sdk_version", "1.0.0")
                .put("notification_opt_in", true)
                .put("locale_language", "en")
                .put("locale_country", "US")
                .build()
                .toJsonValue();

        assertEquals(expected, stateOverrides.toJsonValue());
    }

}
