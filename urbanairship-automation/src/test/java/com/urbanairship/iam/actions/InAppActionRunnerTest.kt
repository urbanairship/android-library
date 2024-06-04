/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.PermissionResultReceiver
import com.urbanairship.actions.PromptPermissionAction
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppPermissionResultEvent
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppActionRunnerTest {

    private val analytics: InAppMessageAnalyticsInterface = mockk(relaxed = true)

    @Test
    public fun testTrackPermissionResult() {
        val metadataSlot = slot<Bundle>()
        val mockRequest: ActionRunRequest = mockk {
            every { setValue(JsonValue.wrap("bar")) } returns this
            every { setMetadata(capture(metadataSlot)) } returns this
            every { this@mockk.run(any()) } just runs
        }

        val runner = InAppActionRunner(
            analytics = analytics,
            trackPermissionResults = true,
            actionRequestFactory = {
                mockRequest
            }
        )

        runner.run("foo", JsonValue.wrap("bar"))

        val receiver: PermissionResultReceiver = metadataSlot.captured.getParcelable(PromptPermissionAction.RECEIVER_METADATA)!!
        receiver.onResult(Permission.LOCATION, PermissionStatus.GRANTED, PermissionStatus.DENIED)

        verify {
            analytics.recordEvent(
                InAppPermissionResultEvent(
                    permission = Permission.LOCATION,
                    startingStatus = PermissionStatus.GRANTED,
                    endingStatus = PermissionStatus.DENIED
                ),
                null
            )
        }
    }

    @Test
    public fun testTrackPermissionResultDisabled() {
        val metadataSlot = slot<Bundle>()
        val mockRequest: ActionRunRequest = mockk {
            every { setValue(JsonValue.wrap("bar")) } returns this
            every { setMetadata(capture(metadataSlot)) } returns this
            every { this@mockk.run(any()) } just runs
        }

        val runner = InAppActionRunner(
            analytics = analytics,
            trackPermissionResults = false,
            actionRequestFactory = {
                mockRequest
            }
        )

        runner.run("foo", JsonValue.wrap("bar"))
        assertFalse(metadataSlot.captured.containsKey(PromptPermissionAction.RECEIVER_METADATA))
    }

    @Test
    public fun testTrackPermissionResultWithLayoutData() {
        val metadataSlot = slot<Bundle>()
        val mockRequest: ActionRunRequest = mockk {
            every { setValue(JsonValue.wrap("bar")) } returns this
            every { setSituation(Action.SITUATION_AUTOMATION) } returns this
            every { setMetadata(capture(metadataSlot)) } returns this
            every { this@mockk.run(any()) } just runs
        }

        val layoutData: LayoutData = mockk()

        val runner = InAppActionRunner(
            analytics = analytics,
            trackPermissionResults = true,
            actionRequestFactory = {
                mockRequest
            }
        )

        runner.run(mapOf("foo" to JsonValue.wrap("bar")), layoutData)

        val receiver: PermissionResultReceiver = metadataSlot.captured.getParcelable(PromptPermissionAction.RECEIVER_METADATA)!!
        receiver.onResult(Permission.LOCATION, PermissionStatus.GRANTED, PermissionStatus.DENIED)

        verify {
            analytics.recordEvent(
                InAppPermissionResultEvent(
                    permission = Permission.LOCATION,
                    startingStatus = PermissionStatus.GRANTED,
                    endingStatus = PermissionStatus.DENIED
                ),
                layoutData
            )
        }
    }
}
