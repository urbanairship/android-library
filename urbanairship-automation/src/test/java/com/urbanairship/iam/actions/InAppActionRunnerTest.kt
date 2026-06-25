/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.AddCustomEventAction
import com.urbanairship.actions.PermissionResultReceiver
import com.urbanairship.actions.PromptPermissionAction
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.iam.analytics.InAppCustomEventContext
import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.android.layout.analytics.events.LayoutPermissionResultEvent
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                LayoutPermissionResultEvent(
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
    public fun testTrackPermissionResultWithLayoutData(): TestResult = runTest {
        val metadataSlot = slot<Bundle>()
        val mockRequest: ActionRunRequest = mockk {
            every { setValue(JsonValue.wrap("bar")) } returns this
            every { setSituation(Action.Situation.AUTOMATION) } returns this
            every { setMetadata(capture(metadataSlot)) } returns this
            coEvery { runSuspending() } returns mockk(relaxed = true)
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
                LayoutPermissionResultEvent(
                    permission = Permission.LOCATION,
                    startingStatus = PermissionStatus.GRANTED,
                    endingStatus = PermissionStatus.DENIED
                ),
                layoutData
            )
        }
    }

    @Test
    public fun testInAppContext(): TestResult = runTest {
        val metadataSlot = slot<Bundle>()
        val mockRequest: ActionRunRequest = mockk {
            every { setValue(JsonValue.wrap("bar")) } returns this
            every { setSituation(Action.Situation.AUTOMATION) } returns this
            every { setMetadata(capture(metadataSlot)) } returns this
            coEvery { runSuspending() } returns mockk(relaxed = true)
        }

        val context = InAppCustomEventContext(
            id = LayoutEventMessageId.AirshipId("foo", null),
            context = null
        )
        val layoutData: LayoutData = mockk()
        every { analytics.customEventContext(layoutData) } returns context

        val runner = InAppActionRunner(
            analytics = analytics,
            trackPermissionResults = true,
            actionRequestFactory = {
                mockRequest
            }
        )

        runner.run(mapOf("foo" to JsonValue.wrap("bar")), layoutData)

        val result = JsonValue.parseString(metadataSlot.captured.getString(AddCustomEventAction.IN_APP_CONTEXT_METADATA_KEY)!!)
        assertEquals(result, context.toJsonValue())
    }

    @Test
    public fun testRunAwaitsActionCompletion(): TestResult = runTest {
        // Production-path guarantee for MOBILE-5701: run(actions, state) must suspend until the
        // actions actually finish, so a caller (e.g. button dismiss) can sequence after them.
        val gate = CompletableDeferred<Unit>()
        val mockRequest: ActionRunRequest = mockk {
            every { setValue(JsonValue.wrap("bar")) } returns this
            every { setSituation(Action.Situation.AUTOMATION) } returns this
            every { setMetadata(any()) } returns this
            coEvery { runSuspending() } coAnswers {
                gate.await()
                mockk(relaxed = true)
            }
        }

        val layoutData: LayoutData = mockk()
        every { analytics.customEventContext(layoutData) } returns mockk(relaxed = true)

        val runner = InAppActionRunner(
            analytics = analytics,
            trackPermissionResults = false,
            actionRequestFactory = { mockRequest }
        )

        val job = launch {
            runner.run(mapOf("foo" to JsonValue.wrap("bar")), layoutData)
        }
        runCurrent()
        assertFalse("run() must not return while the action is still running", job.isCompleted)

        gate.complete(Unit)
        runCurrent()
        assertTrue("run() must return once the action completes", job.isCompleted)
    }
}
