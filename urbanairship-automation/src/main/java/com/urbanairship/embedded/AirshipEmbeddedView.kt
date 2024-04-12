package com.urbanairship.embedded

import android.animation.AnimatorInflater
import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.annotation.AnimatorRes
import androidx.annotation.LayoutRes
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.EmbeddedDisplayRequest
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.property.Size.DimensionType.AUTO
import com.urbanairship.android.layout.property.Size.DimensionType.PERCENT
import com.urbanairship.android.layout.ui.EmbeddedLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Airship Embedded View.
 *
 * Displays embedded content for the provided `embeddedViewId`.
 *
 * @param context The context.
 * @param attrs The attribute set.
 * @param defStyle The default style.
 * @param embeddedViewId The embedded view ID.
 * @param placeholderLayout A placeholder layout resource.
 * @param inAnimation An animation resource used for animate in transitions.
 * @param outAnimation An animation resource used for animate out transitions.
 */
public class AirshipEmbeddedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    embeddedViewId: String? = null,
    @LayoutRes placeholderLayout: Int? = null,
    @AnimatorRes inAnimation: Int = android.R.animator.fade_in,
    @AnimatorRes outAnimation: Int = android.R.animator.fade_out,
    private val manager: AirshipEmbeddedViewManager = EmbeddedViewManager,
) : RelativeLayout(context, attrs, defStyle) {

    /** Listener for an Embedded View. */
    public interface Listener {
        /**
         * Called when embedded content is available to display.
         *
         * @param info The embedded content info.
         *
         * @return `true` or `false`, indicating whether if the embedded content should be displayed.
         */
        public fun onAvailable(info: AirshipEmbeddedInfo): Boolean

        /**
         * Called when no embedded content is currently available.
         */
        public fun onEmpty()
    }

    /**
     * Embedded View listener that will be notified when embedded content is available to display
     * or when no embedded content is currently available.
     */
    public var listener: Listener? = null

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

    private val viewJob = SupervisorJob()
    private val viewScope = CoroutineScope(Dispatchers.Main + viewJob)

    private val id: String
    private val placeholderView: View?

    private val logTag: String
        get() = (tag?.let { ", tag: \'$it\'" } ?: "").let { "(embeddedViewId: \'${id}\'${it})" }

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        UALog.v { "onAttachedToWindow $logTag" }

        viewScope.launch {
            manager.displayRequests(embeddedViewId = id)
                .collect(::onUpdate)
        }
    }

    override fun onDetachedFromWindow() {
        UALog.v("onDetachedFromWindow $logTag")
        super.onDetachedFromWindow()
        viewJob.cancelChildren()
    }

    private fun onUpdate(request: EmbeddedDisplayRequest?) {
        val displayArgs = request?.displayArgsProvider?.invoke()
        val info = request?.let { AirshipEmbeddedInfo(it) }
        val layout = displayArgs?.let { args -> EmbeddedLayout(context, id, args, manager) }

        UALog.v {
            val action = if (layout != null) "available" else "empty"
            "onUpdate: $action $logTag"
        }

        removeAllViews()

        // If we have a layout and the listener is null or the listener returns true,
        // then we'll display the content. Otherwise, display the placeholder if we have one.
        if (layout != null &&
            (listener == null || (info != null && listener?.onAvailable(info) == true))) {

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
                        (size.height.float * parentHeight).roundToInt() to true
                    } ?: (LayoutParams.MATCH_PARENT to false)
                }
                else -> LayoutParams.MATCH_PARENT to false
            }

            val view = layout.makeView(fillWidth, fillHeight)?.apply {
                layoutParams = LayoutParams(widthSpec, heightSpec).apply {
                    gravity = Gravity.CENTER
                }
            } ?: return

            addView(view)
            UALog.v { "onUpdate: displayed content $logTag" }
        } else {
            listener?.onEmpty()

            placeholderView?.let { placeholder ->
                addView(placeholder)
                UALog.v { "onUpdate: displayed placeholder $logTag" }
            }
        }
    }
}
