package com.urbanairship.push.hms;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class HmsTokenCacheTest  {

    @Test
    public void testTokenCache() {
        Context context = ApplicationProvider.getApplicationContext();
        HmsTokenCache cache = new HmsTokenCache();

        assertNull(cache.get(context));

        cache.set(context, "token");
        assertEquals("token", cache.get(context));

        cache.set(context, "rad");
        assertEquals("rad", cache.get(context));

        cache.set(context, null);
        assertNull(cache.get(context));
    }

    @Test
    public void testTokenPersists() {
        Context context = ApplicationProvider.getApplicationContext();
        HmsTokenCache cache = new HmsTokenCache();

        cache.set(context, "token");
        assertEquals("token", cache.get(context));

        cache = new HmsTokenCache();
        assertEquals("token", cache.get(context));
    }
}
