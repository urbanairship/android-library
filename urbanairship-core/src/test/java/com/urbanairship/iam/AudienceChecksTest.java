/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.util.Base64;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.urbanairship.iam.tags.TestUtils.tagSet;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AudienceChecks} tests.
 */
public class AudienceChecksTest extends BaseTestCase {

    private AirshipChannel airshipChannel;
    private PushManager pushManager;
    private UALocationManager locationManager;
    private Context context;
    private ApplicationMetrics applicationMetrics;

    @Before
    public void setup() {
        airshipChannel = mock(AirshipChannel.class);
        pushManager = mock(PushManager.class);
        locationManager = mock(UALocationManager.class);
        applicationMetrics = mock(ApplicationMetrics.class);
        context = TestApplication.getApplication();

        TestApplication.getApplication().setChannel(airshipChannel);
        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setLocationManager(locationManager);
        TestApplication.getApplication().setApplicationMetrics(applicationMetrics);
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

        when(locationManager.isOptIn()).thenReturn(true);
        assertTrue(AudienceChecks.checkAudience(context, requiresOptedIn));
        assertFalse(AudienceChecks.checkAudience(context, requiresOptedOut));
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
            public Set<String> answer(InvocationOnMock invocation) throws Throwable {
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
            public Set<String> answer(InvocationOnMock invocation) throws Throwable {
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

        when(applicationMetrics.getCurrentAppVersion()).thenReturn(1);
        assertTrue(AudienceChecks.checkAudience(context, audience));

        when(applicationMetrics.getCurrentAppVersion()).thenReturn(2);
        assertTrue(AudienceChecks.checkAudience(context, audience));

        when(applicationMetrics.getCurrentAppVersion()).thenReturn(3);
        assertFalse(AudienceChecks.checkAudience(context, audience));
    }

}