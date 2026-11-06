/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.ScoreControllerInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ScoreControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { modelScope } returns testScope
        every { layoutState } returns LayoutState.EMPTY
    }

    private val formState = SharedState(
        State.Form(
            identifier = "form-id",
            formType = FormType.Form,
            formResponseType = "form",
            validationMode = FormValidationMode.IMMEDIATE,
            initialChildrenValues = emptyMap()
        )
    )

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testContainerGivesKeyFocusToItsOptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = List(5) { View(context).apply { isFocusable = true } }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            options.forEach { addView(it, 100, 100) }
            measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 500, 100)
        }

        ScoreController(
            viewInfo = mockk<ScoreControllerInfo>(relaxed = true) {
                every { identifier } returns IDENTIFIER
            },
            view = mockk(relaxed = true),
            formState = ThomasForm(formState),
            scoreState = SharedState(State.Score(identifier = IDENTIFIER)),
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        ).onViewAttached(container)

        // Still a TalkBack stop, but D-pad and keyboard focus go straight to the options.
        assertTrue(container.isFocusable)
        assertFalse(container.getFocusables(View.FOCUS_RIGHT).contains(container))
        assertTrue(container.requestFocus())
        assertTrue(options.first().isFocused)
    }

    private companion object {
        private const val IDENTIFIER = "score"
    }
}
