package com.urbanairship.messagecenter.ui.view

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.view.isVisible
import com.urbanairship.UALog
import com.urbanairship.actions.Action
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.actions.run
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.ui.ThomasLayoutViewFactory
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.view.MessageViewState.Error.Type.LOAD_FAILED
import com.urbanairship.messagecenter.ui.view.MessageViewState.Error.Type.UNAVAILABLE
import com.urbanairship.messagecenter.ui.widget.MessageWebView
import com.urbanairship.messagecenter.ui.widget.MessageWebViewClient
import com.urbanairship.messagecenter.util.getActivity
import com.urbanairship.webkit.AirshipWebChromeClient

/** A `WebView` configured to display Airship Message Center message content. */
public class MessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
): FrameLayout(context, attrs, defStyle, defResStyle) {

    private val views: Views by lazy { Views(this, { showEmptyView }) }

    /** Listener interface for `MessageView` UI updates and UI interactions. */
    public interface Listener {
        /** Called when a [message] is loaded. */
        public fun onMessageLoaded(message: Message)
        /** Called when a message load error occurs. */
        public fun onMessageLoadError(error: MessageViewState.Error.Type)
        /** Called when the retry button is clicked. */
        public fun onRetryClicked()
        /** Called when the message web view requests to be closed. */
        public fun onCloseMessage()
    }

    /** Listener for `MessageView` events. */
    public var listener: Listener? = null

    /** Controls whether the "No message selected" view is shown when no message is loaded. */
    public var showEmptyView: Boolean = false

    private var message: Message? = null

    internal var analyticsFactory: ((onDismissed: () -> Unit) -> ThomasListenerInterface?)? = null
    private var isDismissReported = false
    private var currentDisplayArgs: DisplayArgs? = null

    init {
        inflate(context, R.layout.ua_view_message, this)
        onViewCreated()
    }

    private var error: Int? = null

    private fun createDisplayArgs(layout: LayoutInfo): DisplayArgs? {
        if (message?.contentType != Message.ContentType.NATIVE) {
            return null
        }

        val analytics = analyticsFactory?.invoke {
            isDismissReported = true
            listener?.onCloseMessage()
        } ?: return null

        return DisplayArgs(
            payload = layout,
            listener = analytics,
            inAppActivityMonitor = GlobalActivityMonitor.shared(context),
            actionRunner = { actions, _ ->
                DefaultActionRunner.run(actions, Action.Situation.AUTOMATION)
            }
        )
    }

    private fun onViewCreated() {
        with (views.webView) {
            setWebViewClient(object : MessageWebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    if (error != null) {
                        UALog.d { "Showing error! $url" }

                        views.showError(LOAD_FAILED)
                        listener?.onMessageLoadError(LOAD_FAILED)
                    } else {
                        message?.let {
                            UALog.d { "Mark read and show message! $url" }

                            views.showMessage()
                            listener?.onMessageLoaded(it)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String?) {
                    UALog.w { "onReceivedError! $errorCode $description $failingUrl" }
                    if (message != null && failingUrl != null && failingUrl == message?.bodyUrl) {
                        error = errorCode
                    }
                }

                override fun onClose(webView: WebView) {
                    listener?.onCloseMessage() ?: super.onClose(webView)
                }
            })

            settings.setSupportMultipleWindows(true)
            webChromeClient = AirshipWebChromeClient(context.getActivity())
        }

        views.errorRetryButton.setOnClickListener {
            listener?.onRetryClicked()
        }
    }

    /** Renders the given [state] to the view. */
    @MainThread
    public fun render(state: MessageViewState) {
        when (state) {
            is MessageViewState.MessageContent -> {
                if (message == state.message) {
                    UALog.v("Message already displayed: ${state.message.id}")
                    return
                }

                message = state.message

                when(val content = state.content) {
                    is MessageViewState.MessageContent.Content.Html -> {
                        views.webView.loadMessage(state.message)
                    }
                    is MessageViewState.MessageContent.Content.Native -> {
                        val displayArgs = createDisplayArgs(content.layout.layoutInfo) ?: run {
                            UALog.w { "Failed to create display args" }
                            return
                        }

                        views.nativeContainer.removeAllViews()
                        val thomasView = ThomasLayoutViewFactory.createView(
                            context = context,
                            displayArgs = displayArgs,
                            viewId = state.message.id
                        )
                        if (thomasView == null) {
                            UALog.w { "Failed to load native layout" }
                            return
                        }

                        views.nativeContainer.addView(
                            thomasView,
                            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                        )
                        views.showMessage(true)
                        listener?.onMessageLoaded(state.message)
                        currentDisplayArgs = displayArgs
                    }
                }

            }
            is MessageViewState.Empty -> {
                message = null
                views.showEmpty()
            }
            is MessageViewState.Error -> {
                message = null
                views.showError(state.error)
            }
            is MessageViewState.Loading -> {
                message = null
                views.showProgress()
            }
        }
    }

    internal fun onDismissed() {
        val message = message ?: return
        if (message.contentType != Message.ContentType.NATIVE) {
            return
        }
        if (isDismissReported) {
            ThomasLayoutViewFactory.clear()
            isDismissReported = false
            return
        }

        currentDisplayArgs?.listener?.onReportingEvent(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.UserDismissed,
                displayTime = ThomasLayoutViewFactory.calculateDisplayTime(message.id),
                context = LayoutData.EMPTY
            )
        )

        ThomasLayoutViewFactory.clear()
    }

    /** Pauses the WebView. */
    public fun pauseWebView(): Unit = with (views.webView) {
        onPause()
    }

    /** Resumes the WebView. */
    public fun resumeWebView(): Unit = with (views.webView) {
        onResume()
    }

    /** Saves WebView state. */
    public fun saveWebViewState(outState: Bundle) {
        views.webView.saveState(outState)
    }

    /** Restores WebView state. */
    public fun restoreWebViewState(inState: Bundle) {
        views.webView.restoreState(inState)
    }

    private data class Views(
        val root: View,
        val shouldShowEmptyView: () -> Boolean,
        val webView: MessageWebView = root.findViewById(R.id.message),
        val nativeContainer: FrameLayout = root.findViewById<FrameLayout>(R.id.native_container),
        val progressBar: View = root.findViewById(R.id.progress),
        val errorPage: View = root.findViewById(R.id.error),
        val errorMessage: TextView = root.findViewById(R.id.error_text),
        val errorRetryButton: View = root.findViewById(R.id.error_button),
        val emptyPage: View = root.findViewById(R.id.empty),
        val emptyMessage: TextView = root.findViewById(R.id.empty_message),
    ) {
        fun showProgress() {
            progressBar.isVisible = true

            errorPage.isVisible = false
            webView.isVisible = false
            emptyPage.isVisible = false
            nativeContainer.isVisible = false
        }

        fun showMessage(isNative: Boolean = false) {
            if (isNative) {
                nativeContainer.isVisible = true
                webView.isVisible = false
            } else {
                webView.isVisible = true
                nativeContainer.isVisible = false
            }

            progressBar.isVisible = false
            errorPage.isVisible = false
            emptyPage.isVisible = false
        }

        fun showEmpty() {
            emptyPage.isVisible = true

            errorPage.isVisible = false
            webView.isVisible = false
            progressBar.isVisible = false
            nativeContainer.isVisible = false
        }

        fun showError(error: MessageViewState.Error.Type) {
            when (error) {
                UNAVAILABLE -> {
                    errorRetryButton.visibility = View.GONE
                    errorMessage.setText(com.urbanairship.R.string.ua_mc_no_longer_available)
                }

                LOAD_FAILED -> {
                    errorRetryButton.visibility = View.VISIBLE
                    errorMessage.setText(com.urbanairship.R.string.ua_mc_failed_to_load)
                }
            }

            errorPage.isVisible = true

            webView.isVisible = false
            progressBar.isVisible = false
            emptyPage.isVisible = false
            nativeContainer.isVisible = false
        }
    }
}
