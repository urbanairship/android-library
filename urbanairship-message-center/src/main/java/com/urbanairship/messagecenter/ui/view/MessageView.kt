package com.urbanairship.messagecenter.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.get
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

/**
 * A `WebView` configured to display Airship Message Center message content.
 *
 * Compared to [MessageCenterView], which wraps both the message list and message view, this view is
 * a lower level component that can be used for customizing how the message view is displayed within
 * an app's UI.
 */
public class MessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
): FrameLayout(context, attrs, defStyle, defResStyle) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

    private val views: Views by lazy { Views(this) }

    private var viewModel: MessageViewViewModel? = null
    private var refreshSubscription: SubscriptionCancellation? = null

    /** Listener interface that will be called when a message is loaded. */
    public interface Listener {
        public fun onMessageLoaded(message: Message)
        public fun onMessageLoadError(error: MessageViewState.Error.Type)
    }

    /** Listener for the loaded message. */
    public var listener: Listener? = null

    init {
        inflate(context, R.layout.ua_view_message, this)
        onViewCreated()
    }

    /** The current [Message] ID. */
    public var messageId: String? = null
        set(value) {
            field = value
            value?.let { id -> viewModel?.loadMessage(id) }
        }

    private var message: Message? = null

    private var error: Int? = null

    private fun onViewCreated() {
        with (views.webView) {
            alpha = 0f

            setWebViewClient(object : MessageWebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    UALog.v { "Page finished: $url (error: $error)" }

                    if (error != null) {
                        UALog.i { "Showing error! $url" }

                        views.showError(LOAD_FAILED)
                        listener?.onMessageLoadError(LOAD_FAILED)
                    } else {
                        message?.let {
                            UALog.i { "Mark read and show message! $url" }

                            if (!it.isRead) {
                                viewModel?.markMessagesRead(it)
                            }
                            views.showMessage()
                            listener?.onMessageLoaded(it)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String?) {
                    UALog.i { "onReceivedError! $errorCode $description $failingUrl" }

                    if (message != null && failingUrl != null && failingUrl == message?.bodyUrl) {
                        error = errorCode
                    }
                }
            })

            settings.setSupportMultipleWindows(true)
            webChromeClient = AirshipWebChromeClient(context.getActivity())
        }

        views.errorRetryButton.setOnClickListener {
            message?.let { viewModel?.loadMessage(it.id) }
                ?: UALog.w { "MessageView does not have a message to retry loading!" }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (viewModel == null) {
            viewModel = ViewModelProvider(
                owner = requireNotNull(findViewTreeViewModelStoreOwner()) {
                    "MessageView must be hosted in a view that has a ViewModelStoreOwner!"
                },
                factory = MessageViewViewModel.factory()
            ).get<MessageViewViewModel>().also {
                observeViewModel(it)
            }

            messageId?.let { viewModel?.loadMessage(it) }
        }

        findViewTreeLifecycleOwner()?.lifecycle?.run {
            // Remove any previously attached observer
            removeObserver(lifecycleObserver)
            // Add the observer
            addObserver(lifecycleObserver)
        } ?: UALog.w("MessageView must be hosted in a view that has a LifecycleOwner!")
    }

    private fun observeViewModel(viewModel: MessageViewViewModel) {
        viewModel.states
            .onEach { state ->
                when (state) {
                    is MessageViewState.Content -> {
                        message = state.message
                        views.webView.loadMessage(state.message)
                        views.showMessage()
                    }
                    MessageViewState.Empty -> views.showEmpty()
                    is MessageViewState.Error -> views.showError(state.error)
                    MessageViewState.Loading -> views.showProgress()
                }
            }
            .launchIn(scope)
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            views.webView.onResume()
            refreshSubscription = viewModel?.subscribeForMessageUpdates()
        }

        override fun onPause(owner: LifecycleOwner) {
            views.webView.onPause()
            // Also pause javascript timers
            views.webView.pauseTimers()
            refreshSubscription?.cancel()
        }
    }

    private data class Views(
        val root: View,
        val webView: MessageWebView = root.findViewById(android.R.id.message),
        val progressBar: View = root.findViewById(android.R.id.progress),
        val errorPage: View = root.findViewById(R.id.error),
        val errorMessage: TextView = root.findViewById(R.id.error_message),
        val errorRetryButton: View = root.findViewById(R.id.retry_button),
        val emptyPage: View = root.findViewById(R.id.empty),
        val emptyMessage: TextView = root.findViewById(R.id.empty_message)
    ) {

        fun showProgress() {
            progressBar.alpha = 1f

            if (errorPage.isVisible) {
                errorPage.animateFadeOut()
            }
            webView.animateFadeOut()
            emptyPage.animateFadeOut()
        }

        fun showMessage() {
            webView.animateFadeIn()
            progressBar.animateFadeOut()
            emptyPage.animateFadeOut()
        }

        fun showEmpty() {
            if (errorPage.isVisible) {
                errorPage.animateFadeOut()
            }

            if (webView.isVisible) {
                webView.animateFadeOut()
            }

            progressBar.animateFadeOut()
            emptyPage.animateFadeIn()
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

            with (errorPage) {
                if (isGone) {
                    alpha = 0f
                    visibility = android.view.View.VISIBLE
                }

                animateFadeIn()
            }

            progressBar.animateFadeOut()
            emptyPage.animateFadeOut()
        }
    }
}
