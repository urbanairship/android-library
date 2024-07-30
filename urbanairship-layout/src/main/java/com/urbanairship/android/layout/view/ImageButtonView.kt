/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.urbanairship.UAirship
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.ImageButtonModel
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.util.ColorStateListBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.images.ImageRequestOptions
import kotlinx.coroutines.flow.Flow

internal class ImageButtonView(
    context: Context,
    model: ImageButtonModel,
    viewEnvironment: ViewEnvironment
) : AppCompatImageButton(context), BaseView, TappableView {

    private var visibilityChangeListener: BaseView.VisibilityChangeListener? = null

    init {
        background = ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple)
        isClickable = true
        isFocusable = true
        setPadding(0, 0, 0, 0)
        scaleType = ScaleType.FIT_CENTER

        LayoutUtils.applyBorderAndBackground(this, model)

        model.contentDescription.ifNotEmpty { contentDescription = it }

        val image = model.image
        when (image.type) {
            Image.Type.URL -> {
                var url = (image as Image.Url).url
                viewEnvironment.imageCache()[url]?.let { cachedImage ->
                    url = cachedImage
                }

                var isLoaded = false

                // Falling back to the screen dimensions keeps the image as large as possible,
                // while still allowing for sampling to occur.
                val fallbackWidth = ResourceUtils.getDisplayWidthPixels(context)
                val fallbackHeight = ResourceUtils.getDisplayHeightPixels(context)
                fun loadImage(url: String) = UAirship.shared().imageLoader
                    .load(context, this, ImageRequestOptions.newBuilder(url)
                        .setImageLoadedCallback { success ->
                            if (success) { isLoaded = true }
                        }
                        .setFallbackDimensions(fallbackWidth, fallbackHeight)
                        .build())

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
            Image.Type.ICON -> {
                val icon = image as Image.Icon
                setImageDrawable(icon.getDrawable(context, isEnabled))
                @ColorInt val normalColor = icon.tint.resolve(context)
                @ColorInt val pressedColor = LayoutUtils.generatePressedColor(normalColor)
                @ColorInt val disabledColor = LayoutUtils.generateDisabledColor(normalColor)
                imageTintList = ColorStateListBuilder()
                    .add(pressedColor, android.R.attr.state_pressed)
                    .add(disabledColor, -android.R.attr.state_enabled)
                    .add(normalColor)
                    .build()
            }
        }

        model.listener = object : ButtonModel.Listener {
            override fun setEnabled(enabled: Boolean) {
                this@ImageButtonView.isEnabled = enabled
            }

            override fun setVisibility(visible: Boolean) {
                this@ImageButtonView.isGone = visible
            }

            override fun dismissSoftKeyboard() =
                LayoutUtils.dismissSoftKeyboard(this@ImageButtonView)
        }
    }

    override fun taps(): Flow<Unit> = debouncedClicks()

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        visibilityChangeListener?.onVisibilityChanged(visibility)
    }
}
