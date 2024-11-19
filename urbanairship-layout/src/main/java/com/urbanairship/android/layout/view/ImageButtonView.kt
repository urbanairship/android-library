/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.ImageButton
import android.widget.ImageView.ScaleType.FIT_CENTER
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import com.urbanairship.UAirship
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.ImageButtonModel
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.CropImageButton
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.images.ImageRequestOptions
import kotlinx.coroutines.flow.Flow

internal class ImageButtonView(
    context: Context,
    model: ImageButtonModel,
    viewEnvironment: ViewEnvironment
) : FrameLayout(context), BaseView, TappableView {

    private var visibilityChangeListener: BaseView.VisibilityChangeListener? = null

    private val button: CropImageButton by lazy { makeImageButton(model) }

    init {

        when (val image = model.viewInfo.image) {
            is Image.Url -> {
                var url = image.url
                viewEnvironment.imageCache()[url]?.let { cachedImage ->
                    url = cachedImage
                }

                doOnAttach {
                    val parentLayoutParams = layoutParams

                    if (image.mediaFit == MediaFit.FIT_CROP) {
                        button.setParentLayoutParams(parentLayoutParams)
                        button.setImagePosition(image.position)
                    } else {
                        button.scaleType = image.mediaFit?.scaleType ?: FIT_CENTER
                    }

                    applyImageRippleEffect(button, model.viewInfo.tapEffect, model.viewInfo.border?.radius)

                    addView(button)

                    var isLoaded = false

                    // Falling back to the screen dimensions keeps the image as large as possible,
                    // while still allowing for sampling to occur.
                    val fallbackWidth = ResourceUtils.getDisplayWidthPixels(context)
                    val fallbackHeight = ResourceUtils.getDisplayHeightPixels(context)
                    fun loadImage(url: String) = UAirship.shared().imageLoader.load(context,
                        button,
                        ImageRequestOptions.newBuilder(url).setImageLoadedCallback { success ->
                            if (success) {
                                isLoaded = true
                            }
                        }.setFallbackDimensions(fallbackWidth, fallbackHeight).build()
                    )

                    loadImage(url)

                    // Listen for visibility changes to load images for default GONE views,
                    // once they become visible and have a measured size.
                    visibilityChangeListener = object : BaseView.VisibilityChangeListener {
                        override fun onVisibilityChanged(visibility: Int) {
                            if (visibility == View.VISIBLE && !isLoaded) {
                                loadImage(url)
                            }
                        }
                    }
                }
            }
            is Image.Icon -> {
                with (button) {
                    // Icons are always fit-center
                    scaleType = FIT_CENTER
                    // Set baked-in drawable
                    setImageDrawable(image.getDrawable(context, isEnabled))
                    // Set a tint list to handle pressed/enabled colors
                    imageTintList = LayoutUtils.pressedColorStateList(image.tint.resolve(context))
                }

                // Add a small unbounded ripple effect to the icon
                applyIconRippleEffect(button, model.viewInfo.tapEffect)

                addView(button)
            }
        }

        val baseBackground = this.background
        model.listener = object : ButtonModel.Listener {
            override fun setEnabled(enabled: Boolean) {
                // Enable or disable the button view directly
                button.isEnabled = enabled
            }

            override fun setVisibility(visible: Boolean) {
                // Set visibility on the entire view
                this@ImageButtonView.isVisible = visible
            }

            override fun dismissSoftKeyboard() =
                LayoutUtils.dismissSoftKeyboard(this@ImageButtonView)

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@ImageButtonView, baseBackground, old, new)
            }
        }
    }

    override fun taps(): Flow<Unit> = button.debouncedClicks()

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        visibilityChangeListener?.onVisibilityChanged(visibility)
    }

    private fun makeImageButton(model: ImageButtonModel): CropImageButton {
        return CropImageButton(context).apply {
            id = model.buttonViewId
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true

            // Clear any padding so that we can use the entire view area
            setPadding(0, 0, 0, 0)

            // Set the content description, resolving localization if necessary
            model.contentDescription(context)?.ifNotEmpty {
                contentDescription = it
            }
        }
    }

    private fun applyImageRippleEffect(view: ImageButton, tapEffect: TapEffect, radius: Int?) {
        when (tapEffect) {
            is TapEffect.Default ->
                LayoutUtils.applyImageButtonRippleAndTint(view, radius)
            is TapEffect.None ->
                view.background = null
        }
    }

    private fun applyIconRippleEffect(view: ImageButton, tapEffect: TapEffect) {
        view.background = when (tapEffect) {
            is TapEffect.Default ->
                ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple)
            is TapEffect.None -> null
        }
    }
}
