/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.html

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import com.urbanairship.UAirship
import com.urbanairship.UrlAllowList
import com.urbanairship.automation.R
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageActivity
import com.urbanairship.automation.rewrite.inappmessage.content.HTML
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent.HTMLContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import com.urbanairship.automation.rewrite.inappmessage.view.BoundedFrameLayout
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.emptyJsonMap
import com.urbanairship.util.parcelableExtra
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

    override fun extractDisplayContent(): HTMLContent? = parcelableExtra(DISPLAY_CONTENT)

    override fun onCreateMessage(savedInstanceState: Bundle?) {
        val displayContent = displayContent ?: return
        val html = displayContent.html

        var borderRadius = 0f
        if (isFullScreen(displayContent)) {
            setTheme(R.style.UrbanAirship_InAppHtml_Activity_Fullscreen)
            setContentView(R.layout.ua_iam_html_fullscreen)
        } else {
            setContentView(R.layout.ua_iam_html)
            borderRadius = html.borderRadius
        }

        val progressBar = findViewById<ProgressBar>(R.id.progress)
        val dismiss = findViewById<ImageButton>(R.id.dismiss)
        val content = findViewById<BoundedFrameLayout>(R.id.content_holder)
        applySizeConstraints(html)

        webView = findViewById(R.id.web_view)
        handler = Handler(Looper.getMainLooper())
        url = html.url

        if (!UAirship.shared().urlAllowList.isAllowed(url, UrlAllowList.SCOPE_OPEN_URL)) {
            UALog.e("HTML in-app message URL is not allowed. Unable to display message.")
            finish()
            return
        }

        val extras = try {
            JsonValue.parseString(intent.getStringExtra(INTENT_EXTRAS_KEY)).optMap()
        } catch (ex: JsonException) {
            UALog.e(ex) { "Failed to decode message extras" }
            emptyJsonMap()
        }

        webView?.setWebViewClient(object : HtmlWebViewClient(extras) {
            override fun onMessageDismissed(argument: JsonValue) {
                reportButtonTap(argument)
                finish()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (error == null) {
                    crossFade(webView, progressBar)
                    return
                }

                when (error) {
                    // Retry
                    ERROR_CONNECT, ERROR_TIMEOUT, ERROR_UNKNOWN -> load(RETRY_DELAY_MS)
                    else -> {
                        // Load an empty page
                        error = null
                        webView?.loadData("", "text/html", null)
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
                    UALog.e("HtmlActivity - Failed to load page %s with error %s %s",
                        failingUrl, errorCode, description )
                    error = errorCode
                }
            }
        })
        webView?.setAlpha(0f)
        webView?.settings?.setSupportMultipleWindows(true)
        webView?.webChromeClient = AirshipWebChromeClient(this)

        // DismissButton
        val dismissDrawable = DrawableCompat.wrap(dismiss.drawable).mutate()
        DrawableCompat.setTint(dismissDrawable, html.dismissButtonColor.color)
        dismiss.setImageDrawable(dismissDrawable)
        dismiss.setOnClickListener {
            displayListener?.onUserDismissed()
            finish()
        }
        val backgroundColor = html.backgroundColor.color
        content.setBackgroundColor(backgroundColor)
        webView?.setBackgroundColor(backgroundColor)
        if (Color.alpha(backgroundColor) == 255) {
            if (borderRadius > 0) {
                content.setClipPathBorderRadius(borderRadius)
            }
        }
    }

    override fun getDisplayListener(token: String): InAppMessageDisplayListener? = HtmlDisplayAdapter.getListener(token)

    private fun reportButtonTap(json: JsonValue) {
        try {
            val buttonInfo = json
                .optMap()
                .get("button_info")
                ?.let(InAppMessageButtonInfo::fromJson)
                ?: return

            displayListener?.onButtonDismissed(buttonInfo)
        } catch (ex: JsonException) {
            UALog.e(ex) { "Unable to parse message resolution JSON" }
        }
    }

    private fun isFullScreen(displayContent: HTMLContent): Boolean {
        return if (displayContent.html.allowFullscreenDisplay) {
            resources.getBoolean(R.bool.ua_iam_html_allow_fullscreen_display)
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
        webView?.run {
            stopLoading()
            if (delay > 0) {
                handler?.postDelayed(delayedLoadRunnable, delay)
            } else {
                url?.let {
                    UALog.i("Loading url: %s", it)
                    error = null
                    loadUrl(it)
                }
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
        internal const val INTENT_EXTRAS_KEY = "message_extras"
    }
}
