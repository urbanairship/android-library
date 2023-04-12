package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.StoryIndicatorInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.StoryIndicatorSource
import com.urbanairship.android.layout.property.StoryIndicatorStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.StoryIndicatorView
import kotlinx.coroutines.launch

internal class StoryIndicatorModel(
    val style: StoryIndicatorStyle,
    val source: StoryIndicatorSource,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<StoryIndicatorView, StoryIndicatorModel.Listener>(
    viewType = ViewType.STORY_INDICATOR,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(info: StoryIndicatorInfo, env: ModelEnvironment, props: ModelProperties) : this(
        style = info.style,
        source = info.source,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env,
        properties = props
    )

    interface Listener : BaseModel.Listener {
        fun onUpdate(size: Int, progress: Int)
    }

    override var listener: Listener? = null
        set(value) {
            field = value
            layoutState.pager?.changes?.value?.let { state ->
                // TODO(stories): need to update pager/current_page progress here instead of
                //   just passing the page index.
                listener?.onUpdate(state.pages.size, state.pageIndex)
            }
        }

    private val indicatorViewIds = HashMap<Int, Int>()

    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment) =
        StoryIndicatorView(context, this).apply {
            id = viewId
        }

    override fun onViewAttached(view: StoryIndicatorView) {
        modelScope.launch {
            layoutState.pager?.changes?.collect { state ->
                // TODO(stories): need to update pager/current_page progress here instead of
                //   just passing the page index.
                listener?.onUpdate(state.pages.size, state.pageIndex)
            }
        }
    }

    /** Returns a stable viewId for the indicator view at the given `position`.  */
    fun getIndicatorViewId(position: Int): Int =
        indicatorViewIds.getOrPut(position) { View.generateViewId() }
}
