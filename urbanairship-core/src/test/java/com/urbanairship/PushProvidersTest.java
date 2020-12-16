package com.urbanairship;

import com.urbanairship.push.PushProvider;

import org.junit.Test;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PushProvidersTest extends BaseTestCase {

    @Test
    public void testValidPushProvider() {
        verifyInvalidProvider(new TestPushProvider(UAirship.AMAZON_PLATFORM, PushProvider.FCM_DELIVERY_TYPE));
        verifyInvalidProvider(new TestPushProvider(UAirship.AMAZON_PLATFORM, PushProvider.HMS_DELIVERY_TYPE));
        verifyInvalidProvider(new TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.ADM_DELIVERY_TYPE));

        verifyValidProvider(new TestPushProvider(UAirship.AMAZON_PLATFORM, PushProvider.ADM_DELIVERY_TYPE));
        verifyValidProvider(new TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.HMS_DELIVERY_TYPE));
        verifyValidProvider(new TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.FCM_DELIVERY_TYPE));
    }

    public static void verifyValidProvider(@NonNull TestPushProvider provider) {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder()
                                                                 .setCustomPushProvider(provider)
                                                                 .build();

        PushProviders providers = PushProviders.load(ApplicationProvider.getApplicationContext(), configOptions);
        assertEquals(1, providers.getAvailableProviders().size());
        assertEquals(provider, providers.getAvailableProviders().get(0));
    }

    public static void verifyInvalidProvider(@NonNull TestPushProvider provider) {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder()
                                                                 .setCustomPushProvider(provider)
                                                                 .build();

        PushProviders providers = PushProviders.load(ApplicationProvider.getApplicationContext(), configOptions);
        assertTrue(providers.getAvailableProviders().isEmpty());
    }

}
