package com.urbanairship.android.layout.util

import android.content.Context
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.Size.Dimension
import com.urbanairship.android.layout.util.ResourceUtils.dpToPx
import com.urbanairship.images.ImageSizeResolver

internal class ThomasImageSizeResolver(
    private val thomasSize: Size?,
    private val imageSize: android.util.Size?
): ImageSizeResolver {
    override fun resolveHeight(context: Context, measuredWidth: Int?): Int? {
        return calculateFallbackSize(context, thomasSize?.height) {
            if (measuredWidth == null || imageSize == null) {
                return@calculateFallbackSize null
            }

            ((measuredWidth * imageSize.height).toFloat()/imageSize.width.toFloat()).toInt()
        }
    }

    override fun resolveWidth(context: Context, measuredHeight: Int?): Int? {
        return calculateFallbackSize(context, thomasSize?.width) {
            if (measuredHeight == null || imageSize == null) {
                return@calculateFallbackSize null
            }

            ((measuredHeight * imageSize.width).toFloat()/imageSize.height.toFloat()).toInt()
        }
    }

    private fun calculateFallbackSize(
        context: Context,
        dimension: Dimension?,
        autoSize: () -> Int?
    ): Int? {
        return when (dimension?.type) {
            Size.DimensionType.AUTO ->  autoSize()
            Size.DimensionType.PERCENT -> null
            Size.DimensionType.ABSOLUTE -> dpToPx(context, dimension.int).toInt()
            null -> null
        }
    }

}
