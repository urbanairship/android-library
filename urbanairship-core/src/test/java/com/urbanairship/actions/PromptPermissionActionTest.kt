/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Handler
import android.os.Looper
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action.Situation
import com.urbanairship.json.jsonMapOf
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionPromptFallback
import com.urbanairship.permission.PermissionRequestResult
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PromptPermissionActionTest {

    private val mockPermissionManager: PermissionsManager = mockk()
    private val action = PromptPermissionAction(
        permissionsManagerProvider = { mockPermissionManager }
    )

    @Test
    public fun testAcceptsArgumentsSituations() {

        val value = jsonMapOf(
            PromptPermissionAction.PERMISSION_ARG_KEY to Permission.LOCATION
        )

        //should accept the situations below
        listOf(
            Situation.PUSH_OPENED,
            Situation.MANUAL_INVOCATION,
            Situation.WEB_VIEW_INVOCATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.AUTOMATION
        ).forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, value)
            assertTrue(
                "Should accept arguments in situation $situation", action.acceptsArguments(args)
            )
        }

        // should reject the situations below
        listOf(
            Situation.PUSH_RECEIVED,
            Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON
        ).forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, value)
            assertFalse(
                "Should reject arguments in situation $situation", action.acceptsArguments(args)
            )
        }
    }

    @Test
    public fun testPermissions() {

        coEvery { mockPermissionManager.suspendingCheckPermissionStatus(any()) } returns PermissionStatus.DENIED
        coEvery { mockPermissionManager.suspendingRequestPermission(any(), any(), any()) } returns PermissionRequestResult.granted()

        Permission.entries.forEach { permission ->
            val value = jsonMapOf(
                PromptPermissionAction.PERMISSION_ARG_KEY to permission
            )

            val actionArguments = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, value)
            action.perform(actionArguments)

            coVerify { mockPermissionManager.suspendingCheckPermissionStatus(permission) }
            coVerify { mockPermissionManager.suspendingRequestPermission(permission, false, PermissionPromptFallback.None) }
        }
    }

    @Test
    public fun testEnableAirshipUsage() {
        coEvery { mockPermissionManager.suspendingCheckPermissionStatus(any()) } returns PermissionStatus.DENIED
        coEvery { mockPermissionManager.suspendingRequestPermission(any(), any(), any()) } returns PermissionRequestResult.granted()

        val value = jsonMapOf(
            PromptPermissionAction.PERMISSION_ARG_KEY to Permission.DISPLAY_NOTIFICATIONS,
            PromptPermissionAction.ENABLE_AIRSHIP_USAGE_ARG_KEY to true
        )

        val actionArguments = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, value)
        action.perform(actionArguments)

        coVerify { mockPermissionManager.suspendingRequestPermission(
            permission = Permission.DISPLAY_NOTIFICATIONS,
            enableAirshipUsageOnGrant = true,
            fallback = PermissionPromptFallback.None)
        }
    }

    @Test
    public fun testResult() {
        coEvery {
            mockPermissionManager.suspendingCheckPermissionStatus(Permission.DISPLAY_NOTIFICATIONS)
        } returns PermissionStatus.DENIED

        coEvery {
            mockPermissionManager.suspendingRequestPermission(
                permission = Permission.DISPLAY_NOTIFICATIONS,
                enableAirshipUsageOnGrant = false,
                fallback = PermissionPromptFallback.None
            )
        } returns PermissionRequestResult.granted()

        val handler = Handler(Looper.getMainLooper())
        val resultReceiver = TestReceiver(handler)

        val value = jsonMapOf(
            PromptPermissionAction.PERMISSION_ARG_KEY to Permission.DISPLAY_NOTIFICATIONS
        )

        val metadata = bundleOf(
            PromptPermissionAction.RECEIVER_METADATA to resultReceiver
        )

        val actionArguments =
            ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, value, metadata)
        action.perform(actionArguments)

        assertEquals(PermissionStatus.DENIED, resultReceiver.before)
        assertEquals(PermissionStatus.GRANTED, resultReceiver.after)
        assertEquals(Permission.DISPLAY_NOTIFICATIONS, resultReceiver.permission)
    }

    internal class TestReceiver(handler: Handler) : PermissionResultReceiver(handler) {

        var permission: Permission? = null
        var before: PermissionStatus? = null
        var after: PermissionStatus? = null

        override fun onResult(
            permission: Permission, before: PermissionStatus, after: PermissionStatus
        ) {
            this.permission = permission
            this.before = before
            this.after = after
        }
    }
}
