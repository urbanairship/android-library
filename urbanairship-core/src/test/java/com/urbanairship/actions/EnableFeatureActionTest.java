package com.urbanairship.actions;

import android.Manifest;
import android.content.Context;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.base.Supplier;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.PermissionsRequester;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.Nullable;

import static androidx.core.content.PermissionChecker.PERMISSION_DENIED;
import static androidx.core.content.PermissionChecker.PERMISSION_GRANTED;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class EnableFeatureActionTest extends BaseTestCase {

    private EnableFeatureAction action;
    private @Action.Situation
    int[] acceptedSituations;
    private @Action.Situation
    int[] rejectedSituations;
    private PushManager mockPush;
    private AirshipLocationClient mockLocation;
    private PrivacyManager privacyManager;
    private UAirship mockShip;

    private PermissionsRequester permissionsRequester;

    @Before
    public void setup() {
        mockPush = mock(PushManager.class);
        mockLocation = mock(AirshipLocationClient.class);
        privacyManager = new PrivacyManager(PreferenceDataStore.inMemoryStore(TestApplication.getApplication()), PrivacyManager.FEATURE_NONE);

        mockShip = mock(UAirship.class);
        when(mockShip.getPushManager()).thenReturn(mockPush);
        when(mockShip.getLocationClient()).thenReturn(mockLocation);
        when(mockShip.getPrivacyManager()).thenReturn(privacyManager);

        permissionsRequester = mock(PermissionsRequester.class);
        action = new EnableFeatureAction(permissionsRequester, new Supplier<UAirship>() {
            @Nullable
            @Override
            public UAirship get() {
                return mockShip;
            }
        });

        acceptedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        // Rejected situations (All - accepted)
        rejectedSituations = new int[] {
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON
        };



    }

    /**
     * Test accepts arguments.
     */
    @Test
    public void testAcceptsArgumentWithString() {
        List<String> validValues = Arrays.asList(EnableFeatureAction.FEATURE_BACKGROUND_LOCATION, EnableFeatureAction.FEATURE_USER_NOTIFICATIONS, EnableFeatureAction.FEATURE_LOCATION);

        for (String value : validValues) {
            for (@Action.Situation int situation : acceptedSituations) {
                ActionArguments args = ActionTestUtils.createArgs(situation, value);
                assertTrue("Should accept arguments in situation " + situation,
                        action.acceptsArguments(args));
            }

            for (@Action.Situation int situation : rejectedSituations) {
                ActionArguments args = ActionTestUtils.createArgs(situation, value);
                assertFalse("Should reject arguments in situation " + situation,
                        action.acceptsArguments(args));
            }
        }

        // Verify it rejects invalid argument values
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_AUTOMATION, "invalid");
        assertFalse(action.acceptsArguments(args));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_USER_NOTIFICATIONS}.
     */
    @Test
    public void testEnableUserNotifications() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_USER_NOTIFICATIONS);
        ActionResult result = action.perform(args);
        verify(mockPush).setUserNotificationsEnabled(true);
        assertEquals(PrivacyManager.FEATURE_PUSH, privacyManager.getEnabledFeatures());

        assertTrue(result.getValue().getBoolean(false));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_LOCATION}.
     */
    @Test
    public void testEnableLocation() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] { PERMISSION_GRANTED, PERMISSION_GRANTED });

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_LOCATION);
        ActionResult result = action.perform(args);
        verify(mockLocation).setLocationUpdatesEnabled(true);
        assertEquals(PrivacyManager.FEATURE_LOCATION, privacyManager.getEnabledFeatures());
        assertTrue(result.getValue().getBoolean(false));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_LOCATION} when the permission is denied.
     */
    @Test
    public void testEnableLocationPermissionDenied() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] { PERMISSION_DENIED, PERMISSION_DENIED });

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_LOCATION);
        ActionResult result = action.perform(args);
        assertEquals(PrivacyManager.FEATURE_LOCATION, privacyManager.getEnabledFeatures());
        verifyNoInteractions(mockLocation);
        assertFalse(result.getValue().getBoolean(true));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_BACKGROUND_LOCATION}.
     */
    @Test
    public void testEnableBackgroundLocation() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] { PERMISSION_GRANTED, PERMISSION_GRANTED });

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_BACKGROUND_LOCATION);
        ActionResult result = action.perform(args);

        assertEquals(PrivacyManager.FEATURE_LOCATION, privacyManager.getEnabledFeatures());

        verify(mockLocation).setLocationUpdatesEnabled(true);
        verify(mockLocation).setBackgroundLocationAllowed(true);

        assertTrue(result.getValue().getBoolean(false));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_BACKGROUND_LOCATION} when the permission is denied.
     */
    @Test
    public void testEnableBackgroundLocationPermissionDenied() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] { PERMISSION_DENIED, PERMISSION_DENIED });

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_BACKGROUND_LOCATION);
        ActionResult result = action.perform(args);

        assertEquals(PrivacyManager.FEATURE_LOCATION, privacyManager.getEnabledFeatures());

        verifyNoInteractions(mockLocation);
        assertFalse(result.getValue().getBoolean(true));
    }

}
