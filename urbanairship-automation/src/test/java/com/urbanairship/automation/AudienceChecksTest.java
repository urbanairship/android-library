/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.util.Base64;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.TestApplication;
import com.urbanairship.automation.tags.TagSelector;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AudienceChecks} tests.
 */
@RunWith(AndroidJUnit4.class)
public class AudienceChecksTest {

    private AirshipChannel airshipChannel;
    private PushManager pushManager;
    private AirshipLocationClient locationClient;
    private Context context;
    private ApplicationMetrics applicationMetrics;
    private PrivacyManager privacyManager;

    @Before
    public void setup() {
        airshipChannel = mock(AirshipChannel.class);
        pushManager = mock(PushManager.class);
        locationClient = mock(AirshipLocationClient.class);
        applicationMetrics = mock(ApplicationMetrics.class);
        context = TestApplication.getApplication();

        PreferenceDataStore dataStore = PreferenceDataStore.inMemoryStore(TestApplication.getApplication());
        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);

        TestApplication.getApplication().setChannel(airshipChannel);
        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setLocationClient(locationClient);
        TestApplication.getApplication().setApplicationMetrics(applicationMetrics);
        TestApplication.getApplication().setPrivacyManager(privacyManager);
    }

    @Test
    public void testEmptyAudience() {
        Audience audience = Audience.newBuilder().build();

        assertTrue(AudienceChecks.checkAudience(context, audience));
    }

    @Test
    public void testNotificationOptIn() {
        Audience requiresOptedIn = Audience.newBuilder()
                                           .setNotificationsOptIn(true)
                                           .build();

        Audience requiresOptedOut = Audience.newBuilder()
                                            .setNotificationsOptIn(false)
                                            .build();

        // PushManager returns false by default
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedIn));
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedOut));

        when(pushManager.areNotificationsOptedIn()).thenReturn(true);
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedIn));
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedOut));
    }

    @Test
    public void testLocationOptIn() {
        Audience requiresOptedIn = Audience.newBuilder()
                                           .setLocationOptIn(true)
                                           .build();

        Audience requiresOptedOut = Audience.newBuilder()
                                            .setLocationOptIn(false)
                                            .build();

        // LocationManager returns false by default
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedIn));
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedOut));

        when(locationClient.isOptIn()).thenReturn(true);
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedIn));
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedOut));
    }

    @Test
    public void testRequiresAnalyticsTrue() {
        Audience audience = Audience.newBuilder()
                                    .setRequiresAnalytics(true)
                                    .build();


        privacyManager.enable(PrivacyManager.FEATURE_ANALYTICS);
        assertTrue(AudienceChecks.checkAudience(context, audience));

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);
        assertFalse(AudienceChecks.checkAudience(context, audience));
    }

    @Test
    public void testRequiresAnalyticsFalse() {
        Audience audience = Audience.newBuilder()
                                    .setRequiresAnalytics(false)
                                    .build();


        privacyManager.enable(PrivacyManager.FEATURE_ANALYTICS);
        assertTrue(AudienceChecks.checkAudience(context, audience));

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);
        assertTrue(AudienceChecks.checkAudience(context, audience));
    }

    @Test
    public void testNewUser() {
        Audience requiresNewUser = Audience.newBuilder()
                                           .setNewUser(true)
                                           .build();

        Audience requiresExistingUser = Audience.newBuilder()
                                                .setNewUser(false)
                                                .build();

        assertFalse(AudienceChecks.checkAudienceForScheduling(context, requiresNewUser, false));
        assertTrue(AudienceChecks.checkAudienceForScheduling(context, requiresExistingUser, false));
        assertTrue(AudienceChecks.checkAudienceForScheduling(context, requiresNewUser, true));
        assertFalse(AudienceChecks.checkAudienceForScheduling(context, requiresExistingUser, true));
    }

    @Test
    public void testTestDevices() {
        byte[] bytes = Arrays.copyOf(UAStringUtil.sha256Digest("test channel"), 16);
        String testDevice = Base64.encodeToString(bytes, Base64.DEFAULT);

        Audience testDeviceAudience = Audience.newBuilder()
                                              .addTestDevice(testDevice)
                                              .build();

        Audience someOtherTestDeviceAudience = Audience.newBuilder()
                                                       .addTestDevice(UAStringUtil.sha256("some other channel"))
                                                       .build();

        when(airshipChannel.getId()).thenReturn("test channel");

        assertTrue(AudienceChecks.checkAudienceForScheduling(context, testDeviceAudience, false));
        assertFalse(AudienceChecks.checkAudienceForScheduling(context, someOtherTestDeviceAudience, false));
    }

    @Test
    public void testTagSelector() {
        final Set<String> tags = new HashSet<>();
        when(airshipChannel.getTags()).then(new Answer<Set<String>>() {
            @Override
            public Set<String> answer(InvocationOnMock invocation) {
                return tags;
            }
        });

        Audience audience = Audience.newBuilder()
                                    .setTagSelector(TagSelector.tag("expected tag"))
                                    .build();

        assertFalse(AudienceChecks.checkAudience(context, audience));

        tags.add("expected tag");
        assertTrue(AudienceChecks.checkAudience(context, audience));
    }

    @Test
    public void testTagSelectorWithGroups() {
        final Set<String> tags = new HashSet<>();
        when(airshipChannel.getTags()).then(new Answer<Set<String>>() {
            @Override
            public Set<String> answer(InvocationOnMock invocation) {
                return tags;
            }
        });

        Audience audience = Audience.newBuilder()
                                    .setTagSelector(TagSelector.tag("expected tag", "expected group"))
                                    .build();

        Map<String, Set<String>> tagGroups = new HashMap<>();

        assertFalse(AudienceChecks.checkAudience(context, audience, tagGroups));

        tagGroups.put("expected group", tagSet("expected tag"));
        assertTrue(AudienceChecks.checkAudience(context, audience, tagGroups));
    }

    @Test
    @Config
    public void testLocales() {
        /*
         * No easy way to set the locale in robolectric from the default `en-US`.
         * https://github.com/robolectric/robolectric/issues/3282
         */
        Audience audience = Audience.newBuilder()
                                    .addLanguageTag("en-US")
                                    .build();

        assertTrue(AudienceChecks.checkAudience(context, audience));

        audience = Audience.newBuilder()
                           .addLanguageTag("en")
                           .build();

        assertTrue(AudienceChecks.checkAudience(context, audience));

        audience = Audience.newBuilder()
                           .addLanguageTag("fr")
                           .build();

        assertFalse(AudienceChecks.checkAudience(context, audience));
    }

    @Test
    public void testAppVersion() {
        Audience audience = Audience.newBuilder()
                                    .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 2.0))
                                    .build();

        when(applicationMetrics.getCurrentAppVersion()).thenReturn(1l);
        assertTrue(AudienceChecks.checkAudience(context, audience));

        when(applicationMetrics.getCurrentAppVersion()).thenReturn(2l);
        assertTrue(AudienceChecks.checkAudience(context, audience));

        when(applicationMetrics.getCurrentAppVersion()).thenReturn(3l);
        assertFalse(AudienceChecks.checkAudience(context, audience));
    }

    @Test
    public void testSanitizeLocalesInChecks() {
        Audience audience = Audience.newBuilder()
                                    .addLanguageTag("en-")
                                    .addLanguageTag("en_")
                                    .addLanguageTag("en")
                                    .addLanguageTag("-")
                                    .addLanguageTag("_")
                                    .addLanguageTag("")
                                    .build();

        assertTrue(AudienceChecks.checkAudience(context, audience));
    }
}
