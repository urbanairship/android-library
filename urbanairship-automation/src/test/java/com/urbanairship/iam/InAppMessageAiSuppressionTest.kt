/* Copyright Airship and Contributors */
package com.urbanairship.iam

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ai.Evaluation
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.ai.EvaluationResult
import com.urbanairship.ai.InternalAirshipAi
import com.urbanairship.android.layout.analytics.events.LayoutResolutionEvent
import com.urbanairship.android.layout.assets.AirshipCachedAssets
import com.urbanairship.android.layout.assets.AssetCacheManager
import com.urbanairship.automation.AutomationAiSuppression
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.engine.DelegatePreparerResult
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.adapter.DisplayAdapter
import com.urbanairship.iam.adapter.DisplayAdapterFactory
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.coordinator.DisplayCoordinator
import com.urbanairship.iam.coordinator.DisplayCoordinatorManager
import com.urbanairship.json.jsonMapOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Suppression fails open at every step: only an explicit `allow = false` holds a message back.
 * Anything else — no config, no manager, a skipped or failed evaluation — shows it, so a model
 * that is absent or confused can't silently stop a campaign.
 */
@RunWith(AndroidJUnit4::class)
public class InAppMessageAiSuppressionTest {

    private val assetsManager: AssetCacheManager = mockk()
    private val adapterFactory: DisplayAdapterFactory = mockk()
    private val coordinatorManager: DisplayCoordinatorManager = mockk()
    private val analytics: InAppMessageAnalyticsInterface = mockk(relaxed = true)
    private val analyticsFactory: InAppMessageAnalyticsFactory = mockk(relaxed = true) {
        coEvery { makeAnalytics(any(), any()) } returns analytics
    }
    private val ai = FakeAi()

    private val message = InAppMessage(
        name = "Spring promo",
        extras = jsonMapOf("campaign" to "spring"),
        displayContent = InAppMessageDisplayContent.BannerContent(
            Banner(placement = Banner.Placement.BOTTOM, template = Banner.Template.MEDIA_LEFT)
        )
    )

    private val preparer = InAppMessageAutomationPreparer(
        assetsManager, coordinatorManager, adapterFactory, analyticsFactory, ai = ai
    )

    private fun scheduleInfo(
        suppression: AutomationAiSuppression? = AutomationAiSuppression(
            condition = "the user rents trucks",
            subjectHints = mapOf("surface" to "home")
        )
    ) = PreparedScheduleInfo(
        scheduleId = UUID.randomUUID().toString(),
        triggerSessionId = UUID.randomUUID().toString(),
        priority = 3,
        aiSuppression = suppression
    )

    private fun stubDisplayPath() {
        coEvery { assetsManager.cacheAsset(any(), any()) } returns
                Result.success(mockk<AirshipCachedAssets>())
        every { coordinatorManager.displayCoordinator(any()) } returns mockk<DisplayCoordinator>()
        every { adapterFactory.makeAdapter(any(), any(), any(), any()) } returns
                Result.success(mockk<DisplayAdapter>())
    }

    @Test
    public fun testSuppressesOnlyWhenTheModelDisallows(): TestResult = runTest {
        ai.result = EvaluationResult.Completed(
            InAppMessageSuppressionOutput(allow = false, reason = "not a renter")
        )

        val result = preparer.prepare(message, scheduleInfo()).getOrThrow()

        assertTrue(result is DelegatePreparerResult.Skip)

        // Reported as its own resolution, distinct from the app-side suppression.
        val event = slot<LayoutResolutionEvent>()
        verify { analytics.recordEvent(capture(event), null) }
        assertEquals(
            LayoutResolutionEvent.aiSuppressed().data?.toJsonValue(),
            event.captured.data?.toJsonValue()
        )
    }

    @Test
    public fun testMissBehaviorComesFromTheConfig(): TestResult = runTest {
        ai.result = EvaluationResult.Completed(
            InAppMessageSuppressionOutput(allow = false, reason = "no")
        )

        val result = preparer.prepare(
            message,
            scheduleInfo(
                AutomationAiSuppression(
                    condition = "x",
                    missBehavior = AutomationAudience.MissBehavior.CANCEL
                )
            )
        ).getOrThrow()

        assertTrue(result is DelegatePreparerResult.Cancel)
    }

    @Test
    public fun testAllowShowsTheMessage(): TestResult = runTest {
        stubDisplayPath()
        ai.result = EvaluationResult.Completed(
            InAppMessageSuppressionOutput(allow = true, reason = "renter")
        )

        val result = preparer.prepare(message, scheduleInfo()).getOrThrow()

        assertTrue(result is DelegatePreparerResult.Prepared)
    }

    @Test
    public fun testSkippedAndFailedShowTheMessage(): TestResult = runTest {
        listOf(
            EvaluationResult.Skipped("no model"),
            EvaluationResult.Failed(IllegalStateException("boom"))
        ).forEach { outcome ->
            stubDisplayPath()
            ai.result = outcome

            val result = preparer.prepare(message, scheduleInfo()).getOrThrow()

            assertTrue("$outcome", result is DelegatePreparerResult.Prepared)
        }
    }

    @Test
    public fun testNoConfigNeverAsksTheModel(): TestResult = runTest {
        stubDisplayPath()

        val result = preparer.prepare(message, scheduleInfo(suppression = null)).getOrThrow()

        assertTrue(result is DelegatePreparerResult.Prepared)
        assertNull(ai.evaluation)
    }

    @Test
    public fun testEmptyConditionNeverAsksTheModel(): TestResult = runTest {
        stubDisplayPath()

        val result = preparer.prepare(
            message,
            scheduleInfo(AutomationAiSuppression(condition = ""))
        ).getOrThrow()

        assertTrue(result is DelegatePreparerResult.Prepared)
        assertNull(ai.evaluation)
    }

    private class FakeAi : InternalAirshipAi {
        var result: EvaluationResult<*> = EvaluationResult.Skipped("unset")

        var evaluation: Evaluation<*, *>? = null
            private set

        @Suppress("UNCHECKED_CAST")
        override suspend fun <Output, Subject> evaluate(
            evaluation: Evaluation<Output, Subject>,
            additionalContext: EvaluationContext
        ): EvaluationResult<Output> {
            this.evaluation = evaluation
            return result as EvaluationResult<Output>
        }

        override val defaultModel: com.urbanairship.ai.ModelAdapter? = null
        override fun model(usage: com.urbanairship.ai.Usage<*>) = null
        override fun gatedModel(usage: com.urbanairship.ai.Usage<*>) = null
        override fun <Subject> setContextProvider(
            usage: com.urbanairship.ai.Usage<Subject>,
            provider: com.urbanairship.ai.EvaluationContextProvider<Subject>?
        ) = Unit
        override fun setDefaultContextProvider(
            provider: com.urbanairship.ai.DefaultEvaluationContextProvider?
        ) = Unit
        override fun setEvaluationObserver(observer: com.urbanairship.ai.EvaluationObserver?) = Unit
        override fun setModelResolver(resolver: com.urbanairship.ai.ModelResolver?) = Unit
        override fun registerModelFactory(factory: () -> com.urbanairship.ai.ModelAdapter) = Unit
        override suspend fun <Subject> fetchContext(
            usage: com.urbanairship.ai.Usage<Subject>,
            subject: Subject
        ): EvaluationContext = EvaluationContext.EMPTY
    }
}

private typealias InAppMessageSuppressionOutput =
        com.urbanairship.iam.ai.InAppMessageSuppressionEvaluation.Output
