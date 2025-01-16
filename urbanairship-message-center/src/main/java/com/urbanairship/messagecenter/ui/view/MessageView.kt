package com.urbanairship.messagecenter.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.get
import com.urbanairship.Provider
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.animator.animateFadeIn
import com.urbanairship.messagecenter.animator.animateFadeOut
import com.urbanairship.messagecenter.ui.view.MessageViewState.Error.Type.LOAD_FAILED
import com.urbanairship.messagecenter.ui.view.MessageViewState.Error.Type.UNAVAILABLE
import com.urbanairship.messagecenter.ui.widget.MessageWebView
import com.urbanairship.messagecenter.ui.widget.MessageWebViewClient
import com.urbanairship.messagecenter.util.getActivity
import com.urbanairship.webkit.AirshipWebChromeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
    }

    /** Listener for `MessageView` events. */
    public var listener: Listener? = null

    /** Controls whether the "No message selected" view is shown when no message is loaded. */
    public var showEmptyView: Boolean = false

    private var message: Message? = null

    init {
        inflate(context, R.layout.ua_view_message, this)
        onViewCreated()
    }

    private var error: Int? = null

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
            })

            settings.setSupportMultipleWindows(true)
            webChromeClient = AirshipWebChromeClient(context.getActivity())
        }

        views.errorRetryButton.setOnClickListener {
            listener?.onRetryClicked()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.run {
            // Remove any previously attached observer
            removeObserver(lifecycleObserver)
            // Add the observer
            addObserver(lifecycleObserver)
        } ?: UALog.w("MessageView must be hosted in a view that has a LifecycleOwner!")
    }

    /** Renders the given [state] to the view. */
    @MainThread
    public fun render(state: MessageViewState) {
        when (state) {
            is MessageViewState.Content -> {
                message = state.message
                views.webView.loadMessage(state.message)
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

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            views.webView.onResume()
            views.webView.resumeTimers()
        }

        override fun onPause(owner: LifecycleOwner) {
            views.webView.onPause()
            // Also pause javascript timers
            views.webView.pauseTimers()
        }
    }

    private data class Views(
        val root: View,
        val shouldShowEmptyView: () -> Boolean,
        val webView: MessageWebView = root.findViewById(R.id.message),
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
        }

        fun showMessage() {
            webView.isVisible = true

            progressBar.isVisible = false
            errorPage.isVisible = false
            emptyPage.isVisible = false
        }

        fun showEmpty() {
            emptyPage.isVisible = true

            errorPage.isVisible = false
            webView.isVisible = false
            progressBar.isVisible = false
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
        }
    }
}
