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

    private static final String APP_VERSION = "23";
    private static final String SDK_VERSION = "1.0.0";
    private static final boolean NOTIFICATION_OPT_IN = true;
    private static final String LOCALE_LANGUAGE = "en";
    private static final String LOCALE_COUNTRY = "US";

    @Test
    public void testStateOverrides() {
        Locale locale = new Locale(LOCALE_LANGUAGE, LOCALE_COUNTRY);
        StateOverrides stateOverrides = new StateOverrides(APP_VERSION, SDK_VERSION, NOTIFICATION_OPT_IN, locale);

        JsonValue expected = JsonMap.newBuilder()
                .put("app_version", APP_VERSION)
                .put("sdk_version", SDK_VERSION)
                .put("notification_opt_in", NOTIFICATION_OPT_IN)
                .put("locale_language", LOCALE_LANGUAGE)
                .put("locale_country", LOCALE_COUNTRY)
                .build()
                .toJsonValue();

        assertEquals(expected, stateOverrides.toJsonValue());
    }

}
