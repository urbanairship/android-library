/* Copyright Airship and Contributors */
package com.urbanairship.webkit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebView.WebViewTransport
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.urbanairship.UALog
import com.urbanairship.javascript.NativeBridge
import com.urbanairship.util.UriUtils
import java.lang.ref.WeakReference

/**
 * Web Chrome Client that enables full screen video.
 */
public class AirshipWebChromeClient public constructor(activity: Activity?) : WebChromeClient() {

    private val weakActivity = WeakReference(activity)
    private var customView: View? = null

    override fun getDefaultVideoPoster(): Bitmap {
        return Bitmap.createBitmap(intArrayOf(Color.TRANSPARENT), 1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val activity = weakActivity.get() ?: return

        customView?.let {
            val parent = it.parent as ViewGroup
            parent.removeView(it)
        }

        customView = view
        customView?.setBackgroundColor(Color.BLACK)

        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        activity.window.addContentView(
            customView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }

    override fun onHideCustomView() {
        val activity = weakActivity.get() ?: return
        val customView = this.customView ?: return

        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val parent = customView.parent as ViewGroup
        parent.removeView(customView)

        this.customView = null
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (!isUserGesture || resultMsg == null || resultMsg.obj !is WebViewTransport) {
            return false
        }

        val tempWebView = WebView(view.context)

        tempWebView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val uri = Uri.parse(url)

                val isActionUrl = NativeBridge.UA_ACTION_SCHEME == uri.scheme
                val hasSpecialScheme = SPECIAL_SCHEMES.contains(uri.scheme)
                if ((!hasSpecialScheme && uri.host == null) || isActionUrl) {
                    return false
                }

                val intent = Intent(Intent.ACTION_VIEW, UriUtils.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    view.context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    UALog.e(e)
                }
                return true
            }
        }

        val transport = resultMsg.obj as WebViewTransport
        transport.webView = tempWebView
        resultMsg.sendToTarget()

        return true
    }

    private companion object {
        /** Special schemes for URLs that don't have a host.  */
        private val SPECIAL_SCHEMES = listOf("tel", "sms", "mailto")
    }
}
