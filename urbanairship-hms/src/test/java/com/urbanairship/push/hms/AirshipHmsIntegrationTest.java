package com.urbanairship.push.hms;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.*;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AirshipHmsIntegrationTest {

    @Test
    public void testProcessNewToken() {
        Context context = ApplicationProvider.getApplicationContext();
        HmsTokenCache cache = HmsTokenCache.shared();

        assertNull(cache.get(context));

        AirshipHmsIntegration.processNewToken(context, "token");
        assertEquals("token", cache.get(context));
    }

}
