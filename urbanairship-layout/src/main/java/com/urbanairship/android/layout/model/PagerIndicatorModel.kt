/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.AutomatedAccessibilityActionType
import com.urbanairship.android.layout.info.PagerIndicatorInfo
import com.urbanairship.android.layout.view.PagerIndicatorView
import kotlinx.coroutines.launch

internal class PagerIndicatorModel(
    viewInfo: PagerIndicatorInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<PagerIndicatorView, PagerIndicatorInfo, PagerIndicatorModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    interface Listener : BaseModel.Listener {

        fun onUpdate(size: Int, position: Int)
    }

    override var listener: Listener? = null
        set(value) {
            field = value
            layoutState.pager?.changes?.value?.let { state ->
                listener?.onUpdate(state.pageIds.size, state.pageIndex)
            }
        }

    private val indicatorViewIds = HashMap<Int, Int>()

    val announcePage: Boolean
        get() = viewInfo.automatedAccessibilityActions?.any {
            it.type == AutomatedAccessibilityActionType.ANNOUNCE
        } ?: false

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = PagerIndicatorView(context, this).apply {
        id = viewId
    }

    override fun onViewAttached(view: PagerIndicatorView) {
        modelScope.launch {
            layoutState.pager?.changes?.collect { state ->
                listener?.onUpdate(state.pageIds.size, state.pageIndex)
            }
        }
    }

    /** Returns a stable viewId for the indicator view at the given `position`.  */
    fun getIndicatorViewId(position: Int): Int =
        indicatorViewIds.getOrPut(position) { View.generateViewId() }
}
