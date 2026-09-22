/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.property.Background
import com.urbanairship.android.layout.property.BlendMode
import com.urbanairship.android.layout.property.Gradient
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.Parallax
import com.urbanairship.android.layout.property.ParallaxBackground
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.ThomasImageSizeResolver
import com.urbanairship.images.ImageRequestOptions
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws a `backgrounds` layer stack behind a host view's own content.
 *
 * Never participates in the host's measure/layout: [draw] is only ever called from the host's
 * `dispatchDraw`, after layout has already resolved [width]/[height] from the host's real
 * content -- exactly as it does when `backgrounds` doesn't exist. Each layer is sized to that
 * already-resolved box, never the other way around.
 *
 * The whole stack composites through one isolated offscreen layer, so a layer's `blend_mode`
 * only ever sees the layers drawn before it in this same stack -- never the host's own
 * `background_color`/`border`, and never whatever the host sits on top of in the wider scene.
 * Same idea as `.compositingGroup()` on iOS or `isolation: isolate` in CSS.
 *
 * An image `MediaBackground` reuses [CropImageView] and the existing image loader, so its crop
 * math and caching stay identical to a real `media` view. Video needs a real attached view to
 * play at all, so it's never drawn here -- see [attachedViewBackgrounds]. A layer stacked after
 * such a view draws straight onto the host's canvas rather than into another isolated layer of
 * its own, so its `blend_mode` sees that view's real pixels -- see [drawLayersAfter].
 *
 * `parallax` is read here, but only ever has anything to read from: `Pager`/`ScrollLayout` are
 * the only hosts that ever call [setParallaxBackgrounds] and update [offsetPx] -- the other four
 * hosts only ever call the plain [backgrounds] setter, which leaves every layer's parallax `null`,
 * so [drawLayer] never has anything to offset for them. `offsetPx` is the driving host's own
 * scroll/drag position: `scrollY`/`scrollX` for `ScrollLayout` (one continuous region, correctly
 * unbounded), or, for `Pager`, the offset from the *nearest* page rather than the absolute offset
 * from page 0 -- a pager has discrete pages, so the absolute figure is a full page-width even at
 * complete rest on any page but the first, which would apply a permanent translate with no drag
 * involved. Not a separately-timed animation. Re-deriving the translate from
 * that same live position on every redraw, rather than running a second animation matched to it by
 * duration, is what keeps this in step with drag *and* whatever settle/fling curve the host's own
 * scrolling mechanism uses, automatically.
 */
