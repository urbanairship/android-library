/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import androidx.annotation.Dimension
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.AccessibilityActionType
import com.urbanairship.android.layout.info.AutomatedAccessibilityAction
import com.urbanairship.android.layout.info.AutomatedAccessibilityActionType
import com.urbanairship.android.layout.info.PagerIndicatorInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.PagerIndicatorView
import kotlinx.coroutines.launch

internal class PagerIndicatorModel(
    val bindings: PagerIndicatorInfo.Bindings,
    @get:Dimension(unit = Dimension.DP)
    val indicatorSpacing: Int,
    val automatedAccessibilityActions: List<AutomatedAccessibilityAction>? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<PagerIndicatorView, PagerIndicatorModel.Listener>(
    viewType = ViewType.PAGER_INDICATOR,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(info: PagerIndicatorInfo, env: ModelEnvironment, props: ModelProperties) : this(
        bindings = info.bindings,
        indicatorSpacing = info.indicatorSpacing,
        automatedAccessibilityActions = info.automatedAccessibilityActions,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env,
        properties = props
    )

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
        get() = automatedAccessibilityActions?.any { it.type == AutomatedAccessibilityActionType.ANNOUNCE } ?: false

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        PagerIndicatorView(context, this).apply {
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
