/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionsManager;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class EnableFeatureActionTest extends BaseTestCase {

    private final AirshipLocationClient mockLocation = mock(AirshipLocationClient.class);
    private final PermissionsManager mockPermissionManager = mock(PermissionsManager.class);
    private final EnableFeatureAction action = new EnableFeatureAction(() -> mockPermissionManager, () -> mockLocation);

    @Test
    public void testLocation() throws JsonException {
        ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_LOCATION);
        assertTrue(action.acceptsArguments(arguments));

        PromptPermissionAction.Args args = action.parseArg(arguments);
        assertEquals(Permission.LOCATION, args.permission);
        assertTrue(args.enableAirshipUsage);
        assertTrue(args.fallbackSystemSettings);
    }

    @Test
    public void testBackgroundLocation() throws JsonException {
        ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_BACKGROUND_LOCATION);
        assertTrue(action.acceptsArguments(arguments));

        PromptPermissionAction.Args args = action.parseArg(arguments);
        assertEquals(Permission.LOCATION, args.permission);
        assertTrue(args.enableAirshipUsage);
        assertTrue(args.fallbackSystemSettings);

        action.onStart(arguments);
        verify(mockLocation).setBackgroundLocationAllowed(true);
    }

    @Test
    public void testUserNotifications() throws JsonException {
        ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, EnableFeatureAction.FEATURE_USER_NOTIFICATIONS);
        assertTrue(action.acceptsArguments(arguments));

        PromptPermissionAction.Args args = action.parseArg(arguments);
        assertEquals(Permission.POST_NOTIFICATIONS, args.permission);
        assertTrue(args.enableAirshipUsage);
        assertTrue(args.fallbackSystemSettings);
    }
}
