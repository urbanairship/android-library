package com.urbanairship.actions;


import android.Manifest;
import android.content.Context;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.PermissionsRequester;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static android.support.v4.content.PermissionChecker.PERMISSION_DENIED;
import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class EnableFeatureActionTest extends BaseTestCase {

    private EnableFeatureAction action;
    private @Action.Situation int[] acceptedSituations;
    private @Action.Situation int[] rejectedSituations;
    private PushManager pushManager;
    private UALocationManager locationManager;

    private PermissionsRequester permissionsRequester;

    @Before
    public void setup() {

        permissionsRequester = mock(PermissionsRequester.class);
        action = new EnableFeatureAction(permissionsRequester);

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

        pushManager = mock(PushManager.class);
        locationManager = mock(UALocationManager.class);

        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setLocationManager(locationManager);
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
        verify(pushManager).setUserNotificationsEnabled(true);

        assertTrue(result.getValue().getBoolean(false));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_LOCATION}.
     */
    @Test
    public void testEnableLocation() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] {PERMISSION_GRANTED, PERMISSION_GRANTED});

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_LOCATION);
        ActionResult result = action.perform(args);
        verify(locationManager).setLocationUpdatesEnabled(true);
        assertTrue(result.getValue().getBoolean(false));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_LOCATION} when the permission is denied.
     */
    @Test
    public void testEnableLocationPermissionDenied() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] {PERMISSION_DENIED, PERMISSION_DENIED});

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_LOCATION);
        ActionResult result = action.perform(args);
        verifyZeroInteractions(locationManager);
        assertFalse(result.getValue().getBoolean(true));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_BACKGROUND_LOCATION}.
     */
    @Test
    public void testEnableBackgroundLocation() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] {PERMISSION_GRANTED, PERMISSION_GRANTED});

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_BACKGROUND_LOCATION);
        ActionResult result = action.perform(args);
        verify(locationManager).setLocationUpdatesEnabled(true);
        verify(locationManager).setBackgroundLocationAllowed(true);

        assertTrue(result.getValue().getBoolean(false));
    }

    /**
     * Test enabling {@link EnableFeatureAction#FEATURE_BACKGROUND_LOCATION} when the permission is denied.
     */
    @Test
    public void testEnableBackgroundLocationPermissionDenied() {
        when(permissionsRequester.requestPermissions(any(Context.class), eq(Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))))
                .thenReturn(new int[] {PERMISSION_DENIED, PERMISSION_DENIED});

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_BACKGROUND_LOCATION);
        ActionResult result = action.perform(args);
        verifyZeroInteractions(locationManager);
        assertFalse(result.getValue().getBoolean(true));
    }

}