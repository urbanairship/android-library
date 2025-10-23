package com.urbanairship.messagecenter.compose.ui.widget

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.widget.MessageWebView
import com.urbanairship.messagecenter.ui.widget.MessageWebViewClient
import com.urbanairship.webkit.AirshipWebViewClient

/** Compose wrapper for [MessageWebView]. */
@Composable
internal fun MessageCenterWebView(
    modifier: Modifier = Modifier.Companion,
    message: Message?,
    onPageStarted: (() -> Unit),
    onPageReady: (() -> Unit),
    onPageError: (() -> Unit),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        MessageWebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }

    DisposableEffect(message?.bodyUrl) {
        val listener = object : AirshipWebViewClient.Listener {
            override fun onPageFinished(view: WebView, url: String?) {
                UALog.v("onPageFinished: $url")
                onPageReady()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                UALog.v { "WebView error: ${error.errorCode} ${error.description}" }
                onPageError()
            }

            override fun onClose(view: WebView): Boolean {
                onClose()
                return true
            }
        }

        val client = MessageWebViewClient().apply {
            addListener(listener)
        }

        webView.webViewClient = client

        message?.let {
            webView.loadMessage(it)
            onPageStarted()
        }

        onDispose {
            UALog.v { "Disposing MessageCenterWebView" }
            client.removeListener(listener)
        }
    }

    AndroidView(
        modifier = modifier.graphicsLayer(alpha = 0.99f, clip = true), //fixes a weird web view crash
        factory = { webView }
    )
}
