/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.property.Background
import com.urbanairship.android.layout.property.BlendMode
import com.urbanairship.android.layout.property.Direction
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
 * A stack that blends composites through one isolated offscreen layer, so a layer's `blend_mode`
 * only ever sees the layers drawn before it in this same stack -- never the host's own
 * `background_color`/`border`, and never whatever the host sits on top of in the wider scene.
 * Same idea as `.compositingGroup()` on iOS or `isolation: isolate` in CSS.
 *
 * With one deliberate exception: a layer stacked *after* one drawn as a real view has to read
 * that view's live pixels back, which an isolated buffer would hide (see [drawLayersAfter]). Such
 * a layer blends against the host's own canvas, so its `blend_mode` sees whatever else is already
 * there, not only this stack.
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
 * absolute position: `scrollY`/`scrollX` for `ScrollLayout`, and the track's own offset from page
 * 0 for `Pager`. A pager's figure is a full page-width at complete rest on page 2, with no drag
 * involved, and that standing translate is the effect rather than something to cancel -- a
 * background is meant to have drifted once you are on a later page, and to stay drifted while you
 * sit there. Reporting each page's own relative offset instead rounds the effect away, leaving a
 * background that wobbles through a drag and lands back where it started. iOS
 * (`-(width * pageIndex) + drag`) and web (`currentIndex * -100%`) are both absolute for this same
 * reason. Not a separately-timed animation. Re-deriving the translate from
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

    private companion object {
        /**
         * Top to bottom, which is what a `gradient` with no `angle` means -- the same default
         * iOS (`angle ?? 180`) and web (`gradient.angle ?? 180`) apply. `0` would run it
         * bottom-to-top, rendering every angle-less gradient upside down.
         */
        const val DEFAULT_GRADIENT_ANGLE = 180f
    }

    private var layers: List<Layer> = emptyList()

    /** `null`/empty draws nothing. Replacing the list drops the views drawing the old one. */
    var backgrounds: List<Background>? = null
        set(value) {
            field = value
            layers = value.orEmpty().map { Layer(it, null) }
            mediaViews.clear()
        }

    /**
     * Only `Pager`/`ScrollLayout` ever call this -- see the class doc.
     *
     * @param direction the one axis this host can move a layer along: a pager drags
     *   horizontally, a `ScrollLayout` scrolls along its own `direction`.
     */
    fun setParallaxBackgrounds(value: List<ParallaxBackground>?, direction: Direction) {
        layers = value.orEmpty().map { Layer(it.background, it.parallax) }
        this.direction = direction
        mediaViews.clear()
    }

    /**
     * The axis the host moves layers along, or `null` for a host that moves none.
     *
     * A host has exactly one scroll or drag position, so this is the only axis any layer of its
     * can ever travel on -- which is why a layer states only a [Parallax.factor], never an axis
     * of its own.
     */
    private var direction: Direction? = null

    /**
     * Whether the host lays out right-to-left, which is the side its layers' spare room sits on.
     *
     * A layer rests against the host's *leading* edge with its slack trailing away behind it, and
     * leading is the right-hand side in a right-to-left layout. Set from the host's own
     * `layoutDirection` on every layout, since a scene can be shown either way.
     */
    var isRtl: Boolean = false

    /** How much of the host's own movement this layer takes, or `0` for one that doesn't move. */
    private fun factorFor(parallax: Parallax?): Float =
        if (direction == null) 0f else parallax?.factor ?: 0f

    /** The host's box along [direction]. */
    private fun boxAlongHostAxis(width: Int, height: Int): Int =
        if (direction == Direction.HORIZONTAL) width else height

    /** Pairs [value] with `0` on whichever axis the host doesn't drive. */
    private fun <T> alongHostAxis(value: T, zero: T): Pair<T, T> =
        if (direction == Direction.HORIZONTAL) value to zero else zero to value

    /**
     * The driving host's current scroll/drag position, in pixels. `0` by default. Setting this
     * does not itself redraw -- the host's own scroll/drag callback already invalidates.
     */
    var offsetPx: Float = 0f

    /**
     * The lowest and highest [offsetPx] will ever reach, which is what decides how much spare
     * room a layer is drawn with and on which side -- see [parallaxOversize].
     *
     * Every host states its own, and both travel one way from rest: a `ScrollLayout` from `0` to
     * its scrollable range -- nothing at all when its content already fits -- and a `Pager` from
     * `0` across its pages. So a layer is oversized on one side only, the side it travels toward.
     *
     * Stating it, rather than assuming the worst, is what keeps a `factor: 0.2` layer from being
     * oversized (and so zoomed, since `media_fit` crops into whatever box it is given) as if it
     * were `factor: 1` -- the same reason iOS reads `scrollRange` and web reads
     * `--parallax-y-range` rather than either taking the viewport's own size.
     */
    var offsetBoundsPx: ClosedFloatingPointRange<Float>? = null

    /**
     * Layers rendered by the host as a real attached child view instead of by this class's own
     * canvas painting -- currently only ever a video [Background.MediaBackground], since video
     * needs a real, attached WebView to play at all (see `PagerView`). Splits [drawLayersBefore]/
     * [drawLayersAfter] around them so a host that owns such a child can still paint the rest of
     * the stack in the right order relative to it; a host with none set (the common case) draws
     * everything through [draw] as before.
     */
    var attachedViewBackgrounds: Set<Background> = emptySet()

    /**
     * The scene's current state, which an image layer's `view_overrides` resolve against.
     *
     * Setting it reloads any layer the new state gives a different url -- a layer whose url it
     * doesn't change keeps the image it already has, rather than restarting its load on every
     * unrelated form edit. A host sets this from its model's `onStateUpdated`; a video layer is
     * its own `MediaModel`, which already gets the state that way.
     */
    var state: ThomasState? = null
        set(value) {
            field = value
            mediaViews.forEach { (layer, held) -> held.load(layer) }
        }

    /** An image layer's view, and the url currently in it. */
    private inner class HeldImage(val view: CropImageView) {
        private var loadedUrl: String? = null

        /** Loads [layer]'s url for the current [state], unless that is already what's showing. */
        fun load(layer: Background.MediaBackground) {
            val resolvedUrl = layer.mediaInfo.resolveUrl(state, ResourceUtils.isUiModeNight(context))
            if (resolvedUrl == loadedUrl) return
            loadedUrl = resolvedUrl

            val cached = viewEnvironment.imageCache()?.get(resolvedUrl)
            val options = ImageRequestOptions.newBuilder(cached?.path ?: resolvedUrl)
                .setImageSizeResolver(ThomasImageSizeResolver(null, cached?.size))
                // This view is never attached (see `createCropImageView`) -- a crossfade never
                // completes against it, so the drawable would silently never get set.
                .disableTransition()
                .setImageLoadedCallback { invalidate() }
                .build()
            Airship.imageLoader.load(context, view, options)
        }
    }

    private val mediaViews = mutableMapOf<Background.MediaBackground, HeldImage>()

    /** Whether any layer actually moves, so a host can skip repainting for an offset change. */
    val hasParallaxLayers: Boolean get() = layers.any { it.parallax != null && it.parallax.factor != 0f }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        drawLayers(canvas, width, height, layers)
    }

    /** Layers up to (not including) the first of [attachedViewBackgrounds] in stack order. */
    fun drawLayersBefore(canvas: Canvas, width: Int, height: Int) {
        val firstAttached = layers.indexOfFirst { it.background in attachedViewBackgrounds }
        drawLayers(canvas, width, height, if (firstAttached < 0) layers else layers.subList(0, firstAttached))
    }

    /**
     * The layers stacked between attached layer [attachedIndex] and whichever attached layer
     * comes next, or the end of the stack if there is none.
     *
     * Drawn directly onto [canvas], unlike [drawLayersBefore] -- by the time this runs the
     * attached child has already composited its live pixels into this same [canvas] (see a host's
     * `drawChild`), and a `blend_mode` here needs to read those back. Wrapping this in its own
     * [Canvas.saveLayer] the way [drawLayersBefore] does would isolate it into a fresh, blank
     * buffer instead -- blending against nothing rather than against the attached view.
     *
     * Taking the index rather than always running to the end of the stack is what lets a layer
     * sit between two attached ones and still be drawn.
     */
    fun drawLayersAfter(canvas: Canvas, width: Int, height: Int, attachedIndex: Int) {
        val attached = layers.indices.filter { layers[it].background in attachedViewBackgrounds }
        val start = attached.getOrNull(attachedIndex) ?: return
        val end = attached.getOrNull(attachedIndex + 1) ?: layers.size
        val toDraw = layers.subList(start + 1, end)
        if (toDraw.isEmpty() || width <= 0 || height <= 0) return

        // Drawn straight onto the host's canvas rather than into a layer of its own, so unlike
        // `drawLayers` there is no `saveLayer` bounding it -- a parallaxing layer's spare room
        // would otherwise paint over whatever sits beside the host in the wider scene.
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        try {
            // Drawn onto the host's own canvas, over an attached view that has already put its
            // pixels there -- so unlike the isolated stack, there is always something beneath.
            toDraw.forEach { layer -> drawLayer(canvas, layer, width, height, hasBackdrop = true) }
        } finally {
            canvas.restore()
        }
    }

    /**
     * The translate a parallaxing layer needs for the current [offsetPx] -- shared by
     * [drawLayer] and by a host positioning one of [attachedViewBackgrounds] as a real child, so
     * both move by the same amount.
     *
     * Clamped to exactly the spare room the layer was drawn with on the axis(es) [parallax] moves
     * since that is how much extra the layer was actually drawn with -- travelling further would
     * only pull it off its own edge. The clamp is what keeps a `ScrollLayout` honest either way:
     * its [offsetPx] is raw, unbounded `scrollY`/`scrollX`, so absent [offsetBoundsPx] telling it
     * otherwise, a long scroll would ask a layer to travel arbitrarily far from its box.
     */
    fun parallaxTranslation(parallax: Parallax?, width: Int, height: Int): Pair<Float, Float> {
        val factor = factorFor(parallax)
        if (factor == 0f) return 0f to 0f

        val (leading, trailing) = oversizeFor(factor, boxAlongHostAxis(width, height))
        val delta = (-factor * offsetPx).coerceIn(-trailing.toFloat(), leading.toFloat())

        // A layer travels *into* its own slack, and right-to-left puts that slack on the other
        // side (see `parallaxLeadingOversize`), so it has to travel the other way to reach it.
        // Mirroring where it rests without mirroring where it goes walks its trailing edge into
        // view, leaving a strip of whatever is underneath.
        val mirrored = if (isRtl && direction == Direction.HORIZONTAL) -delta else delta
        return alongHostAxis(mirrored, 0f)
    }

    /**
     * How much larger than [width]/[height] a parallaxing layer is drawn, in total across both
     * sides of each axis, so it still covers the box at either end of its own travel.
     *
     * `0` on an axis [parallax] doesn't move along, where [parallax] is `null`, and -- the case
     * that matters most -- where the driving host cannot actually travel at all, such as a
     * `ScrollLayout` whose content already fits without scrolling. A layer is only ever oversized
     * by what its own `factor` will really use, because `media_fit` crops into the box it is
     * given: oversize is zoom, so oversizing for travel that never happens reads as an image
     * zoomed in for no reason (and cropped tighter than the same scene on iOS or web).
     *
     * Shared with a host positioning one of [attachedViewBackgrounds] as a real child (see
     * `PagerView`), so that child is oversized the same way [drawLayer] oversizes a
     * canvas-painted one.
     */
    fun parallaxOversize(parallax: Parallax?, width: Int, height: Int): Pair<Int, Int> {
        val factor = factorFor(parallax)
        if (factor == 0f) return 0 to 0

        val (leading, trailing) = oversizeFor(factor, boxAlongHostAxis(width, height))
        return alongHostAxis(leading + trailing, 0)
    }

    /**
     * The share of [parallaxOversize] that sits before the box's own start on each axis -- what a
     * host positioning one of [attachedViewBackgrounds] as a real child needs to place it so its
     * unshifted (`offsetPx == 0`) position lines up with the box, the same way [drawLayer]'s
     * `dx - leadingX` translate does. Centering that child instead (an even split of the total
     * oversize) only matches this when [oversizeFor] happens to split evenly too -- it doesn't
     * whenever [offsetBoundsPx] isn't itself centered on `0`, which is the common case.
     */
    fun parallaxLeadingOversize(parallax: Parallax?, width: Int, height: Int): Pair<Int, Int> {
        val factor = factorFor(parallax)
        if (factor == 0f) return 0 to 0

        val (leading, trailing) = oversizeFor(factor, boxAlongHostAxis(width, height))
        // How much of the spare room sits to the *physical* left (or top) of the host's own box.
        // A layer rests against the leading edge, which a right-to-left layout puts on the right
        // -- so its slack hangs off the left there, and the whole layer sits shifted back by it.
        // iOS gets this from a `leading`-aligned frame, which resolves the same way.
        val beforeBox = if (isRtl && direction == Direction.HORIZONTAL) trailing else leading
        return alongHostAxis(beforeBox, 0)
    }

    /**
     * The spare room a layer moving at [factor] needs before the start of an axis [boxSize] long
     * and after its end, in pixels.
     *
     * A layer's own travel is `-factor * offsetPx` over [offsetBoundsPx]' span, so each side only
     * needs whatever that reaches in its own direction -- nothing at all on a side the layer
     * never moves toward. A `ScrollLayout`, which only ever scrolls away from rest in one
     * direction, is oversized on exactly one side as a result, matching iOS (one `.frame`
     * alignment edge) and web (`contentSize` growing only the trailing edge).
     */
    private fun oversizeFor(factor: Float, boxSize: Int): Pair<Int, Int> {
        val bounds = offsetBoundsPx ?: -boxSize.toFloat()..boxSize.toFloat()
        val atStart = -factor * bounds.start
        val atEnd = -factor * bounds.endInclusive
        // Only floored, never capped: a cap would be reached partway through the host's travel
        // and the layer would simply stop moving for the rest of it, with nothing on screen to
        // say why. `factor * travel` is what the layer actually uses, so that is what it gets.
        fun room(value: Float) = value.coerceAtLeast(0f).roundToInt()
        return room(maxOf(atStart, atEnd)) to room(-minOf(atStart, atEnd))
    }

    private fun drawLayers(canvas: Canvas, width: Int, height: Int, toDraw: List<Layer>) {
        if (toDraw.isEmpty() || width <= 0 || height <= 0) return

        // The isolation only shows when something actually blends against what's beneath it.
        // Without that, `saveLayer` is a full-size offscreen buffer allocated per frame -- on a
        // pager drag, per frame of the drag -- for a result identical to drawing straight on.
        val needsIsolation = toDraw.any {
            it.background.blendMode != null && it.background.blendMode != BlendMode.NORMAL
        }
        val saveCount = if (needsIsolation) {
            canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        } else {
            // `saveLayer` bounds every draw to that rect as well as isolating it; a plain `save`
            // isolates nothing *and* bounds nothing, so the box has to be clipped explicitly or
            // a layer drawn oversized for its parallax travel paints over whatever sits beside
            // the host.
            canvas.save().also { canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat()) }
        }
        try {
            toDraw.forEachIndexed { index, layer ->
                drawLayer(canvas, layer, width, height, hasBackdrop = index > 0)
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * Translates by [parallaxTranslation]'s clamped amount, shifted back by whatever spare room
     * [oversizeFor] put *before* the box's own start, so the box still sees the layer's resting
     * position at `offsetPx == 0` however that spare room is split. A layer with no
     * [Layer.parallax] -- or one whose host cannot travel -- is oversized by nothing, so this is
     * a no-op translate and an unchanged (`width`, `height`) draw size.
     */
    private fun drawLayer(canvas: Canvas, layer: Layer, width: Int, height: Int, hasBackdrop: Boolean) {
        val (dx, dy) = parallaxTranslation(layer.parallax, width, height)
        val (leadingX, leadingY) = parallaxLeadingOversize(layer.parallax, width, height)
        val (oversizeX, oversizeY) = parallaxOversize(layer.parallax, width, height)
        val drawWidth = width + oversizeX
        val drawHeight = height + oversizeY

        canvas.save()
        canvas.translate(dx - leadingX, dy - leadingY)
        try {
            when (val background = layer.background) {
                is Background.ColorBackground -> drawColor(canvas, background, drawWidth, drawHeight, hasBackdrop)
                is Background.GradientBackground ->
                    drawGradient(canvas, background, drawWidth, drawHeight, hasBackdrop)
                is Background.MediaBackground ->
                    drawMedia(canvas, background, width, height, drawWidth, drawHeight, hasBackdrop)
            }
        } finally {
            canvas.restore()
        }
    }

    private fun drawColor(
        canvas: Canvas,
        layer: Background.ColorBackground,
        width: Int,
        height: Int,
        hasBackdrop: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = layer.color.resolve(context)
        applyBlendMode(paint, layer.blendMode, hasBackdrop)
        // The color's own alpha and the layer's opacity both apply -- multiply rather than replace.
        paint.alpha = (paint.alpha * layer.opacity.orFullyOpaque()).roundToInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /**
     * The gradient is shaped to the whole rect it fills, spare parallax room included, so its
     * stops stay spread across the layer wherever that layer has travelled to.
     *
     * Shaping it to the box instead and offsetting it into the oversized rect leaves the stops
     * fixed to one part of the layer: since the rect is anchored at its leading edge and `CLAMP`
     * carries the end stops outward, the box then shows flat end-colour over whatever part of the
     * spare room it currently overlaps, and never reaches the far stop at all. Web sizes the
     * gradient to the same oversized element for this reason.
     */
    private fun drawGradient(
        canvas: Canvas,
        layer: Background.GradientBackground,
        drawWidth: Int,
        drawHeight: Int,
        hasBackdrop: Boolean
    ) {
        val gradient = layer.gradient
        if (gradient.stops.isEmpty()) return

        val colors = gradient.stops.map { it.color.resolve(context) }.toIntArray()
        val positions = gradient.stops.map { it.position }.toFloatArray()

        // A shader interpolates between stops, so it needs at least two of them -- `LinearGradient`
        // and `RadialGradient` both throw otherwise, and the DSL accepts a one-stop `stops` list.
        // One stop has nothing to interpolate toward: it is that colour, everywhere.
        val shader: Shader? = if (colors.size < 2) null else when (gradient.style) {
            Gradient.Style.LINEAR -> {
                val line = linearGradientLine(gradient.angle ?: DEFAULT_GRADIENT_ANGLE, drawWidth.toFloat(), drawHeight.toFloat())
                LinearGradient(line.x0, line.y0, line.x1, line.y1, colors, positions, Shader.TileMode.CLAMP)
            }
            Gradient.Style.RADIAL -> {
                val radius = hypot(drawWidth / 2f, drawHeight / 2f).coerceAtLeast(1f)
                RadialGradient(drawWidth / 2f, drawHeight / 2f, radius, colors, positions, Shader.TileMode.CLAMP)
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        if (shader == null) {
            paint.color = colors.first()
        }
        applyBlendMode(paint, layer.blendMode, hasBackdrop)
        // `Paint.alpha` modulates a shader's output the same way it does a flat color -- so for a
        // solid fill it has to carry the stop's own alpha, which a shader's output already has.
        val sourceAlpha = if (shader == null) Color.alpha(colors.first()) / 255f else 1f
        paint.alpha = (255 * sourceAlpha * layer.opacity.orFullyOpaque()).roundToInt()
        canvas.drawRect(0f, 0f, drawWidth.toFloat(), drawHeight.toFloat(), paint)
    }

    /**
     * `media_fit` crops into [drawWidth]/[drawHeight] -- the layer's own box *plus* whatever
     * [parallaxOversize] added for its travel -- not into the smaller box the host resolved to
     * box, which is only the gradient's own shaping size (see [drawGradient]).
     *
     * A cropping fit always fills the box it is given, so cropping into the oversized box is what
     * makes a gap at the end of the travel impossible: there is no size a source image can be
     * that leaves the box short. Cropping into the smaller box and translating that within the
     * larger one cannot promise this -- `cover` only ever leaves slack on whichever axis the
     * image happens to have proportionally more of, never necessarily the axis about to move, so
     * an image that merely isn't shaped like its box runs out mid-travel however large it is.
     * The cost is that a parallaxing layer sits a little more zoomed in than a non-parallaxing
     * sibling, in proportion to how far it can travel ([Parallax.factor] of [offsetBoundsPx]);
     * iOS (`ThomasParallaxBackgroundLayerView`, cropping into its own oversized `.frame`) and web
     * (`Backgrounds.tsx`, `object-fit: cover` on a `contentSize`-oversized `<img>`) both make
     * exactly this trade, and matching it is what keeps the three rendering the same scene alike.
     */
    private fun drawMedia(
        canvas: Canvas,
        layer: Background.MediaBackground,
        shaderWidth: Int,
        shaderHeight: Int,
        drawWidth: Int,
        drawHeight: Int,
        hasBackdrop: Boolean
    ) {
        // Video/YouTube/Vimeo need a real attached view to play at all -- a host that supports
        // them positions one itself (see attachedViewBackgrounds) and never reaches this branch
        // for that layer. A host that doesn't opt in just doesn't render it, same as before.
        if (layer.mediaType != MediaType.IMAGE) return
        if (shaderWidth <= 0 || shaderHeight <= 0) return

        // Requested (and so decoded) at the oversized size, not the layer's own box: a source
        // image with real spare resolution shouldn't be downsampled to the smaller box before a
        // parallaxing layer ever gets to reveal it. Only has any say the first time, when this
        // actually creates the view and starts the load.
        val imageView = mediaViews.getOrPut(layer) {
            HeldImage(createCropImageView(layer, drawWidth, drawHeight)).apply { load(layer) }
        }.view
        // Re-affirmed on every draw in case the box has changed size since creation. Harmless to
        // repeat -- a load only happens when the layer's resolved url actually changes.
        imageView.measure(
            MeasureSpec.makeMeasureSpec(drawWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(drawHeight, MeasureSpec.EXACTLY)
        )
        imageView.layout(0, 0, drawWidth, drawHeight)

        val opacity = layer.opacity.orFullyOpaque()
        val hasBlend = layer.blendMode != null && layer.blendMode != BlendMode.NORMAL

        if (opacity >= 1f && !hasBlend) {
            imageView.draw(canvas)
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        applyBlendMode(paint, layer.blendMode, hasBackdrop)
        paint.alpha = (255 * opacity).roundToInt()
        val saveCount = canvas.saveLayer(0f, 0f, drawWidth.toFloat(), drawHeight.toFloat(), paint)
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
     * Measured and laid out to [width]/[height] before [HeldImage.load] is called, critically --
     * the loader's `Target.getSize()` reads `view.width`/`view.height` synchronously on that
     * call, and a detached view's `ViewTreeObserver` never fires, so if it fell back to
     * `addOnPreDrawListener` instead, the load would never be sized and would never complete.
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

        return imageView
    }

    private fun Float?.orFullyOpaque(): Float = (this ?: 1f).coerceIn(0f, 1f)

    private fun applyBlendMode(paint: Paint, mode: BlendMode?, hasBackdrop: Boolean) {
        if (mode == null || mode == BlendMode.NORMAL) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = mode.toPlatformBlendMode()
            return
        }

        // A real blend mode composites source-over and only blends its *colour* with the
        // backdrop; the Porter-Duff stand-ins below multiply the alphas too, so with nothing
        // beneath them they resolve to fully transparent and erase the layer instead of drawing
        // it. Where there is no backdrop there is also nothing to blend with, so drawing it
        // plainly is both correct and what API 29+ does.
        if (!hasBackdrop) return

        // No Porter-Duff equivalent below API 29 for color_dodge/color_burn/hard_light/
        // soft_light/difference/exclusion -- draws as normal there rather than wrong.
        mode.toLegacyPorterDuffMode()?.let { paint.xfermode = PorterDuffXfermode(it) }
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
