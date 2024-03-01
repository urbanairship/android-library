package com.urbanairship.android.layout

import android.animation.AnimatorInflater
import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.annotation.AnimatorRes
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.ui.EmbeddedLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class AirshipEmbeddedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    embeddedViewId: String? = null,
    placeholder: View? = null,
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
        } else {
            placeholder
        }

        val animationIn = a.getResourceId(R.styleable.AirshipEmbeddedView_inAnimation, inAnimation)
        val animationOut = a.getResourceId(R.styleable.AirshipEmbeddedView_outAnimation, outAnimation)

        a.recycle()

        val layoutTransition: LayoutTransition = LayoutTransition().apply {
            setAnimator(LayoutTransition.APPEARING, AnimatorInflater.loadAnimator(context, animationIn))
            setAnimator(LayoutTransition.DISAPPEARING, AnimatorInflater.loadAnimator(context, animationOut))
        }

        setLayoutTransition(layoutTransition)
    }

    public var listener: Listener? = object : Listener {
        override fun onAvailable(): Boolean = true
        override fun onEmpty() {}
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        viewScope.launch {
            manager.displayRequests(embeddedViewId = id)
                .map { request ->
                    val displayArgs = request?.displayArgsProvider?.invoke() ?: return@map null
                    EmbeddedLayout(context, id, displayArgs)
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

            val widthSpec = when (size.width.type) {
                Size.DimensionType.AUTO -> LayoutParams.WRAP_CONTENT
                else -> LayoutParams.MATCH_PARENT
            }
            val heightSpec = when (size.height.type) {
                Size.DimensionType.AUTO -> LayoutParams.WRAP_CONTENT
                else -> LayoutParams.MATCH_PARENT
            }

            val view = layout.makeView()?.apply {
                layoutParams = LayoutParams(widthSpec, heightSpec).apply {
                    gravity = Gravity.CENTER
                }
            } ?: return

            addView(view)
        } else {
            listener?.onEmpty()

            placeholderView?.let { placeholder ->
                addView(placeholder)
            }
        }
    }
}
