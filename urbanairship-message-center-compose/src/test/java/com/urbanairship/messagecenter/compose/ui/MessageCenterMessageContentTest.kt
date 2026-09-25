/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.compose.ui

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.Airship
import com.urbanairship.R as CoreR
import com.urbanairship.messagecenter.User
import com.urbanairship.messagecenter.compose.createMessage
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.Content
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.WebViewState
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterColors
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme
import com.urbanairship.messagecenter.messageCenter
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageCenterMessageContentTest {

    @get:Rule
    public val composeRule: ComposeContentTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val message = createMessage("message-id")
    private val actions = mutableListOf<Action>()

    private val progressIndicator = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Before
    public fun setUp() {
        // MessageWebView.loadMessage reads the user from the Airship singleton.
        mockkObject(Airship)
        every { Airship.messageCenter } returns mockk {
            every { user } returns mockk<User>(relaxed = true)
        }
    }

    @After
    public fun tearDown() {
        unmockkAll()
    }

    @Test
    public fun loadingStateShowsProgress() {
        setContent(State.Loading("message-id"))

        composeRule.onNode(progressIndicator).assertExists()
    }

    @Test
    public fun htmlContentReportsPageStartedAfterFirstComposition() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.INIT)))

        assertThat(actions).contains(Action.UpdateWebViewState(WebViewState.LOADING))
    }

    @Test
    public fun htmlInitShowsProgressOverlay() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.INIT)))

        composeRule.onNode(progressIndicator).assertExists()
    }

    @Test
    public fun htmlLoadingShowsProgressOverlay() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.LOADING)))

        composeRule.onNode(progressIndicator).assertExists()
    }

    @Test
    public fun htmlLoadedShowsNoOverlay() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.LOADED)))

        composeRule.onNode(progressIndicator).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(CoreR.string.ua_mc_failed_to_load)).assertDoesNotExist()
    }

    @Test
    public fun htmlErrorShowsErrorViewWithRetry() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.ERROR)))

        composeRule.onNode(progressIndicator).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(CoreR.string.ua_mc_failed_to_load)).assertExists()

        composeRule.onNodeWithText(context.getString(CoreR.string.ua_retry_button).uppercase()).performClick()
        assertThat(actions).contains(Action.Refresh)
    }

    private fun setContent(viewState: State) {
        val state = MessageCenterMessageState(
            onAction = { actions.add(it) },
            makeAnalytics = { _, _ -> mockk() },
        )
        state.viewState = viewState

        composeRule.setContent {
            MessageCenterTheme(colors = MessageCenterColors.lightDefaults()) {
                MessageCenterMessage(state = state, onClose = {})
            }
        }
        composeRule.waitForIdle()
    }
}
