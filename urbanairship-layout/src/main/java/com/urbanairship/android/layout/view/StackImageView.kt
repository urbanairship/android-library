/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.ImageView.ScaleType.FIT_CENTER
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.StackItemInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.StackImageViewModel
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.ThomasImageSizeResolver
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.CropImageView
import com.urbanairship.images.ImageRequestOptions

/**
 * Non-interactive visual counterpart to [StackImageButtonView].
 * Used for decorative elements, like a banner nub.
 */
internal class StackImageView(
    context: Context,
    private val model: StackImageViewModel,
    private val viewEnvironment: ViewEnvironment,
    private val itemProperties: ItemProperties?,
) : FrameLayout(context), BaseView {

    private var visibilityChangeListener: BaseView.VisibilityChangeListener? = null

    private val imageView: CropImageView by lazy { makeImageView() }

    init {
        model.contentDescription(context).ifNotEmpty { contentDescription = it }

        if (model.viewInfo.accessibilityHidden == true) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        renderItems(model.viewInfo.items)

        val baseBackground = this.background
        model.listener = object : BaseModel.Listener {
            override fun setEnabled(enabled: Boolean) {
                this@StackImageView.isEnabled = enabled
            }

            override fun onStateUpdated(state: ThomasState) {}

            override fun setVisibility(visible: Boolean) {
                this@StackImageView.isVisible = visible
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@StackImageView, baseBackground, old, new)
            }
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        visibilityChangeListener?.onVisibilityChanged(visibility)
    }

    private fun makeImageView(): CropImageView {
        return CropImageView(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true
            isClickable = false

            // Clear any padding so that we can use the entire view area
            setPadding(0, 0, 0, 0)
        }
    }

    private fun renderItems(items: List<StackItemInfo>) {
        items.forEach { item ->
            when (item) {
                is StackItemInfo.ShapeItem -> background = item.shape.getDrawable(context)
                is StackItemInfo.IconItem -> createOrUpdateIcon(item.icon as Image.Icon)
                is StackItemInfo.ImageItem -> createOrUpdateImage(item)
            }
        }
    }

    private fun createOrUpdateIcon(icon: Image.Icon) {
        with(imageView) {
            scaleType = FIT_CENTER
            setImageDrawable(icon.getDrawable(context, isEnabled))
            imageTintList = LayoutUtils.pressedColorStateList(icon.tint.resolve(context))
        }
        if (imageView.parent == null) {
            addView(imageView)
        }
    }

    private fun createOrUpdateImage(item: StackItemInfo.ImageItem) {
        val resolvedUrl = item.resolveUrl(ResourceUtils.isUiModeNight(context))
        val cached = viewEnvironment.imageCache()?.get(resolvedUrl)
        val url = cached?.path ?: resolvedUrl

        doOnAttach {
            val parentLayoutParams = layoutParams

            if (item.mediaFit == MediaFit.FIT_CROP) {
                imageView.setParentLayoutParams(parentLayoutParams)
                imageView.setImagePosition(item.cropPosition)
            } else {
                imageView.scaleType = item.mediaFit.scaleType
            }

            if (imageView.parent == null) {
                addView(imageView)
            }

            var isLoaded = false

            fun loadImage(url: String) = Airship.imageLoader.load(context,
                imageView,
                ImageRequestOptions.newBuilder(url)
                    .setImageSizeResolver(ThomasImageSizeResolver(itemProperties?.size, cached?.size))
                    .setImageLoadedCallback { success ->
                        if (success) {
                            isLoaded = true
                        } else {
                            UALog.w { "Failed to load image: $url" }
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
}
