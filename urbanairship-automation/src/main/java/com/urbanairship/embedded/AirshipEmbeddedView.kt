package com.urbanairship.embedded

import android.animation.AnimatorInflater.loadAnimator
import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.RelativeLayout
import android.widget.RelativeLayout.LayoutParams
import androidx.annotation.AnimatorRes
import androidx.annotation.LayoutRes
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.EmbeddedDisplayRequest
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.Size.DimensionType.AUTO
import com.urbanairship.android.layout.property.Size.DimensionType.PERCENT
import com.urbanairship.android.layout.ui.EmbeddedLayout
import com.urbanairship.automation.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlin.math.round

/**
 * A container that displays embedded content for the given `embeddedId`.
 */
public class AirshipEmbeddedView private constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int,
    embeddedId: String?,
    @LayoutRes placeholderRes: Int?,
    comparator: Comparator<AirshipEmbeddedInfo>?,
    private val manager: AirshipEmbeddedViewManager
) : RelativeLayout(context, attrs, defStyle) {

    /**
     * Constructor for inflating from XML.
     *
     * @param context The context.
     * @param attrs The attribute set.
     * @param defStyle The default style.
     */
    @JvmOverloads
    public constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
    ) : this(
        context = context,
        attrs = attrs,
        defStyle = defStyle,
        embeddedId = null,
        placeholderRes = null,
        comparator = null,
        manager = EmbeddedViewManager
    )

    /**
     * Constructs an embedded view that will display content for the given embedded ID.
     *
     * @param context a [Context].
     * @param embeddedId the embedded ID.
     * @param comparator optional `Comparator` used to sort available embedded contents.
     * @param placeholderRes optional placeholder layout resource to display when no content is
     *      available.
     */
    @JvmOverloads
    public constructor(
        context: Context,
        embeddedId: String,
        comparator: Comparator<AirshipEmbeddedInfo>? = null,
        @LayoutRes placeholderRes: Int? = null,
    ) : this(
        context = context,
        attrs = null,
        defStyle = 0,
        embeddedId = embeddedId,
        placeholderRes = placeholderRes,
        comparator = comparator,
        manager = EmbeddedViewManager
    )

    /**
     * Supply an alternate width for calculating percentage dimensions.
     *
     * When placing an embedded view inside of a scrolling container, this can be used to provide
     * the width of the scrolling dimension (e.g. the width of the ScrollView's frame).
     */
    public var parentWidthProvider: (() -> Int)? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /**
     * Supply an alternate height for calculating percentage dimensions.
     *
     * When placing an embedded view inside of a scrolling container, this can be used to provide
     * the height of the scrolling dimension (e.g. the height of the ScrollView's frame).
     */
    public var parentHeightProvider: (() -> Int)? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /**
     * The [Comparator] used to sort available embedded contents.
     *
     * If `null` (default), the order (FIFO) of available content will be used.
     */
    public var comparator: Comparator<AirshipEmbeddedInfo>? = comparator
        set(value) {
            field = value
            if (isAttachedToWindow) {
                collectDisplayRequests()
            }
        }

    private val viewJob = SupervisorJob()
    private val viewScope = CoroutineScope(Dispatchers.Main + viewJob)

    private val id: String
    private val placeholderLayoutRes: Int?

    /**
     * The job that collects display requests from the embedded manager.
     *
     * If a previous job exists, it will be cancelled before the new job value is set.
     */
    private var displayRequestsJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    init {
        @Suppress("NamedArgsPositionMismatch") // False positive. attrs is the correct type.
        val a = context.obtainStyledAttributes(attrs, R.styleable.AirshipEmbeddedView, defStyle, 0)

        id = embeddedId ?: requireNotNull(a.getString(R.styleable.AirshipEmbeddedView_airshipEmbeddedId)) {
            "AirshipEmbeddedView requires a layout_id!"
        }

        val placeholder = a.getResourceId(R.styleable.AirshipEmbeddedView_airshipPlaceholder, placeholderRes ?: 0)
        placeholderLayoutRes = if (placeholder == 0) null else placeholder

        setAnimations(
            inAnimation = a.getResourceId(R.styleable.AirshipEmbeddedView_airshipInAnimation, 0),
            outAnimation = a.getResourceId(R.styleable.AirshipEmbeddedView_airshipOutAnimation, 0)
        )

        a.recycle()
    }

    private val logTag: String by lazy { "(embeddedId: \'${id}\')" }

    /**
     * Sets the layout transition animations that will be used to animate embedded content changes.
     *
     * When included inside of a `RecyclerView`, animations should not be set directly on the
     * embedded view, as they may conflict with the `RecyclerView` animations. Prefer item
     * animations on the `RecyclerView` instead.
     *
     * @param inAnimation The animation resource ID for the in animation.
     * @param outAnimation The animation resource ID for the out animation.
     */
    public fun setAnimations(@AnimatorRes inAnimation: Int?, @AnimatorRes outAnimation: Int?) {
        val (animIn, animOut) = try {
            Pair(
                inAnimation?.let { if (it == 0) null else loadAnimator(context, it) },
                outAnimation?.let { if (it == 0) null else loadAnimator(context, it) }
            )
        } catch (e: Exception) {
            UALog.e(e) { "Failed to load embedded view animations!" }
            return
        }

        val layoutTransition: LayoutTransition? =
            if (animIn == null && animOut == null) {
                null
            } else {
                LayoutTransition().apply {
                    setAnimator(LayoutTransition.APPEARING, animIn)
                    setAnimator(LayoutTransition.DISAPPEARING, animOut)
                }
            }

        setLayoutTransition(layoutTransition)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        UALog.v { "onAttachedToWindow $logTag" }

        collectDisplayRequests()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        UALog.v { "onDetachedFromWindow $logTag" }

        viewJob.cancelChildren()
    }

    private fun collectDisplayRequests() {
        displayRequestsJob = viewScope.launch {
            try {
                manager.displayRequests(embeddedViewId = id, comparator = comparator)
                    .collect(::onUpdate)
            } catch (e: CancellationException) {
                UALog.v { "Stopped collecting display requests for $logTag" }
            } catch (e: Exception) {
                UALog.e(e) { "Failed to collect display requests for $logTag" }
            }
        }
    }

    private fun onUpdate(request: EmbeddedDisplayRequest?) {
        val displayArgs = request?.displayArgsProvider?.invoke()
        val layout = displayArgs?.let { args -> EmbeddedLayout(context, id, args, manager) }

        UALog.v {
            val action = if (layout != null) "available" else "empty"
            "onUpdate: $action $logTag"
        }

        removeAllViews()

        // If we have a layout and the listener is null or the listener returns true,
        // then we'll display the content. Otherwise, display the placeholder if we have one.
        if (layout != null) {
            val (width, height) = layout.getPlacement()?.size
                ?.toEmbeddedSize(
                    parentWidthProvider = parentWidthProvider,
                    parentHeightProvider = parentHeightProvider
                ) ?: return

            val view = layout.getOrCreateView(width.fill, height.fill)?.apply {
                layoutParams = LayoutParams(width.spec, height.spec).apply {
                    gravity = Gravity.CENTER
                }
            } ?: return

            addView(view)
            UALog.v { "onUpdate: displayed content $logTag" }
        } else {
            placeholderLayoutRes?.let { resId ->
                val placeholder = LayoutInflater.from(context).inflate(resId, this, false)
                addView(placeholder)
                UALog.v { "onUpdate: displayed placeholder $logTag" }
            }
        }
    }
}

//
// Helpers
//

private fun ConstrainedSize.toEmbeddedSize(
    parentWidthProvider: (() -> Int)?,
    parentHeightProvider: (() -> Int)?
): EmbeddedSize =
    EmbeddedSize(
        width = width.toEmbeddedDimension(parentWidthProvider),
        height = height.toEmbeddedDimension(parentHeightProvider)
    )

private fun Size.Dimension.toEmbeddedDimension(
    parentDimensionProvider: (() -> Int)?
): EmbeddedDimension = when (this.type) {
    AUTO -> EmbeddedDimension(LayoutParams.WRAP_CONTENT, false)
    PERCENT -> parentDimensionProvider?.invoke()?.let { parentDimension ->
        EmbeddedDimension(round(this.float * parentDimension).toInt(), true)
    }
    else -> null
} ?: EmbeddedDimension(LayoutParams.MATCH_PARENT,  false)

private data class EmbeddedSize(
    val width: EmbeddedDimension,
    val height: EmbeddedDimension
)

private data class EmbeddedDimension(
    val spec: Int,
    val fill: Boolean
)
