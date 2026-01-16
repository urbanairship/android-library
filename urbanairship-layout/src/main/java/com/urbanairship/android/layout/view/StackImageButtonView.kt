/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.ImageButton
import android.widget.ImageView.ScaleType.FIT_CENTER
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.forEach
import androidx.core.view.isVisible
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.StackItemInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.ImageButtonModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.model.StackImageButtonModel
import com.urbanairship.android.layout.property.CheckboxStyle
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.shape.Shape
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.dpToPx
import com.urbanairship.android.layout.util.ThomasImageSizeResolver
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.CropImageButton
import com.urbanairship.android.layout.widget.ShapeButton
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.images.ImageRequestOptions
import kotlinx.coroutines.flow.Flow

internal class StackImageButtonView(
    context: Context,
    private val model: StackImageButtonModel,
    private val viewEnvironment: ViewEnvironment,
    private val itemProperties: ItemProperties?,
) : FrameLayout(context), BaseView, TappableView {

    private var visibilityChangeListener: BaseView.VisibilityChangeListener? = null

    private var lastState: StackImageButtonModel.ResolvedState? = null

    private val button: CropImageButton by lazy { makeImageButton(model) }
    private var children = ArrayList<View>()

    init {
        model.viewInfo.items.forEach { item ->
            when (item) {
                is StackItemInfo.ShapeItem -> background = buildLayerDrawable(context, item.shape)
                is StackItemInfo.IconItem -> {
                    createOrUpdateIcon(item.icon as Image.Icon)
                }
                is StackItemInfo.ImageItem -> {
                    createOrUpdateImage(item)
                }
            }
        }

        val baseBackground = this.background
        model.listener = object : ButtonModel.Listener {
            override fun setEnabled(enabled: Boolean) {
                if (button.isEnabled != enabled) {
                    button.isEnabled = enabled
                    this@StackImageButtonView.alpha = if (enabled) 1.0f else LayoutUtils.MATERIAL_ALPHA_DISABLED
                }
            }

            override fun onStateUpdated(state: ThomasState) {
                updateButton(state)
            }

            override fun setVisibility(visible: Boolean) {
                this@StackImageButtonView.isVisible = visible
            }

            override fun dismissSoftKeyboard() =
                LayoutUtils.dismissSoftKeyboard(this@StackImageButtonView)

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@StackImageButtonView, baseBackground, old, new)
            }
        }
    }

    override fun taps(): Flow<Unit> = button.debouncedClicks()

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        visibilityChangeListener?.onVisibilityChanged(visibility)
    }

    private fun buildLayerDrawable(
        context: Context, shape: Shape): Drawable {
        return shape.getDrawable(context)
    }

    private fun makeImageButton(model: StackImageButtonModel): CropImageButton {
        return CropImageButton(context).apply {
            id = model.buttonViewId
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true

            // Clear any padding so that we can use the entire view area
            setPadding(0, 0, 0, 0)

            // Set the content description using resolved content description
            model.resolveContentDescription(context, null)?.ifNotEmpty {
                contentDescription = it
            }
        }
    }

    private fun updateButton(state: ThomasState?) {
        val resolvedState = model.resolveState(context, state)
        if (resolvedState == this.lastState) {
            return
        } else {
            resolvedState.items?.forEach {
                when (it) {
                    is StackItemInfo.ShapeItem -> background = buildLayerDrawable(context, it.shape)
                    is StackItemInfo.IconItem -> {
                        createOrUpdateIcon(it.icon as Image.Icon)
                    }
                    is StackItemInfo.ImageItem -> createOrUpdateImage(it)
                }
            }

            // Update content description based on resolved state
            val newContentDescription = model.resolveContentDescription(context, state)
            if (newContentDescription != button.contentDescription) {
                button.contentDescription = newContentDescription
            }
        }

        this.lastState = resolvedState
    }

    private fun createOrUpdateIcon(icon: Image.Icon) {
        with(button) {
            scaleType = FIT_CENTER
            setImageDrawable(icon.getDrawable(context, isEnabled))
            imageTintList =
                LayoutUtils.pressedColorStateList(icon.tint.resolve(context))
        }
        applyIconRippleEffect(button, model.viewInfo.tapEffect)
        if (children.isNotEmpty()) {
            children.removeAll(children)
            removeView(button)
        }
        children.add(button)
        addView(button)
    }

    private fun createOrUpdateImage(item: StackItemInfo.ImageItem) {
        val cached = viewEnvironment.imageCache()?.get(item.imageUrl)
        val url = cached?.path ?: item.imageUrl

        doOnAttach {
            val parentLayoutParams = layoutParams

            if (item.mediaFit == MediaFit.FIT_CROP) {
                button.setParentLayoutParams(parentLayoutParams)
                button.setImagePosition(item.cropPosition)
            } else {
                button.scaleType = item.mediaFit.scaleType
            }

            applyImageRippleEffect(button, model.viewInfo.tapEffect, model.viewInfo.border?.radii { dpToPx(context, it) })

            if (children.isNotEmpty()) {
                children.removeAll(children)
                removeView(button)
            }
            children.add(button)
            addView(button)

            var isLoaded = false

            fun loadImage(url: String) = Airship.imageLoader.load(context,
                button,
                ImageRequestOptions.newBuilder(url)
                    .setImageSizeResolver(ThomasImageSizeResolver(itemProperties?.size, cached?.size))
                    .setImageLoadedCallback { success ->
                        if (success) {
                            isLoaded = true
                        }
                    }
                    .build()
            )

            loadImage(url)

            // Listen for visibility changes to load images for default GONE views,
            // once they become visible and have a measured size.
            visibilityChangeListener = object : BaseView.VisibilityChangeListener {
                override fun onVisibilityChanged(visibility: Int) {
                    if (visibility == VISIBLE && !isLoaded) {
                        loadImage(url)
                    }
                }
            }
        }
    }

    private fun applyImageRippleEffect(view: ImageButton, tapEffect: TapEffect, radii: FloatArray?) {
        when (tapEffect) {
            is TapEffect.Default ->
                LayoutUtils.applyImageButtonRippleAndTint(view, radii)
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
