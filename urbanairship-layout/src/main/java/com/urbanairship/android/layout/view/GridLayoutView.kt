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
import com.urbanairship.android.layout.environment.GridState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasCapabilities
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.GridCellInfo
import com.urbanairship.android.layout.info.GridLayoutItemInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.GridLayoutModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.property.GridItemConfig
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.util.GridPlacement
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.gridColumnCount
import com.urbanairship.android.layout.util.gridMaxColumnWidth
import com.urbanairship.android.layout.util.isLayoutRtl
import com.urbanairship.android.layout.util.gridMinColumnWidth
import com.urbanairship.android.layout.util.placeGridItems
import com.urbanairship.android.layout.util.spreadSpace
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

    /** The items' cells, in item order. */
    private val cells: List<GridCell>
    private val header: GridCell?
    private val footer: GridCell?

    /** Every cell, header and footer included, in reading order. */
    private val allCells: List<GridCell>

    /** The latest state at the grid, which its and its items' overrides match against. */
    private var state = ThomasState(State.Layout.DEFAULT, null, null, null, null, ThomasCapabilities.NONE)

    /** The inner width of the last measure, in points, which layout publishes as `$grids.current.width`. */
    private var measuredGridWidth: Float? = null

    private var contentHeight = 0
    /** How wide the block of columns is, which is less than the grid once `max_width` caps them. */
    private var blockWidth = 0
    private var contentPosition = Position(HorizontalPosition.START, VerticalPosition.TOP)

    init {
        backgroundsPainter.backgrounds = model.viewInfo.backgrounds
        backgroundsPainter.attachedViewBackgrounds = videoBackgrounds.paintedByView
        videoBackgrounds.addTo(this)

        fun cell(info: GridCellInfo, itemModel: AnyModel) =
            GridCell(
                context, info,
                itemModel.createView(context, viewEnvironment, ItemProperties(info.size, info.position))
            ).also { addView(it) }

        header = model.header?.let { cell(it.info, it.model) }
        cells = model.items.map { cell(it.info, it.model) }
        footer = model.footer?.let { cell(it.info, it.model) }
        allCells = listOfNotNull(header) + cells + listOfNotNull(footer)

        model.listener = object : BaseModel.Listener {
            override fun onStateUpdated(state: ThomasState) {
                backgroundsPainter.state = state
                this@GridLayoutView.state = state
                if (model.hasOverrides) requestLayout()
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
            allCells.forEach { ViewCompat.dispatchApplyWindowInsets(it, noInsets) }
            noInsets
        }
    }

    override fun isShrinkable(): Boolean = model.isShrinkable

    /** Ratio rows take their height from the grid's width, but only every config setting one makes that certain. */
    override fun establishesLength(horizontal: Boolean): Boolean {
        if (!horizontal && model.everyConfigHasRatio) return true
        if (horizontal && setsItsOwnWidthUnbounded) return true
        return cells.any { it.holdsItem && it.suppliesLength(horizontal) { view -> view.establishesLength(horizontal) } }
    }

    override fun statesLength(horizontal: Boolean): Boolean {
        if (!horizontal && model.everyConfigHasRatio) return true
        if (horizontal && setsItsOwnWidthUnbounded) return true
        return cells.any { it.holdsItem && it.suppliesLength(horizontal) { view -> view.statesLength(horizontal) } }
    }

    /**
     * Whether, with no width to divide, the grid's config alone sets its width: `fit` columns with
     * a minimum draw exactly `n × min + gaps`, whatever the items hold.
     */
    private val setsItsOwnWidthUnbounded: Boolean
        get() {
            val config = gridState(width = null).resolveRequired(model.viewInfo.viewOverrides?.config, model.viewInfo.config)
            return gridMinColumnWidth(config, resources.configuration.fontScale) != null
        }

    /** The state the grid's and its items' overrides match against, with the grid at `$grids.current`. */
    private fun gridState(width: Float?): ThomasState = state.copy(grid = GridState(model.viewInfo.identifier, width))


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

        measuredGridWidth = innerWidth?.let { it / density }
        // Matched against the width just measured, not the published one, which arrives a pass later.
        val gridState = gridState(measuredGridWidth)
        val config = gridState.resolveRequired(model.viewInfo.viewOverrides?.config, model.viewInfo.config)
        val textScale = resources.configuration.fontScale
        val columns = gridColumnCount(config, innerWidth?.let { it / density }, textScale)
        val minGap = config.columnSpacing.min * density
        val minRowGap = config.rowSpacing.min * density
        contentPosition = config.contentPosition

        val shown = cells.filter { it.holdsItem }
        // A hidden header or footer leaves no row.
        val edges = listOfNotNull(header, footer).filter { it.holdsItem }
        allCells.forEach { if (!it.holdsItem) it.measure(exactly(0), exactly(0)) }

        // Against the same width as the grid's config, so a span and the count capping it agree.
        // Sorted stably, so items of one order keep list (or shuffled) order.
        val ordered = shown
            .map { cell ->
                val itemConfig = cell.gridItem?.let { gridState.resolveRequired(it.overrides?.config, it.config) }
                cell to (itemConfig ?: GridItemConfig())
            }
            .sortedBy { (_, itemConfig) -> itemConfig.order }
        val placements = placeGridItems(ordered.map { it.second.columnSpan }, columns, config.partialRowPosition)
        ordered.forEachIndexed { index, (cell, _) ->
            cell.placement = placements[index]
            cell.readingIndex = index
        }
        edges.forEach { it.placement = GridPlacement(row = 0, halfColumn = 0, span = columns) }

        val share = innerWidth?.let { (it - (columns - 1) * minGap) / columns }
        val cap = gridMaxColumnWidth(config, textScale)?.let { it * density }
        // Past their cap the columns stop growing, and `content_position` places the block.
        val capped = share != null && cap != null && share > cap
        // Under `spread` spacing, what capped columns leave goes into the gaps instead.
        val gap = if (capped && innerWidth != null && cap != null) {
            spreadSpace(config.columnSpacing, (innerWidth - columns * cap) / density, columns)
                ?.let { it * density } ?: minGap
        } else {
            minGap
        }
        val cellWidth = when {
            capped -> cap
            share != null -> share
            else -> gridMinColumnWidth(config, textScale)?.let { it * density }
                ?: huggedColumnWidth(shown + edges, gap)
        }.coerceAtLeast(0f)

        val hugsWidth = innerWidth == null && gridMinColumnWidth(config, textScale) == null
        (shown + edges).forEach { cell ->
            cell.hugsWidth = hugsWidth
            // A header or footer keeps its own height beside ratio rows.
            cell.heightFromRatio = config.cellAspectRatio != null && cell.gridItem != null
            val placement = cell.placement
            val start = placement.halfColumn * (cellWidth + gap) / 2
            val end = start + placement.span * cellWidth + (placement.span - 1) * gap
            cell.start = start.roundToInt()
            cell.cellWidth = end.roundToInt() - cell.start
        }

        val ratioRowHeight = config.cellAspectRatio?.let { (cellWidth / it).roundToInt() }
        val rows = shown.groupBy { it.placement.row }.toSortedMap().values.map { it to ratioRowHeight }
        // Header and footer rows size to their own content, a percentage a share of it.
        val bands = listOfNotNull(header?.takeIf { it.holdsItem }?.let { listOf(it) to null }) +
            rows + listOfNotNull(footer?.takeIf { it.holdsItem }?.let { listOf(it) to null })
        val heights = bands.map { (row, rowHeight) -> measureRow(row, rowHeight, isEdge = row.first().gridItem == null) }

        // Rows spread only into a height the grid has of its own, and only between two rows or
        // more; the header and footer stay the minimum from the cells.
        val ownHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec) - verticalPadding
        } else {
            null
        }
        val isEdge = bands.map { (row, _) -> row.first().gridItem == null }
        val edgesHeight = heights.indices.filter { isEdge[it] }.sumOf { heights[it] + minRowGap.toDouble() }.toFloat()
        val rowsHeight = heights.indices.filterNot { isEdge[it] }.sumOf { heights[it] }
        val rowGap = ownHeight?.let { spreadSpace(config.rowSpacing, (it - edgesHeight - rowsHeight) / density, rows.size) }
            ?.let { it * density } ?: minRowGap

        var top = 0f
        bands.forEachIndexed { index, (row, _) ->
            if (index > 0) top += if (isEdge[index] || isEdge[index - 1]) minRowGap else rowGap
            row.forEach { it.rowTop = top.roundToInt() }
            top += heights[index]
        }
        contentHeight = top.roundToInt()

        val columnsWidth = (columns * cellWidth + (columns - 1) * gap).roundToInt()
        blockWidth = if (innerWidth != null && !capped) innerWidth else columnsWidth
        val contentWidth = innerWidth ?: columnsWidth

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
    private fun measureRow(row: List<GridCell>, ratioRowHeight: Int?, isEdge: Boolean): Int {
        if (ratioRowHeight != null) {
            row.forEach {
                it.measureItem(ratioRowHeight)
                it.measure(exactly(it.cellWidth), exactly(ratioRowHeight))
            }
            return ratioRowHeight
        }

        val hasBasis = isEdge || row.any { it.establishesRowHeight() }
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
        // From layout, not measure: a parent can measure more than once before settling on a width.
        model.gridState.update { it.copy(width = measuredGridWidth) }
        val spareHeight = (height - paddingTop - paddingBottom - contentHeight).coerceAtLeast(0)
        val offset = when (contentPosition.vertical) {
            VerticalPosition.TOP -> 0
            VerticalPosition.CENTER -> spareHeight / 2
            VerticalPosition.BOTTOM -> spareHeight
        }
        // From the start edge, like each cell's own offset, so RTL mirrors both.
        val spareWidth = (width - paddingLeft - paddingRight - blockWidth).coerceAtLeast(0)
        val startOffset = when (contentPosition.horizontal) {
            HorizontalPosition.START -> 0
            HorizontalPosition.CENTER -> spareWidth / 2
            HorizontalPosition.END -> spareWidth
        }
        val rtl = isLayoutRtl

        allCells.forEach { cell ->
            if (!cell.holdsItem) {
                cell.layout(0, 0, 0, 0)
                return@forEach
            }
            val start = startOffset + cell.start
            val left = if (rtl) width - paddingRight - start - cell.measuredWidth else paddingLeft + start
            val top = paddingTop + offset + cell.rowTop
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
                    val index = cell.readingIndex
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
        val info: GridCellInfo,
        val item: View
    ) : ViewGroup(context), AutoSizeProvider {

        /** The grid item this cell holds, or null for a header or footer, which spans every column. */
        val gridItem: GridLayoutItemInfo? = info as? GridLayoutItemInfo

        lateinit var placement: GridPlacement
        /** Where the item is read among the grid's shown items: its placement order. */
        var readingIndex = 0
        /** Offset from the block's start edge. */
        var start = 0
        /** Offset from the top of the rows. */
        var rowTop = 0
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
