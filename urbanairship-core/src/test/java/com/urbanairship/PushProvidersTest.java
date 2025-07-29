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
        verifyInvalidProvider(new TestPushProvider(UAirship.AMAZON_PLATFORM, PushProvider.DeliveryType.FCM));
        verifyInvalidProvider(new TestPushProvider(UAirship.AMAZON_PLATFORM, PushProvider.DeliveryType.HMS));
        verifyInvalidProvider(new TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.DeliveryType.ADM));

        verifyValidProvider(new TestPushProvider(UAirship.AMAZON_PLATFORM, PushProvider.DeliveryType.ADM));
        verifyValidProvider(new TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.DeliveryType.HMS));
        verifyValidProvider(new TestPushProvider(UAirship.ANDROID_PLATFORM, PushProvider.DeliveryType.FCM));
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
