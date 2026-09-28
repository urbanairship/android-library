/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.compose.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
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
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
public class MessageCenterMessageContentTest {

    @get:Rule
    public val composeRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity> =
        createAndroidComposeRule<ComponentActivity>()

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
        every { Airship.urlAllowList } returns mockk {
            every { isAllowed(any(), any()) } returns false
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
    public fun htmlErrorShowsErrorView() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.ERROR)))

        composeRule.onNode(progressIndicator).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(CoreR.string.ua_mc_failed_to_load)).assertExists()
    }

    @Test
    public fun htmlErrorRetryReloadsWebViewWithoutRefresh() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.ERROR)))
        assertThat(actions.count { it == Action.UpdateWebViewState(WebViewState.LOADING) }).isEqualTo(1)

        composeRule.onNodeWithText(context.getString(CoreR.string.ua_retry_button).uppercase()).performClick()
        composeRule.waitForIdle()

        assertThat(actions.count { it == Action.UpdateWebViewState(WebViewState.LOADING) }).isEqualTo(2)
        assertThat(actions).doesNotContain(Action.Refresh)
    }

    @Test
    public fun pageFinishedReportsLoadedAndMarksRead() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.INIT)))
        val webView = findWebView()

        composeRule.runOnUiThread {
            webView.webViewClient.onPageFinished(webView, message.bodyUrl)
        }

        assertThat(actions).contains(Action.UpdateWebViewState(WebViewState.LOADED))
        assertThat(actions).contains(Action.MarkCurrentMessageRead)
    }

    @Test
    public fun pageFinishedAfterMainFrameErrorKeepsError() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.INIT)))
        val webView = findWebView()

        composeRule.runOnUiThread { webView.failMainFrameLoad() }

        assertThat(actions).contains(Action.UpdateWebViewState(WebViewState.ERROR))
        assertThat(actions).doesNotContain(Action.UpdateWebViewState(WebViewState.LOADED))
        assertThat(actions).doesNotContain(Action.MarkCurrentMessageRead)
    }

    @Test
    public fun retryAfterPageErrorRecoversOnSuccessfulLoad() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.INIT)), applyWebViewState = true)
        val failedWebView = findWebView()

        composeRule.runOnUiThread { failedWebView.failMainFrameLoad() }
        composeRule.onNodeWithText(context.getString(CoreR.string.ua_mc_failed_to_load)).assertExists()

        composeRule.onNodeWithText(context.getString(CoreR.string.ua_retry_button).uppercase()).performClick()
        composeRule.waitForIdle()
        val retriedWebView = findWebView()
        assertThat(retriedWebView).isNotSameInstanceAs(failedWebView)
        composeRule.onNode(progressIndicator).assertExists()

        composeRule.runOnUiThread { retriedWebView.webViewClient.onPageFinished(retriedWebView, message.bodyUrl) }
        composeRule.waitForIdle()

        composeRule.onNode(progressIndicator).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(CoreR.string.ua_mc_failed_to_load)).assertDoesNotExist()
        assertThat(actions).contains(Action.MarkCurrentMessageRead)
    }

    @Test
    public fun retryDestroysTheFailedWebView() {
        setContent(State.MessageContent(message, Content.Html(WebViewState.ERROR)))
        val failedWebView = findWebView()

        composeRule.onNodeWithText(context.getString(CoreR.string.ua_retry_button).uppercase()).performClick()
        composeRule.waitForIdle()

        assertThat(shadowOf(failedWebView).wasDestroyCalled()).isTrue()
        assertThat(shadowOf(findWebView()).wasDestroyCalled()).isFalse()
    }

    @Test
    public fun leavingTheMessageDestroysTheWebView() {
        val state = setContent(State.MessageContent(message, Content.Html(WebViewState.LOADED)))
        val webView = findWebView()

        composeRule.runOnUiThread { state.viewState = State.Empty }
        composeRule.waitForIdle()

        assertThat(shadowOf(webView).wasDestroyCalled()).isTrue()
    }

    @Test
    public fun switchingMessagesReusesTheWebView() {
        val state = setContent(State.MessageContent(message, Content.Html(WebViewState.LOADED)))
        val webView = findWebView()

        composeRule.runOnUiThread {
            state.viewState = State.MessageContent(createMessage("other-id"), Content.Html(WebViewState.INIT))
        }
        composeRule.waitForIdle()

        assertThat(findWebView()).isSameInstanceAs(webView)
        assertThat(shadowOf(webView).wasDestroyCalled()).isFalse()
    }

    /** Replays WebView's callback order for a failed main-frame load: the error, then a finish. */
    private fun WebView.failMainFrameLoad() {
        val request = mockk<WebResourceRequest>(relaxed = true) { every { isForMainFrame } returns true }
        val error = mockk<WebResourceError>(relaxed = true) {
            every { errorCode } returns WebViewClient.ERROR_HOST_LOOKUP
            every { description } returns "net::ERR_INTERNET_DISCONNECTED"
        }
        webViewClient.onReceivedError(this, request, error)
        webViewClient.onPageFinished(this, message.bodyUrl)
    }

    private fun findWebView(): WebView {
        fun View.find(): WebView? = when (this) {
            is WebView -> this
            is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).find() }
            else -> null
        }
        return requireNotNull(composeRule.activity.window.decorView.find())
    }

    /**
     * Shows [MessageCenterMessage] for [viewState], recording every action it sends.
     *
     * @param viewState The state to display.
     * @param applyWebViewState Whether to apply [Action.UpdateWebViewState] to the displayed state, as the
     *   view model would. Off by default so a test's state stays exactly as given.
     * @return The displayed state, for tests that change it afterwards.
     */
    private fun setContent(viewState: State, applyWebViewState: Boolean = false): MessageCenterMessageState {
        lateinit var state: MessageCenterMessageState
        state = MessageCenterMessageState(
            onAction = { action ->
                actions.add(action)
                val current = state.viewState
                if (applyWebViewState && action is Action.UpdateWebViewState && current is State.MessageContent) {
                    state.viewState = current.copy(content = Content.Html(action.state))
                }
            },
            makeAnalytics = { _, _ -> mockk() },
        )
        state.viewState = viewState

        composeRule.setContent {
            MessageCenterTheme(colors = MessageCenterColors.lightDefaults()) {
                MessageCenterMessage(state = state, onClose = {})
            }
        }
        composeRule.waitForIdle()
        return state
    }
}
