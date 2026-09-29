/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.core.graphics.Insets
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.GridLayoutItemInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.GridLayoutModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.property.everyConfigHasRatio
import com.urbanairship.android.layout.util.GridEnvironment
import com.urbanairship.android.layout.util.GridPlacement
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.gridColumnCount
import com.urbanairship.android.layout.util.isLayoutRtl
import com.urbanairship.android.layout.util.gridMinColumnWidth
import com.urbanairship.android.layout.util.placeGridItems
import com.urbanairship.android.layout.util.resolveGridConfig
import com.urbanairship.android.layout.widget.AutoSizeProvider
import com.urbanairship.android.layout.widget.BackgroundsPainter
import com.urbanairship.android.layout.widget.Clippable
import com.urbanairship.android.layout.widget.ClippableViewDelegate
import com.urbanairship.android.layout.widget.FillsOfferedWidth
import com.urbanairship.android.layout.widget.LengthBasisProvider
import com.urbanairship.android.layout.widget.ShrinkableView
import com.urbanairship.android.layout.widget.VideoBackgroundChildren
import com.urbanairship.android.layout.widget.backgroundsBoxHeight
import com.urbanairship.android.layout.widget.backgroundsBoxWidth
import com.urbanairship.android.layout.widget.drawInsideStroke
import com.urbanairship.android.layout.widget.establishesLength
import com.urbanairship.android.layout.widget.paintBackgroundsBox
import com.urbanairship.android.layout.widget.statesLength
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lays its items out in rows of equal-width cells whose column count comes from its own width.
 *
 * Every item is a [GridCell] child for the life of the view: a hidden item's cell is laid out
 * empty rather than removed, so showing or hiding one never recreates the cells after it.
 */
