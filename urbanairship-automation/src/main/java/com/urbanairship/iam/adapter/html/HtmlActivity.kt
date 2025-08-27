/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.html

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.core.graphics.drawable.DrawableCompat
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.UrlAllowList
import com.urbanairship.automation.R
import com.urbanairship.iam.InAppMessageActivity
import com.urbanairship.iam.content.HTML
import com.urbanairship.iam.content.InAppMessageDisplayContent.HTMLContent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.view.BoundedFrameLayout
import com.urbanairship.javascript.NativeBridge
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.emptyJsonMap
import com.urbanairship.webkit.AirshipWebChromeClient
import com.urbanairship.webkit.AirshipWebView
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 * HTML in-app message activity.
 */
internal class HtmlActivity : InAppMessageActivity<HTMLContent>() {

    private var webView: AirshipWebView? = null
    private var error: Int? = null
    private var handler: Handler? = null
    private var url: String? = null
    private val delayedLoadRunnable = Runnable { load() }

    override fun onCreateMessage(savedInstanceState: Bundle?) {
        val messageContent = displayContent?.html
        if (messageContent == null) {
            finish()
            return
        }
        var borderRadius = 0f
        if (isFullScreen(messageContent)) {
            setTheme(R.style.UrbanAirship_InAppHtml_Activity_Fullscreen)
            setContentView(R.layout.ua_iam_html_fullscreen)
        } else {
            setContentView(R.layout.ua_iam_html)
            borderRadius = messageContent.borderRadius
        }

        val progressBar = findViewById<ProgressBar>(R.id.progress)
        val dismiss = findViewById<ImageButton>(R.id.dismiss)
        val content = findViewById<BoundedFrameLayout>(R.id.content_holder)
        applySizeConstraints(messageContent)

        val wv = findViewById<AirshipWebView>(R.id.web_view)
        webView = wv

        handler = Handler(Looper.getMainLooper())
        url = messageContent.url

        if (!Airship.shared().urlAllowList.isAllowed(url, UrlAllowList.Scope.OPEN_URL)) {
            UALog.e("HTML in-app message URL is not allowed. Unable to display message.")
            finish()
            return
        }

        val extras = args.extras ?: emptyJsonMap()

        wv.setWebViewClient(object : HtmlWebViewClient(NativeBridge(args.actionRunner), extras) {
            override fun onMessageDismissed(argument: JsonValue) {
                reportButtonTap(argument)
                finish()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (error == null) {
                    crossFade(view, progressBar)
                    return
                }

                when (error) {
                    // Retry
                    ERROR_CONNECT, ERROR_TIMEOUT, ERROR_UNKNOWN -> load(RETRY_DELAY_MS)
                    else -> {
                        // Load an empty page
                        error = null
                        view?.loadData("", "text/html", null)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String,
                failingUrl: String
            ) {
                if (failingUrl == intent.dataString) {
                    UALog.e {
                        "HtmlActivity - Failed to load page $failingUrl " +
                                "with error $errorCode $description"
                    }
                    error = errorCode
                }
            }
        })
        wv.setAlpha(0f)
        wv.settings.setSupportMultipleWindows(true)
        wv.webChromeClient = AirshipWebChromeClient(this)

        // DismissButton
        val dismissDrawable = DrawableCompat.wrap(dismiss.drawable).mutate()
        DrawableCompat.setTint(dismissDrawable, messageContent.dismissButtonColor.color)
        dismiss.setImageDrawable(dismissDrawable)
        dismiss.setOnClickListener {
            displayListener?.onUserDismissed()
            finish()
        }

        // Background
        val backgroundColor = messageContent.backgroundColor.color
        content.setBackgroundColor(backgroundColor)
        wv.setBackgroundColor(backgroundColor)
        if (Color.alpha(backgroundColor) == 255) {
            if (borderRadius > 0) {
                content.setClipPathBorderRadius(borderRadius)
            }
        }
    }

    private fun reportButtonTap(json: JsonValue) {
        try {
            json.optMap().get("button_info")
                ?.let(InAppMessageButtonInfo::fromJson)
                ?.let { displayListener?.onButtonDismissed(it) }
        } catch (ex: JsonException) {
            UALog.e(ex) { "Unable to parse message resolution JSON" }
        }
    }

    private fun isFullScreen(html: HTML): Boolean {
        return if (html.allowFullscreenDisplay) {
            try {
                resources.getBoolean(R.bool.ua_iam_html_allow_fullscreen_display)
            } catch (e: Resources.NotFoundException) {
                UALog.w { "Failed to load 'R.bool.ua_iam_html_allow_fullscreen_display'!"  }
                false
            }
        } else {
            false
        }
    }

    public override fun onResume() {
        super.onResume()
        webView?.onResume()
        load()
    }

    public override fun onPause() {
        super.onPause()
        webView?.onPause()

        // Stop any loading
        webView?.stopLoading()

        // Cancel any delayed loads
        handler?.removeCallbacks(delayedLoadRunnable)
    }

    /**
     * Fades a view in while fading another view out.
     *
     * @param inView The view to fade in
     * @param outView The view to fade out
     */
    private fun crossFade(inView: View?, outView: View?) {
        inView?.animate()?.alpha(1f)?.setDuration(200)
        outView?.animate()?.alpha(0f)?.setDuration(200)
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    outView.visibility = View.GONE
                }
            })
    }
    /**
     * Load the page with a delay.
     *
     * @param delay Delay before loading the page. A delay of 0 or less load the page immediately.
     */
    private fun load(delay: Long = 0) {
        webView?.stopLoading()

        if (delay > 0) {
            handler?.postDelayed(delayedLoadRunnable, delay)
        } else {
            this@HtmlActivity.url?.let {
                UALog.i("Loading url: %s", it)
                error = null
                webView?.loadUrl(it)
            } ?: UALog.w {
                "Unable to load HTML for in-app message. URL is null!"
            }
        }
    }

    private fun applySizeConstraints(html: HTML) {
        if (html.width == 0L && html.height == 0L) {
            return
        }

        val view = findViewById<View>(R.id.content_holder) ?: return
        val width = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            html.width.toFloat(),
            resources.displayMetrics
        ).toInt()
        val height = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            html.height.toFloat(),
            resources.displayMetrics
        ).toInt()
        val aspectLock = html.aspectLock == true
        val viewWeakReference = WeakReference(view)
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val weakView = viewWeakReference.get() ?: return true
                val params = weakView.layoutParams
                val parentWidth = weakView.measuredWidth
                val parentHeight = weakView.measuredHeight
                var normalizedWidth = min(parentWidth, width)
                var normalizedHeight = min(parentHeight, height)
                if (aspectLock && (normalizedWidth != width || normalizedHeight != height)) {
                    val landingPageAspect = width.toFloat() / height
                    val parentAspect = parentWidth.toFloat() / parentHeight
                    if (parentAspect > landingPageAspect) {
                        normalizedWidth = (width.toFloat() * parentHeight / height).toInt()
                    } else {
                        normalizedHeight = (height.toFloat() * parentWidth / width).toInt()
                    }
                }
                if (normalizedHeight > 0) {
                    params.height = normalizedHeight
                }
                if (normalizedWidth > 0) {
                    params.width = normalizedWidth
                }
                weakView.layoutParams = params
                weakView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    companion object {
        private const val RETRY_DELAY_MS: Long = 20_000 // 20 seconds
    }
}
