package com.urbanairship.embedded

import android.animation.AnimatorInflater
import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.AnimatorRes
import androidx.annotation.LayoutRes
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.Size.DimensionType.AUTO
import com.urbanairship.android.layout.property.Size.DimensionType.PERCENT
import com.urbanairship.android.layout.ui.EmbeddedLayout
import com.urbanairship.android.layout.util.LayoutUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

public class AirshipEmbeddedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    embeddedViewId: String? = null,
    @LayoutRes placeholderLayout: Int? = null,
    @AnimatorRes inAnimation: Int = android.R.animator.fade_in,
    @AnimatorRes outAnimation: Int = android.R.animator.fade_out,
    private val manager: AirshipEmbeddedViewManager = DefaultEmbeddedViewManager,
) : RelativeLayout(context, attrs, defStyle) {

    public interface Listener {
        public fun onAvailable(): Boolean
        public fun onEmpty()
    }

    private val viewJob = SupervisorJob()
    private val viewScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    private val id: String
    private val placeholderView: View?

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AirshipEmbeddedView, defStyle, 0)

        id = embeddedViewId ?: requireNotNull(a.getString(R.styleable.AirshipEmbeddedView_layout_id)) {
            "AirshipEmbeddedView requires a layout_id!"
        }

        val placeholderRes = a.getResourceId(R.styleable.AirshipEmbeddedView_placeholder, 0)
        placeholderView = if (placeholderRes != 0) {
            LayoutInflater.from(context).inflate(placeholderRes, this, false)
        } else if (placeholderLayout != null) {
            LayoutInflater.from(context).inflate(placeholderLayout, this, false)
        } else {
            null
        }

        val animationIn = a.getResourceId(R.styleable.AirshipEmbeddedView_in_animation, inAnimation)
        val animationOut = a.getResourceId(R.styleable.AirshipEmbeddedView_out_animation, outAnimation)

        a.recycle()

        val layoutTransition: LayoutTransition = LayoutTransition().apply {
            setAnimator(LayoutTransition.APPEARING, AnimatorInflater.loadAnimator(context, animationIn))
            setAnimator(LayoutTransition.DISAPPEARING, AnimatorInflater.loadAnimator(context, animationOut))
        }

        setLayoutTransition(layoutTransition)
    }

    /**
     * Embedded View listener that will be notified when embedded content is available to display
     * or when no embedded content is currently available.
     */
    public var listener: Listener? = object : Listener {
        override fun onAvailable(): Boolean = true
        override fun onEmpty() {}
    }

    /**
     * Supply an alternate width for calculating percentage dimensions. When placing an embedded
     * view inside of a scrolling container, this can be used to provide the width of the scrolling
     * dimension (e.g. the width of the ScrollView's frame).
     */
    public  var parentWidthProvider: (() -> Int)? = null

    /**
     * Supply an alternate height for calculating percentage dimensions. When placing an embedded
     * view inside of a scrolling container, this can be used to provide the height of the scrolling
     * dimension (e.g. the height of the ScrollView's frame).
     */
    public  var parentHeightProvider: (() -> Int)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        viewScope.launch {
            manager.displayRequests(embeddedViewId = id)
                .map { request ->
                    val displayArgs = request?.displayArgsProvider?.invoke() ?: return@map null
                    EmbeddedLayout(context, id, displayArgs, manager)
                }
                .collect(::update)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewJob.cancelChildren()
    }

    private fun update(layout: EmbeddedLayout?) {
        removeAllViews()

        if (layout != null && (listener == null || listener?.onAvailable() == true)) {
            val size = layout.getPlacement()?.size ?: return

            val (widthSpec, fillWidth) = when (size.width.type) {
                AUTO -> LayoutParams.WRAP_CONTENT to false
                PERCENT -> parentWidthProvider?.invoke()?.let { parentWidth ->
                    (size.width.float * parentWidth).roundToInt() to true
                } ?: (LayoutParams.MATCH_PARENT to false)
                else -> LayoutParams.MATCH_PARENT to false
            }
            val (heightSpec, fillHeight) = when (size.height.type) {
                AUTO -> LayoutParams.WRAP_CONTENT to false
                PERCENT -> {
                    parentHeightProvider?.invoke()?.let { parentHeight ->
                        (size.width.float * parentHeight).roundToInt() to true
                    } ?: (LayoutParams.MATCH_PARENT to false)
                }
                else -> LayoutParams.MATCH_PARENT to false
            }

            val frame = FrameLayout(context).apply {
                layoutParams = LayoutParams(widthSpec, heightSpec)
            }

            val view = layout.makeView(fillWidth, fillHeight)?.apply {
                layoutParams = LayoutParams(widthSpec, heightSpec).apply {
                    gravity = Gravity.CENTER
                }
            } ?: return

            frame.addView(view)
            addView(frame)
        } else {
            listener?.onEmpty()

            placeholderView?.let { placeholder ->
                addView(placeholder)
            }
        }
    }
}
