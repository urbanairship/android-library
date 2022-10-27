/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.urbanairship.UAirship
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.ImageButtonModel
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.util.ColorStateListBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.images.ImageRequestOptions

internal class ImageButtonView(
    context: Context,
    private val model: ImageButtonModel,
    private val viewEnvironment: ViewEnvironment
) : AppCompatImageButton(context), BaseView {

    private val modelListener: ButtonModel.Listener = object : ButtonModel.Listener {
        override fun setEnabled(isEnabled: Boolean) {
            this@ImageButtonView.isEnabled = isEnabled
        }
    }

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        background = ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple)
        isClickable = true
        isFocusable = true
        setPadding(0, 0, 0, 0)
        scaleType = ScaleType.FIT_CENTER
        LayoutUtils.applyBorderAndBackground(this, model)
        model.setViewListener(modelListener)
        model.contentDescription.ifNotEmpty { contentDescription = it }

        val image = model.image
        when (image.type) {
            Image.Type.URL -> {
                var url = (image as Image.Url).url
                viewEnvironment.imageCache()[url]?.let { cachedImage ->
                    url = cachedImage
                }
                UAirship.shared().imageLoader
                    .load(context, this, ImageRequestOptions.newBuilder(url).build())
            }
            Image.Type.ICON -> {
                val icon = image as Image.Icon
                setImageDrawable(icon.getDrawable(context))
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

        setOnClickListener { model.onClick() }
    }
}
