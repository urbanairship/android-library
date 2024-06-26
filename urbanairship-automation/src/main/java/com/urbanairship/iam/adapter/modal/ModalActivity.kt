/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.modal

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.urbanairship.actions.run
import com.urbanairship.automation.R
import com.urbanairship.iam.InAppMessageActivity
import com.urbanairship.iam.content.InAppMessageDisplayContent.ModalContent
import com.urbanairship.iam.content.Modal
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.iam.view.BackgroundDrawableBuilder
import com.urbanairship.iam.view.BorderRadius
import com.urbanairship.iam.view.BoundedLinearLayout
import com.urbanairship.iam.view.InAppButtonLayout
import com.urbanairship.iam.view.InAppViewUtils
import com.urbanairship.iam.view.MediaView
import com.urbanairship.webkit.AirshipWebChromeClient
import kotlin.math.max

/** Modal in-app message activity. */
internal class ModalActivity : InAppMessageActivity<ModalContent>(), InAppButtonLayout.ButtonClickListener {

    private var mediaView: MediaView? = null

    override fun onCreateMessage(savedInstanceState: Bundle?) {
        val messageContent = displayContent?.modal
        if (messageContent == null) {
            finish()
            return
        }

        val messageAllowsFullscreen = messageContent.allowFullscreenDisplay
        val appAllowsFullscreen = resources.getBoolean(R.bool.ua_iam_modal_allow_fullscreen_display)
        val borderRadius = if (appAllowsFullscreen && messageAllowsFullscreen) {
            setTheme(R.style.UrbanAirship_InAppModal_Activity_Fullscreen)
            setContentView(R.layout.ua_iam_modal_fullscreen)
            0f
        } else {
            setContentView(R.layout.ua_iam_modal)
            messageContent.borderRadius
        }

        val template = normalizeTemplate(messageContent)

        // Inflate the content before finding other views
        val content = findViewById<ViewStub>(R.id.modal_content)
        if (content == null) {
            finish()
            return
        }

        content.layoutResource = getTemplate(template)
        content.inflate()

        val modal = findViewById<BoundedLinearLayout>(R.id.modal)
        val heading = findViewById<TextView>(R.id.heading)
        val body = findViewById<TextView>(R.id.body)
        val buttonLayout = findViewById<InAppButtonLayout>(R.id.buttons)
        val footer = findViewById<Button>(R.id.footer)
        val dismiss = findViewById<ImageButton>(R.id.dismiss)
        mediaView = findViewById(R.id.media)

        // Heading
        if (messageContent.heading != null) {
            InAppViewUtils.applyTextInfo(heading, messageContent.heading)
            // Heading overlaps with the dismiss button, so it has more padding on the right than
            // the left. If its centered, normalize the padding to prevent center text being misaligned.
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
        if (messageContent.media != null) {
            mediaView?.let {
                it.setChromeClient(AirshipWebChromeClient(this))
                InAppViewUtils.loadMediaInfo(it, messageContent.media, assets)
            }
        } else {
            mediaView?.visibility = View.GONE
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

        // Background color
        val background: Drawable = BackgroundDrawableBuilder.newBuilder(this)
            .setBackgroundColor(messageContent.backgroundColor.color)
            .setBorderRadius(borderRadius, BorderRadius.ALL)
            .build()
        ViewCompat.setBackground(modal, background)

        if (borderRadius > 0) {
            modal.setClipPathBorderRadius(borderRadius)
        }

        // Dismiss Button
        val dismissDrawable = DrawableCompat.wrap(dismiss.drawable).mutate()
        DrawableCompat.setTint(dismissDrawable, messageContent.dismissButtonColor.color)
        dismiss.setImageDrawable(dismissDrawable)
        dismiss.setOnClickListener {
            displayListener?.onUserDismissed()
            finish()
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
     * @param template The modal template.
     * @return The template layout resource ID.
     */
    @LayoutRes
    private fun getTemplate(template: Modal.Template): Int {
        return when (template) {
            Modal.Template.HEADER_BODY_MEDIA -> R.layout.ua_iam_modal_header_body_media
            Modal.Template.HEADER_MEDIA_BODY -> R.layout.ua_iam_modal_header_media_body
            Modal.Template.MEDIA_HEADER_BODY -> R.layout.ua_iam_modal_media_header_body
        }
    }

    /**
     * Gets the normalized template from the display content. The template may differ from the
     * display content's template to facilitate theming.
     *
     * @param modal The modal display content.
     * @return The modal template.
     */
    private fun normalizeTemplate(modal: Modal): Modal.Template {

        // If we do not have media use TEMPLATE_HEADER_BODY_MEDIA
        if (modal.media == null) {
            return Modal.Template.HEADER_BODY_MEDIA
        }

        // If we do not have a header for template TEMPLATE_HEADER_MEDIA_BODY, but we have media,
        // fallback to TEMPLATE_MEDIA_HEADER_BODY to avoid missing padding at the top modal
        return if (modal.template == Modal.Template.HEADER_MEDIA_BODY && modal.heading == null) {
            Modal.Template.MEDIA_HEADER_BODY
        } else {
            modal.template
        }
    }

    private fun normalizeHorizontalPadding(view: TextView) {
        val padding = max(ViewCompat.getPaddingEnd(view), ViewCompat.getPaddingStart(view))
        view.setPadding(padding, view.paddingTop, padding, view.paddingBottom)
        view.requestLayout()
    }
}