internal class GridLayoutView(
    context: Context,
    private val model: GridLayoutModel,
    viewEnvironment: ViewEnvironment
) : ViewGroup(context), BaseView, Clippable, ShrinkableView, LengthBasisProvider, FillsOfferedWidth {

    private val clippableViewDelegate = ClippableViewDelegate()

    // Only ever painted, never measured or laid out -- see `BackgroundsPainter`.
    private val backgroundsPainter = BackgroundsPainter(context, viewEnvironment) {
        // postInvalidate(), not invalidate() -- the image loader's callback may run off the UI thread.
        postInvalidate()
    }

    private val videoBackgrounds = VideoBackgroundChildren(
        layers = model.viewInfo.backgrounds.orEmpty().map { it to null },
        models = model.videoBackgroundModels,
        context = context,
        viewEnvironment = viewEnvironment
    )

    private val cells: List<GridCell>

    /** Where each row starts below the first, from the last measure. */
    private var rowTops = IntArray(0)
    private var contentHeight = 0
    private var rowAlignment = VerticalPosition.TOP

    init {
        backgroundsPainter.backgrounds = model.viewInfo.backgrounds
        backgroundsPainter.attachedViewBackgrounds = videoBackgrounds.paintedByView
        videoBackgrounds.addTo(this)

        cells = model.items.map { item ->
            val itemView = item.model.createView(
                context, viewEnvironment, ItemProperties(item.info.size, item.info.position)
            )
            GridCell(context, item.info, itemView).also { addView(it) }
        }

        model.listener = object : BaseModel.Listener {
            override fun onStateUpdated(state: ThomasState) {
                backgroundsPainter.state = state
            }

            override fun setVisibility(visible: Boolean) {
                isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@GridLayoutView, old, new)
            }
        }

        if (model.viewInfo.isListView) {
            setUpListAccessibility()
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, _: WindowInsetsCompat ->
            val noInsets = WindowInsetsCompat.Builder()
                .setInsets(systemBars(), Insets.NONE)
                .build()
            cells.forEach { ViewCompat.dispatchApplyWindowInsets(it, noInsets) }
            noInsets
        }
    }

    override fun isShrinkable(): Boolean = model.isShrinkable

    /** Ratio rows take their height from the grid's width, but only every config setting one makes that certain. */
    override fun establishesLength(horizontal: Boolean): Boolean {
        if (!horizontal && model.viewInfo.config.everyConfigHasRatio) return true
        return cells.any { it.holdsItem && it.suppliesLength(horizontal) { view -> view.establishesLength(horizontal) } }
    }

    override fun statesLength(horizontal: Boolean): Boolean {
        if (!horizontal && model.viewInfo.config.everyConfigHasRatio) return true
        return cells.any { it.holdsItem && it.suppliesLength(horizontal) { view -> view.statesLength(horizontal) } }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom

        // `auto` fills whatever width is offered; only an unbounded spec leaves the grid without one.
        val innerWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            null
        } else {
            (MeasureSpec.getSize(widthMeasureSpec) - horizontalPadding).coerceAtLeast(0)
        }

        val environment = GridEnvironment(
            windowSize = ResourceUtils.getWindowSize(context),
            orientation = ResourceUtils.getOrientation(context),
            gridWidth = innerWidth?.let { it / density }
        )
        val config = resolveGridConfig(model.viewInfo.config, environment)
        val textScale = resources.configuration.fontScale
        val columns = gridColumnCount(config, innerWidth?.let { it / density }, textScale)
        val gap = config.columnSpacing * density
        val rowGap = (config.rowSpacing * density).roundToInt()
        rowAlignment = config.rowAlignment

        val shown = cells.filter { it.holdsItem }
        cells.forEach { if (!it.holdsItem) it.measure(exactly(0), exactly(0)) }

        // Against the same width as the grid's config, so a span and the count capping it agree.
        val spans = shown.map { resolveGridConfig(it.info.config, environment).columnSpan }
        val placements = placeGridItems(spans, columns, config.partialRowAlignment)
        shown.forEachIndexed { index, cell -> cell.placement = placements[index] }

        val cellWidth = when {
            innerWidth != null -> (innerWidth - (columns - 1) * gap) / columns
            else -> gridMinColumnWidth(config, textScale)?.let { it * density }
                ?: huggedColumnWidth(shown, gap)
        }.coerceAtLeast(0f)

        val hugsWidth = innerWidth == null && gridMinColumnWidth(config, textScale) == null
        shown.forEach { cell ->
            cell.hugsWidth = hugsWidth
            cell.heightFromRatio = config.cellAspectRatio != null
            val placement = cell.placement
            val start = placement.halfColumn * (cellWidth + gap) / 2
            val end = start + placement.span * cellWidth + (placement.span - 1) * gap
            cell.start = start.roundToInt()
            cell.cellWidth = end.roundToInt() - cell.start
        }

        val rows = shown.groupBy { it.placement.row }.toSortedMap().values.toList()
        val ratioRowHeight = config.cellAspectRatio?.let { (cellWidth / it).roundToInt() }
        rowTops = IntArray(rows.size)
        var top = 0
        rows.forEachIndexed { index, row ->
            if (index > 0) top += rowGap
            rowTops[index] = top
            top += measureRow(row, ratioRowHeight)
        }
        contentHeight = top

        val contentWidth = innerWidth
            ?: (columns * cellWidth + (columns - 1) * gap).roundToInt()

        setMeasuredDimension(
            if (innerWidth != null) MeasureSpec.getSize(widthMeasureSpec) else contentWidth + horizontalPadding,
            resolveSize(contentHeight + verticalPadding, heightMeasureSpec)
        )
    }

    /** With neither a width nor a minimum, every column is as wide as the widest item needs. */
    private fun huggedColumnWidth(shown: List<GridCell>, gap: Float): Float =
        shown.maxOfOrNull { cell ->
            cell.item.measure(unspecified(), unspecified())
            val needed = cell.item.measuredWidth + cell.horizontalMargins
            val span = cell.placement.span
            (needed - (span - 1) * gap) / span
        } ?: 0f

    /**
     * Measures one row's cells and returns the row's height.
     *
     * With a ratio every row is that tall. Otherwise a row is as tall as its tallest cell's
     * content, and a percentage height follows a horizontal stack's cross axis: a share of the row
     * where something in it has a height of its own, and otherwise its content, unless it is the
     * whole row.
     */
    private fun measureRow(row: List<GridCell>, ratioRowHeight: Int?): Int {
        if (ratioRowHeight != null) {
            row.forEach {
                it.measureItem(ratioRowHeight)
                it.measure(exactly(it.cellWidth), exactly(ratioRowHeight))
            }
            return ratioRowHeight
        }

        val hasBasis = row.any { it.establishesRowHeight() }
        val sharesOfRow = row.filter { cell ->
            val height = cell.info.size.height
            height.isPercent && (hasBasis || height.getFloat() >= 1f)
        }

        // Every cell's content counts toward the row, a share of it included: a `100%` label that
        // wraps grows its row, as a percentage item's content does an auto grid track on web.
        var rowHeight = 0
        row.forEach { cell ->
            cell.measureItem(rowHeight = null)
            rowHeight = max(rowHeight, cell.item.measuredHeight + cell.verticalMargins)
        }
        sharesOfRow.forEach { it.measureItem(rowHeight) }
        row.forEach { it.measure(exactly(it.cellWidth), exactly(rowHeight)) }
        return rowHeight
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val innerHeight = height - paddingTop - paddingBottom
        val spare = (innerHeight - contentHeight).coerceAtLeast(0)
        val offset = when (rowAlignment) {
            VerticalPosition.TOP -> 0
            VerticalPosition.CENTER -> spare / 2
            VerticalPosition.BOTTOM -> spare
        }
        val rtl = isLayoutRtl

        cells.forEach { cell ->
            if (!cell.holdsItem) {
                cell.layout(0, 0, 0, 0)
                return@forEach
            }
            val left = if (rtl) width - paddingRight - cell.start - cell.measuredWidth else paddingLeft + cell.start
            val top = paddingTop + offset + rowTops[cell.placement.row]
            cell.layout(left, top, left + cell.measuredWidth, top + cell.measuredHeight)
        }

        // Not a cell, so `onMeasure` never sized it.
        videoBackgrounds.placeInto(
            backgroundsPainter, backgroundsBoxWidth, backgroundsBoxHeight, paddingLeft, paddingTop
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Corner tiles would paint over the stroke, and rows taller than the grid past its edge.
        drawInsideStroke(canvas) {
            paintBackgroundsBox(canvas) { w, h -> backgroundsPainter.drawLayersBefore(canvas, w, h) }
            super.dispatchDraw(canvas)
        }
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val result = super.drawChild(canvas, child, drawingTime)
        // Layers above one drawn as a real view blend against its live pixels, so they go down here.
        val attachedIndex = videoBackgrounds.indexOf(child)
        if (attachedIndex >= 0) {
            paintBackgroundsBox(canvas) { w, h ->
                backgroundsPainter.drawLayersAfter(canvas, w, h, attachedIndex)
            }
        }
        return result
    }

    override fun shouldDelayChildPressedState(): Boolean = false

    override fun setClipPathBorderRadius(borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }

    override fun setClipPathBorderRadius(borderRadii: FloatArray?) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii)
    }

    /** Announces the grid as a list of its visible items, in item order rather than screen order. */
    private fun setUpListAccessibility() {
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = ListView::class.java.name
                info.setCollectionInfo(CollectionInfoCompat.obtain(cells.count { it.holdsItem }, 1, false))
                info.isScreenReaderFocusable = true
                info.isFocusable = true
            }
        })

        cells.forEach { cell ->
            ViewCompat.setAccessibilityDelegate(cell, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    if (!cell.holdsItem) return
                    val index = cells.filter { it.holdsItem }.indexOf(cell)
                    info.setCollectionItemInfo(CollectionItemInfoCompat.obtain(index, 1, 0, 1, false))
                    info.isScreenReaderFocusable = true
                }
            })
        }
    }

    /**
     * One grid cell: the item's slot, measured by the grid and holding the item at its [Size] within.
     *
     * The grid measures [item] itself, since a cell's height can depend on its row's other cells;
     * the cell only places what the grid measured.
     */
    private class GridCell(
        context: Context,
        val info: GridLayoutItemInfo,
        val item: View
    ) : ViewGroup(context), AutoSizeProvider {

        lateinit var placement: GridPlacement
        /** Offset from the grid's start edge. */
        var start = 0
        var cellWidth = 0

        private val margins = Rect()
        private val insideMargins = Rect()
        private val itemBounds = Rect()

        /** Whether the grid sets this cell's height from its width rather than from the row's items. */
        var heightFromRatio = false
            set(value) {
                field = value
                layoutParams.height = if (value) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
            }

        /** Whether this cell's width comes from its item, in a grid with neither a width nor a minimum. */
        var hugsWidth = false
            set(value) {
                field = value
                layoutParams.width = if (value) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
            }

        init {
            addView(item, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        /** Hidden items leave their cell empty, and later items close up the gap. */
        val holdsItem: Boolean get() = item.visibility != GONE

        val horizontalMargins: Int get() = resolveMargins().let { it.left + it.right }
        val verticalMargins: Int get() = resolveMargins().let { it.top + it.bottom }

        /** Whether this cell gives its row a height of its own, rather than taking a share of it. */
        fun establishesRowHeight(): Boolean {
            val height = info.size.height
            return height.isAbsolute ||
                (height.isAuto && (info.size.aspectRatio != null || item.establishesLength(horizontal = false)))
        }

        fun suppliesLength(horizontal: Boolean, auto: (View) -> Boolean): Boolean {
            val dimension = if (horizontal) info.size.width else info.size.height
            return when (dimension.type) {
                Size.DimensionType.ABSOLUTE -> true
                Size.DimensionType.PERCENT -> false
                Size.DimensionType.AUTO -> auto(item)
            }
        }

        /**
         * Measures the item against this cell's width and, where it is known, the row's height.
         *
         * @param rowHeight the row's height in pixels, or null while the row is sizing to its items.
         */
        fun measureItem(rowHeight: Int?) {
            val margin = resolveMargins()
            val innerWidth = (cellWidth - margin.left - margin.right).coerceAtLeast(0)
            val widthSpec = specFor(info.size.width, innerWidth, capToBase = true)

            val height = info.size.height
            val aspectRatio = info.size.aspectRatio
            if (height.isAuto && aspectRatio != null && aspectRatio > 0) {
                item.measure(widthSpec, unspecified())
                val width = item.measuredWidth
                item.measure(exactly(width), exactly((width / aspectRatio).roundToInt()))
                return
            }

            val heightSpec = when {
                rowHeight == null -> when {
                    height.isAbsolute -> exactly(dpToPx(height.getInt()))
                    else -> unspecified()
                }
                else -> specFor(
                    height, (rowHeight - margin.top - margin.bottom).coerceAtLeast(0),
                    capToBase = false
                )
            }
            item.measure(widthSpec, heightSpec)
        }

        /**
         * Returns the spec for one axis of the item against [base], the cell less its margins.
         *
         * A percentage is a share of [base]; with [capToBase] a fixed length never exceeds it, so an item
         * never overflows into the next cell.
         */
        private fun specFor(dimension: Size.Dimension, base: Int, capToBase: Boolean): Int =
            when (dimension.type) {
                Size.DimensionType.PERCENT -> exactly((dimension.getFloat() * base).roundToInt())
                Size.DimensionType.ABSOLUTE -> dpToPx(dimension.getInt()).let {
                    exactly(if (capToBase) min(it, base) else it)
                }
                Size.DimensionType.AUTO -> MeasureSpec.makeMeasureSpec(base, MeasureSpec.AT_MOST)
            }

        private fun resolveMargins(): Rect {
            val margin = info.margin
            val start = dpToPx(margin?.start ?: 0)
            val end = dpToPx(margin?.end ?: 0)
            val rtl = isLayoutRtl
            margins.set(
                if (rtl) end else start,
                dpToPx(margin?.top ?: 0),
                if (rtl) start else end,
                dpToPx(margin?.bottom ?: 0)
            )
            return margins
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec)
            )
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            if (!holdsItem) return
            val margin = resolveMargins()
            insideMargins.set(margin.left, margin.top, width - margin.right, height - margin.bottom)
            Gravity.apply(
                info.position.getGravity(), item.measuredWidth, item.measuredHeight,
                insideMargins, itemBounds, layoutDirection
            )
            item.layout(itemBounds.left, itemBounds.top, itemBounds.right, itemBounds.bottom)
        }

        override fun isAutoSized(horizontal: Boolean): Boolean =
            if (horizontal) hugsWidth else !heightFromRatio

        override fun shouldDelayChildPressedState(): Boolean = false

        /** Converts as every other layout does, so a size lands on the same pixel in a grid or out of it. */
        private fun dpToPx(dp: Int): Int = ResourceUtils.dpToPx(context, dp).toInt()
    }

    private companion object {
        fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), MeasureSpec.EXACTLY)
        fun unspecified() = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    }
}