internal class BackgroundsPainter(
    private val context: Context,
    private val viewEnvironment: ViewEnvironment,
    private val invalidate: () -> Unit
) {

    private data class Layer(val background: Background, val parallax: Parallax?)

    private var layers: List<Layer> = emptyList()

    /** `null`/empty draws nothing. Replacing the list drops any in-flight image loads. */
    var backgrounds: List<Background>? = null
        set(value) {
            field = value
            layers = value.orEmpty().map { Layer(it, null) }
            mediaViews.clear()
        }

    /** Only `Pager`/`ScrollLayout` ever call this -- see the class doc. */
    fun setParallaxBackgrounds(value: List<ParallaxBackground>?) {
        layers = value.orEmpty().map { Layer(it.background, it.parallax) }
        mediaViews.clear()
    }

    /**
     * The driving host's current scroll/drag position, in pixels. `0` by default. Setting this
     * does not itself redraw -- the host's own scroll/drag callback already invalidates.
     */
    var offsetPx: Float = 0f

    /**
     * Layers rendered by the host as a real attached child view instead of by this class's own
     * canvas painting -- currently only ever a video [Background.MediaBackground], since video
     * needs a real, attached WebView to play at all (see `PagerView`). Splits [drawLayersBefore]/
     * [drawLayersAfter] around them so a host that owns such a child can still paint the rest of
     * the stack in the right order relative to it; a host with none set (the common case) draws
     * everything through [draw] as before.
     */
    var attachedViewBackgrounds: Set<Background> = emptySet()

    private val mediaViews = mutableMapOf<Background.MediaBackground, CropImageView>()

    fun draw(canvas: Canvas, width: Int, height: Int) {
        drawLayers(canvas, width, height, layers)
    }

    /** Layers up to (not including) the first of [attachedViewBackgrounds] in stack order. */
    fun drawLayersBefore(canvas: Canvas, width: Int, height: Int) {
        val firstAttached = layers.indexOfFirst { it.background in attachedViewBackgrounds }
        drawLayers(canvas, width, height, if (firstAttached < 0) layers else layers.subList(0, firstAttached))
    }

    /**
     * Layers after the last of [attachedViewBackgrounds] in stack order.
     *
     * Drawn directly onto [canvas], unlike [drawLayersBefore] -- by the time this runs, the
     * attached child has already composited its live pixels into this same [canvas] (see
     * `PagerView.drawChild`), and a `blend_mode` here needs to read those back. Wrapping this in
     * its own [Canvas.saveLayer] the way [drawLayersBefore] does would isolate it into a fresh,
     * blank buffer instead -- blending against nothing rather than against the attached view.
     */
    fun drawLayersAfter(canvas: Canvas, width: Int, height: Int) {
        val lastAttached = layers.indexOfLast { it.background in attachedViewBackgrounds }
        val toDraw = if (lastAttached < 0) emptyList() else layers.subList(lastAttached + 1, layers.size)
        if (toDraw.isEmpty() || width <= 0 || height <= 0) return
        toDraw.forEach { layer -> drawLayer(canvas, layer, width, height) }
    }

    /**
     * The translate a parallaxing layer needs for the current [offsetPx] -- shared by
     * [drawLayer] and by a host positioning one of [attachedViewBackgrounds] as a real child, so
     * both move by the same amount.
     *
     * Clamped to [width]/[height] on the axis(es) [parallax] moves along: [offsetPx] is
     * unbounded for a driving host with a continuous scroll region (`ScrollLayout`), and a
     * `factor` is only ever documented up to a magnitude of `1` (see [Parallax]), so without a
     * clamp a layer could be asked to travel arbitrarily far from the box it's drawn into.
     * Paired with [parallaxMargins], which oversizes that same layer by exactly this much, so the
     * clamped range never outruns the content and exposes a gap at the box's edge.
     */
    fun parallaxTranslation(parallax: Parallax?, width: Int, height: Int): Pair<Float, Float> {
        val dx = if (parallax != null && parallax.axis != Parallax.Axis.VERTICAL) {
            (-parallax.factor * offsetPx).coerceIn(-width.toFloat(), width.toFloat())
        } else 0f
        val dy = if (parallax != null && parallax.axis != Parallax.Axis.HORIZONTAL) {
            (-parallax.factor * offsetPx).coerceIn(-height.toFloat(), height.toFloat())
        } else 0f
        return dx to dy
    }

    /**
     * The extra room a parallaxing layer needs on each side of [width]/[height], so it still
     * fully covers the box at the far end of [parallaxTranslation]'s clamped range.
     *
     * `0` on an axis [parallax] doesn't move along, or where [parallax] is `null`. Shared with a
     * host positioning one of [attachedViewBackgrounds] as a real child (see `PagerView`), so
     * that child is oversized and centered the same way [drawLayer] oversizes a canvas-painted
     * one.
     */
    fun parallaxMargins(parallax: Parallax?, width: Int, height: Int): Pair<Int, Int> {
        val marginX = if (parallax != null && parallax.axis != Parallax.Axis.VERTICAL) width else 0
        val marginY = if (parallax != null && parallax.axis != Parallax.Axis.HORIZONTAL) height else 0
        return marginX to marginY
    }

    private fun drawLayers(canvas: Canvas, width: Int, height: Int, toDraw: List<Layer>) {
        if (toDraw.isEmpty() || width <= 0 || height <= 0) return

        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        try {
            toDraw.forEach { layer -> drawLayer(canvas, layer, width, height) }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * Translates by [parallaxTranslation]'s clamped amount, offset by [parallaxMargins]' oversize
     * so the box sees the *oversized* layer's own center at rest (`offsetPx == 0`) -- the same
     * reasoning as centering any larger box over a smaller one. A layer with no [Layer.parallax]
     * gets zero margin on both axes, so this is a no-op translate and an unchanged (`width`,
     * `height`) draw size, exactly as before this existed.
     */
    private fun drawLayer(canvas: Canvas, layer: Layer, width: Int, height: Int) {
        val (dx, dy) = parallaxTranslation(layer.parallax, width, height)
        val (marginX, marginY) = parallaxMargins(layer.parallax, width, height)
        val drawWidth = width + 2 * marginX
        val drawHeight = height + 2 * marginY

        canvas.save()
        canvas.translate(dx - marginX, dy - marginY)
        try {
            when (val background = layer.background) {
                is Background.ColorBackground -> drawColor(canvas, background, drawWidth, drawHeight)
                is Background.GradientBackground ->
                    drawGradient(canvas, background, width, height, drawWidth, drawHeight)
                is Background.MediaBackground -> drawMedia(canvas, background, drawWidth, drawHeight)
            }
        } finally {
            canvas.restore()
        }
    }

    private fun drawColor(canvas: Canvas, layer: Background.ColorBackground, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = layer.color.resolve(context)
        applyBlendMode(paint, layer.blendMode)
        // The color's own alpha and the layer's opacity both apply -- multiply rather than replace.
        paint.alpha = (paint.alpha * layer.opacity.orFullyOpaque()).roundToInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /**
     * [shaderWidth]/[shaderHeight] shape the gradient itself (so it doesn't stretch when a
     * parallaxing layer draws oversized), while [drawWidth]/[drawHeight] size the rect it's
     * painted onto -- `CLAMP` tiling carries the end-stop color out into that extra margin.
     */
    private fun drawGradient(
        canvas: Canvas,
        layer: Background.GradientBackground,
        shaderWidth: Int,
        shaderHeight: Int,
        drawWidth: Int,
        drawHeight: Int
    ) {
        val gradient = layer.gradient
        if (gradient.stops.isEmpty()) return

        val colors = gradient.stops.map { it.color.resolve(context) }.toIntArray()
        val positions = gradient.stops.map { it.position }.toFloatArray()

        val shader: Shader = when (gradient.style) {
            Gradient.Style.LINEAR -> {
                val line = linearGradientLine(gradient.angle ?: 0f, shaderWidth.toFloat(), shaderHeight.toFloat())
                LinearGradient(line.x0, line.y0, line.x1, line.y1, colors, positions, Shader.TileMode.CLAMP)
            }
            Gradient.Style.RADIAL -> {
                val radius = hypot(shaderWidth / 2f, shaderHeight / 2f).coerceAtLeast(1f)
                RadialGradient(shaderWidth / 2f, shaderHeight / 2f, radius, colors, positions, Shader.TileMode.CLAMP)
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        applyBlendMode(paint, layer.blendMode)
        // `Paint.alpha` modulates a shader's output the same way it does a flat color.
        paint.alpha = (255 * layer.opacity.orFullyOpaque()).roundToInt()
        canvas.drawRect(0f, 0f, drawWidth.toFloat(), drawHeight.toFloat(), paint)
    }

    private fun drawMedia(canvas: Canvas, layer: Background.MediaBackground, width: Int, height: Int) {
        // Video/YouTube/Vimeo need a real attached view to play at all -- a host that supports
        // them positions one itself (see attachedViewBackgrounds) and never reaches this branch
        // for that layer. A host that doesn't opt in just doesn't render it, same as before.
        if (layer.mediaType != MediaType.IMAGE) return

        val imageView = mediaViews.getOrPut(layer) { createCropImageView(layer, width, height) }
        // Re-affirmed on every draw in case the box has changed size since creation. Harmless to
        // repeat -- the load itself, below, only ever happens once per layer.
        imageView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        imageView.layout(0, 0, width, height)

        val opacity = layer.opacity.orFullyOpaque()
        val hasBlend = layer.blendMode != null && layer.blendMode != BlendMode.NORMAL
        if (opacity >= 1f && !hasBlend) {
            imageView.draw(canvas)
            return
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        applyBlendMode(paint, layer.blendMode)
        paint.alpha = (255 * opacity).roundToInt()
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), paint)
        try {
            imageView.draw(canvas)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * A detached `CropImageView` -- never added to the host's child list, so it can never be
     * seen by the host's own measure/layout, only manually sized here in [drawMedia].
     *
     * Measured and laid out to [width]/[height] *before* the image loader is asked to load,
     * critically -- the loader's `Target.getSize()` reads `view.width`/`view.height` synchronously
     * on this same call, and a detached view's `ViewTreeObserver` never fires, so if it fell back
     * to `addOnPreDrawListener` instead, the load would never be sized and would never complete.
     */
    private fun createCropImageView(
        layer: Background.MediaBackground,
        width: Int,
        height: Int
    ): CropImageView {
        val imageView = CropImageView(context)
        imageView.adjustViewBounds = true
        val layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        imageView.layoutParams = layoutParams

        // A background always fills its box, so it's always treated as the 100%/100% case --
        // same fit math a real `media` view uses, just always with a full box to crop into.
        if (layer.mediaFit == MediaFit.FIT_CROP) {
            imageView.setParentLayoutParams(layoutParams)
            imageView.setImagePosition(layer.position)
        } else {
            imageView.scaleType = layer.mediaFit.scaleType
        }

        imageView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        imageView.layout(0, 0, width, height)

        val resolvedUrl = layer.resolveUrl(ResourceUtils.isUiModeNight(context))
        val cached = viewEnvironment.imageCache()?.get(resolvedUrl)
        val url = cached?.path ?: resolvedUrl

        val options = ImageRequestOptions.newBuilder(url)
            .setImageSizeResolver(ThomasImageSizeResolver(null, cached?.size))
            .setImageLoadedCallback { invalidate() }
            .build()
        Airship.imageLoader.load(context, imageView, options)

        return imageView
    }

    private fun Float?.orFullyOpaque(): Float = (this ?: 1f).coerceIn(0f, 1f)

    private fun applyBlendMode(paint: Paint, mode: BlendMode?) {
        if (mode == null || mode == BlendMode.NORMAL) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = mode.toPlatformBlendMode()
        } else {
            // No Porter-Duff equivalent below API 29 for color_dodge/color_burn/hard_light/
            // soft_light/difference/exclusion -- draws as normal there rather than wrong.
            mode.toLegacyPorterDuffMode()?.let { paint.xfermode = PorterDuffXfermode(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun BlendMode.toPlatformBlendMode(): android.graphics.BlendMode = when (this) {
        BlendMode.NORMAL -> android.graphics.BlendMode.SRC_OVER
        BlendMode.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
        BlendMode.SCREEN -> android.graphics.BlendMode.SCREEN
        BlendMode.OVERLAY -> android.graphics.BlendMode.OVERLAY
        BlendMode.DARKEN -> android.graphics.BlendMode.DARKEN
        BlendMode.LIGHTEN -> android.graphics.BlendMode.LIGHTEN
        BlendMode.COLOR_DODGE -> android.graphics.BlendMode.COLOR_DODGE
        BlendMode.COLOR_BURN -> android.graphics.BlendMode.COLOR_BURN
        BlendMode.HARD_LIGHT -> android.graphics.BlendMode.HARD_LIGHT
        BlendMode.SOFT_LIGHT -> android.graphics.BlendMode.SOFT_LIGHT
        BlendMode.DIFFERENCE -> android.graphics.BlendMode.DIFFERENCE
        BlendMode.EXCLUSION -> android.graphics.BlendMode.EXCLUSION
    }

    private fun BlendMode.toLegacyPorterDuffMode(): PorterDuff.Mode? = when (this) {
        BlendMode.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        BlendMode.SCREEN -> PorterDuff.Mode.SCREEN
        BlendMode.OVERLAY -> PorterDuff.Mode.OVERLAY
        BlendMode.DARKEN -> PorterDuff.Mode.DARKEN
        BlendMode.LIGHTEN -> PorterDuff.Mode.LIGHTEN
        else -> null
    }

    private data class GradientLine(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    /**
     * The gradient line for a CSS-style angle: `0` points up, increasing clockwise (`90` right,
     * `180` down, `270` left). Length follows the CSS formula for a box gradient line -- the
     * distance between the perpendicular lines through the box's corners -- so the gradient
     * reaches corner to corner rather than edge to edge on a non-square box.
     */
    private fun linearGradientLine(angleDegrees: Float, width: Float, height: Float): GradientLine {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = sin(radians).toFloat()
        val dy = -cos(radians).toFloat()
        val length = abs(width * dx) + abs(height * dy)
        val cx = width / 2f
        val cy = height / 2f
        return GradientLine(
            x0 = cx - dx * length / 2f,
            y0 = cy - dy * length / 2f,
            x1 = cx + dx * length / 2f,
            y1 = cy + dy * length / 2f
        )
    }
}
