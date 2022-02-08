/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;

import org.junit.Test;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;

public class AppStoreUtilsTest extends BaseTestCase {
    private Context context = ApplicationProvider.getApplicationContext();
    private AirshipConfigOptions emptyConfig = AirshipConfigOptions.newBuilder().build();

    @Test
    public void testAmazonIntent() {
        Intent intent = AppStoreUtils.getAppStoreIntent(context, UAirship.AMAZON_PLATFORM, emptyConfig);
        assertEquals("amzn://apps/android?p=com.urbanairship.test", intent.getData().toString());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
    }

    @Test
    public void testAndroidIntent() {
        Intent intent = AppStoreUtils.getAppStoreIntent(context, UAirship.ANDROID_PLATFORM, emptyConfig);
        assertEquals("https://play.google.com/store/apps/details?id=com.urbanairship.test", intent.getData().toString());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
    }

    @Test
    public void testOverrideUri() {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder()
                                                                 .setAppStoreUri(Uri.parse("https://neat"))
                                                                 .build();

        Intent intent = AppStoreUtils.getAppStoreIntent(context, UAirship.ANDROID_PLATFORM, configOptions);
        assertEquals("https://neat", intent.getData().toString());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
    }
}
