/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

/**
 * {@link AudienceChecks} tests.
 */
public class AudienceChecksTest extends BaseTestCase {

    private PushManager pushManager;
    private UALocationManager locationManager;
    private Context context;
    private PackageInfo packageInfo;

    @Before
    public void setup() {
        pushManager = mock(PushManager.class);
        locationManager = mock(UALocationManager.class);
        context = TestApplication.getApplication();

        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setLocationManager(locationManager);

        packageInfo = new PackageInfo();
        packageInfo.packageName = "com.urbanairship";
        packageInfo.versionName = "2.0";
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = "com.urbanairship";
        packageInfo.applicationInfo.name = "com.urbanairship";

        ShadowPackageManager shadowPackageManager = shadowOf(RuntimeEnvironment.application.getPackageManager());
        shadowPackageManager.addPackage(packageInfo);
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

        when(pushManager.isOptIn()).thenReturn(true);
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

        when(locationManager.isLocationUpdatesEnabled()).thenReturn(true);
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

        assertFalse(AudienceChecks.checkAudience(context, requiresNewUser, false));
        assertTrue(AudienceChecks.checkAudience(context, requiresExistingUser, false));
        assertTrue(AudienceChecks.checkAudience(context, requiresNewUser, true));
        assertFalse(AudienceChecks.checkAudience(context, requiresExistingUser, true));
    }

    @Test
    public void testTagSelector() {
        final Set<String> tags = new HashSet<>();
        when(pushManager.getTags()).then(new Answer<Set<String>>() {
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


        packageInfo.versionCode = 1;
        assertTrue(AudienceChecks.checkAudience(context, audience));

        packageInfo.versionCode = 2;
        assertTrue(AudienceChecks.checkAudience(context, audience));

        packageInfo.versionCode = 3;
        assertFalse(AudienceChecks.checkAudience(context, audience));
    }
}