/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionsManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class EnableFeatureActionTest {

    private val mockPermissionManager: PermissionsManager = mockk()
    private val action = EnableFeatureAction(
        permissionsManagerSupplier = { mockPermissionManager }
    )

    @Test
    public fun testLocation() {
        val arguments = ActionTestUtils.createArgs(
            Action.Situation.MANUAL_INVOCATION, EnableFeatureAction.FEATURE_LOCATION
        )
        assertTrue(action.acceptsArguments(arguments))

        val args = action.parseArg(arguments)
        assertEquals(Permission.LOCATION, args.permission)
        assertTrue(args.enableAirshipUsage)
        assertTrue(args.fallbackSystemSettings)
    }

    @Test
    public fun testBackgroundLocation() {
        val arguments = ActionTestUtils.createArgs(
            Action.Situation.MANUAL_INVOCATION, EnableFeatureAction.FEATURE_BACKGROUND_LOCATION
        )
        assertTrue(action.acceptsArguments(arguments))

        val args = action.parseArg(arguments)
        assertEquals(Permission.LOCATION, args.permission)
        assertTrue(args.enableAirshipUsage)
        assertTrue(args.fallbackSystemSettings)

        action.onStart(arguments)
    }

    @Test
    public fun testUserNotifications() {
        val arguments = ActionTestUtils.createArgs(
            Action.Situation.MANUAL_INVOCATION, EnableFeatureAction.FEATURE_USER_NOTIFICATIONS
        )
        assertTrue(action.acceptsArguments(arguments))

        val args = action.parseArg(arguments)
        assertEquals(Permission.DISPLAY_NOTIFICATIONS, args.permission)
        assertTrue(args.enableAirshipUsage)
        assertTrue(args.fallbackSystemSettings)
    }
}
