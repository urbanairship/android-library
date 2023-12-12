package com.urbanairship.android.layout

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.urbanairship.android.layout.ui.EmbeddedLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class AirshipEmbeddedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    private val manager: AirshipEmbeddedViewManager = DefaultEmbeddedViewManager
) : FrameLayout(context, attrs, defStyle) {

    public interface Listener {
        public fun onAvailable(): Boolean
        public fun onEmpty()
    }

    private val viewJob = SupervisorJob()
    private val viewScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    private val embeddedViewId: String

    private var displayedLayout: EmbeddedLayout? = null

    public var listener: Listener? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AirshipEmbeddedView, defStyle, 0)

        embeddedViewId = requireNotNull(a.getString(R.styleable.AirshipEmbeddedView_layout_id)) {
            "AirshipEmbeddedView requires a layout_id!"
        }

        a.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        viewScope.launch {
            manager.displayRequests(embeddedViewId = embeddedViewId)
                .map { request ->
                    val displayArgs = request?.displayArgsProvider?.invoke() ?: return@map null
                    EmbeddedLayout(context, embeddedViewId, displayArgs)
                }
                .collect { layout -> update(layout) }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewJob.cancelChildren()
    }

    public fun dismiss() {
        displayedLayout?.dismiss()
        viewScope.cancel()
    }

    private fun update(layout: EmbeddedLayout?) {
        displayedLayout?.dismiss(isInternal = true)

        if (layout != null && (listener == null || listener?.onAvailable() == true)) {
            layout.displayIn(this)
        } else {
            listener?.onEmpty()
        }

        displayedLayout = layout
    }
}
