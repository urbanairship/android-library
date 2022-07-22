/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;
import android.util.Base64;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestApplication;
import com.urbanairship.TestUtils;
import com.urbanairship.UAirship;
import com.urbanairship.automation.tags.TagSelector;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.json.JsonMatcher;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionDelegate;
import com.urbanairship.permission.PermissionRequestResult;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AudienceChecks} tests.
 */
@Config(
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class AudienceChecksTest {

    private Context context = ApplicationProvider.getApplicationContext();

    private UAirship airship = mock(UAirship.class);
    private AirshipChannel airshipChannel = mock(AirshipChannel.class);
    private PushManager pushManager = mock(PushManager.class);
    private ApplicationMetrics applicationMetrics = mock(ApplicationMetrics.class);
    private PermissionsManager permissionsManager = PermissionsManager.newPermissionsManager(context);
    private PreferenceDataStore dataStore = PreferenceDataStore.inMemoryStore(context);
    private PrivacyManager privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);

    @Before
    public void setup() {
        when(airship.getChannel()).thenReturn(airshipChannel);
        when(airship.getPushManager()).thenReturn(pushManager);
        when(airship.getApplicationMetrics()).thenReturn(applicationMetrics);
        when(airship.getPrivacyManager()).thenReturn(privacyManager);
        when(airship.getPermissionsManager()).thenReturn(permissionsManager);
        TestUtils.setAirshipInstance(airship);
    }

    @After
    public void tearDown() {
        TestUtils.setAirshipInstance(null);
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
    public void testLocationOptInRequired() {
        TestPermissionsDelegate locationDelegate = new TestPermissionsDelegate();

        Audience requiresOptedIn = Audience.newBuilder()
                                           .setLocationOptIn(true)
                                           .build();

        // Not set
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedIn));

        permissionsManager.setPermissionDelegate(Permission.LOCATION, locationDelegate);

        // Denied
        locationDelegate.status = PermissionStatus.DENIED;
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedIn));

        // Not determined
        locationDelegate.status = PermissionStatus.NOT_DETERMINED;
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedIn));

        // Granted
        locationDelegate.status = PermissionStatus.GRANTED;
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedIn));
    }

    @Test
    public void testLocationOptOutRequired() {
        TestPermissionsDelegate locationDelegate = new TestPermissionsDelegate();

        Audience requiresOptedOut = Audience.newBuilder()
                                            .setLocationOptIn(false)
                                            .build();

        // Not set
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedOut));

        permissionsManager.setPermissionDelegate(Permission.LOCATION, locationDelegate);

        // Denied
        locationDelegate.status = PermissionStatus.DENIED;
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedOut));

        // Not determined
        locationDelegate.status = PermissionStatus.NOT_DETERMINED;
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedOut));

        // Granted
        locationDelegate.status = PermissionStatus.GRANTED;
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedOut));
    }

    @Test
    public void testPermissionsPredicate() {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                        .setKey(Permission.DISPLAY_NOTIFICATIONS.getValue())
                                                                        .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("granted")))
                                                                        .build())
                                               .build();

        Audience audienceChecks = Audience.newBuilder()
                                          .setPermissionsPredicate(predicate)
                                          .build();


        // Not set
        assertFalse(AudienceChecks.checkAudience(context, audienceChecks));

        TestPermissionsDelegate notificationDelegate = new TestPermissionsDelegate();
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, notificationDelegate);

        // Denied
        notificationDelegate.status = PermissionStatus.DENIED;
        assertFalse(AudienceChecks.checkAudience(context, audienceChecks));

        // Not determined
        notificationDelegate.status = PermissionStatus.NOT_DETERMINED;
        assertFalse(AudienceChecks.checkAudience(context, audienceChecks));

        // Granted
        notificationDelegate.status = PermissionStatus.GRANTED;
        assertTrue(AudienceChecks.checkAudience(context, audienceChecks));
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
        assertTrue(AudienceChecks.checkAudience(context, testDeviceAudience));
        assertFalse(AudienceChecks.checkAudienceForScheduling(context, someOtherTestDeviceAudience, false));
        assertFalse(AudienceChecks.checkAudience(context, someOtherTestDeviceAudience));
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

    private static class TestPermissionsDelegate implements PermissionDelegate {

        private PermissionStatus status = PermissionStatus.NOT_DETERMINED;

        @Override
        public void checkPermissionStatus(@NonNull Context context, @NonNull Consumer<PermissionStatus> callback) {
            callback.accept(status);
        }

        @Override
        public void requestPermission(@NonNull Context context, @NonNull Consumer<PermissionRequestResult> callback) {
            Assert.fail();
        }

    }

}
