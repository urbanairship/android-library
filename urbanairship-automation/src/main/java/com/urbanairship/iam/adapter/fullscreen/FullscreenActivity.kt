/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.fullscreen

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.urbanairship.actions.run
import com.urbanairship.automation.R
import com.urbanairship.iam.InAppMessageActivity
import com.urbanairship.iam.content.Fullscreen
import com.urbanairship.iam.content.InAppMessageDisplayContent.FullscreenContent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.iam.view.InAppButtonLayout
import com.urbanairship.iam.view.InAppViewUtils
import com.urbanairship.iam.view.MediaView
import com.urbanairship.webkit.AirshipWebChromeClient
import kotlin.math.max

/**
 * Full screen in-app message activity.
 */
internal class FullscreenActivity : InAppMessageActivity<FullscreenContent>(), InAppButtonLayout.ButtonClickListener {

    private var mediaView: MediaView? = null
    override fun onCreateMessage(savedInstanceState: Bundle?) {
        val messageContent = this.displayContent?.fullscreen
        if (messageContent == null) {
            finish()
            return
        }

        val template = normalizeTemplate(messageContent)

        setContentView(getTemplate(template))
        hideActionBar()

        val heading: TextView = findViewById(R.id.heading)
        val body: TextView = findViewById(R.id.body)
        val buttonLayout: InAppButtonLayout = findViewById(R.id.buttons)
        val footer: Button = findViewById(R.id.footer)
        val dismiss: ImageButton = findViewById(R.id.dismiss)
        val contentHolder: View = findViewById(R.id.content_holder)
        mediaView = findViewById(R.id.media)

        // Heading
        if (messageContent.heading != null) {
            InAppViewUtils.applyTextInfo(heading, messageContent.heading)

            // Heading overlaps with the dismiss button, so it has more padding on the right than
            // the left. If it's centered, normalize the padding to prevent center text being misaligned.
            if (messageContent.heading.alignment == InAppMessageTextInfo.Alignment.CENTER) {
                normalizeHorizontalPadding(heading)
            }
        } else {
            heading.visibility = View.GONE
        }

        // Body
        if (messageContent.body != null) {
            InAppViewUtils.applyTextInfo(body, messageContent.body)
        } else {
            body.visibility = View.GONE
        }

        // Media
        mediaView?.let {
            if (messageContent.media != null) {
                it.setChromeClient(AirshipWebChromeClient(this))
                InAppViewUtils.loadMediaInfo(it, messageContent.media, assets)
            } else {
                it.visibility = View.GONE
            }
        }

        // Button Layout
        if (messageContent.buttons.isNotEmpty()) {
            buttonLayout.setButtons(messageContent.buttonLayoutType, messageContent.buttons)
            buttonLayout.setButtonClickListener(this)
        } else {
            buttonLayout.visibility = View.GONE
        }

        // Footer
        if (messageContent.footer != null) {
            InAppViewUtils.applyButtonInfo(footer, messageContent.footer, 0)
            footer.setOnClickListener { view -> onButtonClicked(view, messageContent.footer) }
        } else {
            footer.visibility = View.GONE
        }

        // Dismiss Button
        val dismissDrawable = DrawableCompat.wrap(dismiss.drawable).mutate()
        DrawableCompat.setTint(dismissDrawable, messageContent.dismissButtonColor.color)
        dismiss.setImageDrawable(dismissDrawable)
        dismiss.setOnClickListener {
            displayListener?.onUserDismissed()
            finish()
        }

        // Background color
        window.decorView.setBackgroundColor(messageContent.backgroundColor.color)

        // Apply the insets but do not consume them. Allows for the dismiss button to also receive the insets.
        if (ViewCompat.getFitsSystemWindows(contentHolder)) {
            ViewCompat.setOnApplyWindowInsetsListener(contentHolder) { v, insets ->
                ViewCompat.onApplyWindowInsets(v, insets)
                insets
            }
        }
    }

    override fun onButtonClicked(view: View, buttonInfo: InAppMessageButtonInfo) {
        buttonInfo.actions?.let {
            args.actionRunner.run(it.map)
        }
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
     * @param fullscreen The `Fullscreen` content.
     * @return The full screen template.
     */
    private fun normalizeTemplate(fullscreen: Fullscreen): Fullscreen.Template {
        // If we do not have media use TEMPLATE_HEADER_BODY_MEDIA
        if (fullscreen.media == null) {
            return Fullscreen.Template.HEADER_BODY_MEDIA
        }

        // If we do not have a header for template TEMPLATE_HEADER_MEDIA_BODY, but we have media,
        // fallback to TEMPLATE_MEDIA_HEADER_BODY to avoid missing padding at the top modal
        return when(fullscreen.template) {
            Fullscreen.Template.HEADER_MEDIA_BODY -> {
                if (fullscreen.heading == null) {
                    Fullscreen.Template.MEDIA_HEADER_BODY
                } else {
                    fullscreen.template
                }
            }
            else -> fullscreen.template
        }
    }

    private fun normalizeHorizontalPadding(view: TextView) {
        val padding = max(ViewCompat.getPaddingEnd(view), ViewCompat.getPaddingStart(view))
        view.setPadding(padding, view.paddingTop, padding, view.paddingBottom)
        view.requestLayout()
    }
}
