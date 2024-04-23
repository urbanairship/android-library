/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.fullscreen

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.urbanairship.automation.R
import com.urbanairship.automation.rewrite.inappmessage.InAppActionUtils
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageActivity
import com.urbanairship.automation.rewrite.inappmessage.content.Fullscreen
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent.FullscreenContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageTextInfo
import com.urbanairship.automation.rewrite.inappmessage.view.InAppButtonLayout
import com.urbanairship.automation.rewrite.inappmessage.view.InAppViewUtils
import com.urbanairship.automation.rewrite.inappmessage.view.MediaView
import com.urbanairship.util.parcelableExtra
import com.urbanairship.webkit.AirshipWebChromeClient
import kotlin.math.max

/**
 * Full screen in-app message activity.
 */
internal class FullScreenActivity : InAppMessageActivity<FullscreenContent>(), InAppButtonLayout.ButtonClickListener {

    private var mediaView: MediaView? = null

    override fun extractDisplayContent(): FullscreenContent? = parcelableExtra(DISPLAY_CONTENT)

    override fun onCreateMessage(savedInstanceState: Bundle?) {
        val displayContent = this.displayContent ?: return
        val fullscreen = displayContent.fullscreen

        val template = normalizeTemplate(displayContent)
        setContentView(getTemplate(template))
        hideActionBar()

        val heading: TextView = findViewById(R.id.heading)
        val body: TextView = findViewById(R.id.body)
        val buttonLayout: InAppButtonLayout = findViewById(R.id.buttons)
        mediaView = findViewById(R.id.media)
        val footer: Button = findViewById(R.id.footer)
        val dismiss: ImageButton = findViewById(R.id.dismiss)
        val contentHolder: View = findViewById(R.id.content_holder)

        // Heading
        if (fullscreen.heading != null) {
            InAppViewUtils.applyTextInfo(heading, fullscreen.heading)

            // Heading overlaps with the dismiss button, so it has more padding on the right than
            // the left. If it's centered, normalize the padding to prevent center text being misaligned.
            if (fullscreen.heading.alignment == InAppMessageTextInfo.Alignment.CENTER) {
                normalizeHorizontalPadding(heading)
            }
        } else {
            heading.visibility = View.GONE
        }

        // Body
        if (fullscreen.body != null) {
            InAppViewUtils.applyTextInfo(body, fullscreen.body)
        } else {
            body.visibility = View.GONE
        }

        // Media
        mediaView?.let {
            if (fullscreen.media != null) {
                it.setChromeClient(AirshipWebChromeClient(this))
                InAppViewUtils.loadMediaInfo(it, fullscreen.media, assets)
            } else {
                it.visibility = View.GONE
            }
        }

        if (fullscreen.buttons.isNotEmpty()) {
            buttonLayout.setButtons(fullscreen.buttonLayoutType, fullscreen.buttons)
            buttonLayout.setButtonClickListener(this)
        } else {
            buttonLayout.visibility = View.GONE
        }

        // Footer
        if (fullscreen.footer != null) {
            InAppViewUtils.applyButtonInfo(footer, fullscreen.footer, 0)
            footer.setOnClickListener { view -> onButtonClicked(view, fullscreen.footer) }
        } else {
            footer.visibility = View.GONE
        }

        // DismissButton
        val dismissDrawable = DrawableCompat.wrap(dismiss.drawable).mutate()
        DrawableCompat.setTint(dismissDrawable, fullscreen.dismissButtonColor.color)
        dismiss.setImageDrawable(dismissDrawable)
        dismiss.setOnClickListener {
            displayListener?.onUserDismissed()
            finish()
        }

        // Background color
        window.decorView.setBackgroundColor(fullscreen.backgroundColor.color)

        // Apply the insets but do not consume them. Allows for the dismiss button to also receive the insets.
        if (ViewCompat.getFitsSystemWindows(contentHolder)) {
            ViewCompat.setOnApplyWindowInsetsListener(contentHolder) { v, insets ->
                ViewCompat.onApplyWindowInsets(v, insets)
                insets
            }
        }
    }

    override fun getDisplayListener(token: String): InAppMessageDisplayListener? = FullScreenAdapter.getListener(token)

    override fun onButtonClicked(view: View, buttonInfo: InAppMessageButtonInfo) {
        InAppActionUtils.runActions(buttonInfo)
        displayListener?.onButtonDismissed(buttonInfo)
        finish()
    }

    override fun onResume() {
        super.onResume()
        mediaView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mediaView?.onPause()
    }

    /**
     * Gets the layout for the given template.
     *
     * @param template The fullscreen template.
     * @return The template layout resource ID.
     */
    @LayoutRes
    internal fun getTemplate(template: Fullscreen.Template): Int {
        return when(template) {
            Fullscreen.Template.HEADER_BODY_MEDIA -> R.layout.ua_iam_fullscreen_header_body_media
            Fullscreen.Template.HEADER_MEDIA_BODY -> R.layout.ua_iam_fullscreen_header_media_body
            Fullscreen.Template.MEDIA_HEADER_BODY -> R.layout.ua_iam_fullscreen_media_header_body
        }
    }

    /**
     * Gets the normalized template from the display content. The template may differ from the
     * display content's template to facilitate theming.
     *
     * @param displayContent The display content.
     * @return The full screen template.
     */
    internal fun normalizeTemplate(displayContent: FullscreenContent): Fullscreen.Template {
        val content = displayContent.fullscreen

        // If we do not have media use TEMPLATE_HEADER_BODY_MEDIA
        if (content.media == null) {
            return Fullscreen.Template.HEADER_BODY_MEDIA
        }

        // If we do not have a header for template TEMPLATE_HEADER_MEDIA_BODY, but we have media,
        // fallback to TEMPLATE_MEDIA_HEADER_BODY to avoid missing padding at the top modal
        return when(content.template) {
            Fullscreen.Template.HEADER_MEDIA_BODY -> {
                if (content.heading == null) {
                    Fullscreen.Template.MEDIA_HEADER_BODY
                } else {
                    content.template
                }
            }
            else -> content.template
        }
    }

    private fun normalizeHorizontalPadding(view: TextView) {
        val padding = max(ViewCompat.getPaddingEnd(view), ViewCompat.getPaddingStart(view))
        view.setPadding(padding, view.paddingTop, padding, view.paddingBottom)
        view.requestLayout()
    }
}
