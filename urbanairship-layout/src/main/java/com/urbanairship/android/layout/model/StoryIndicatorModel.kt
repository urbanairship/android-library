package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.AutomatedAccessibilityActionType
import com.urbanairship.android.layout.info.StoryIndicatorInfo
import com.urbanairship.android.layout.view.StoryIndicatorView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class StoryIndicatorModel(
    viewInfo: StoryIndicatorInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<StoryIndicatorView, StoryIndicatorInfo, StoryIndicatorModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    interface Listener : BaseModel.Listener {

        fun onUpdate(
            size: Int,
            pageIndex: Int,
            progress: Int,
            durations: List<Int?>,
            announcePage: Boolean
        )
    }

    override var listener: Listener? = null
        set(value) {
            field = value

            layoutState.pager?.changes?.value?.let { state ->
                listener?.onUpdate(
                    size = state.pageIds.size,
                    pageIndex = state.pageIndex,
                    progress = state.progress,
                    durations = state.durations,
                    announcePage = this.announcePage
                )
            }
        }

    private val indicatorViewIds = HashMap<Int, Int>()

    val announcePage: Boolean
        get() = viewInfo.automatedAccessibilityActions?.any {
            it.type == AutomatedAccessibilityActionType.ANNOUNCE
        } ?: false

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = StoryIndicatorView(context, this).apply {
        id = viewId
    }

    override fun onViewAttached(view: StoryIndicatorView) {
        viewScope.launch {
            layoutState.pager?.changes
                // Pull out the state we care about so that we can use distinctUntilChanged to
                // avoid unnecessary updates.
                ?.map {
                    StoryIndicatorUpdate(
                        it.pageIds.size,
                        it.pageIndex,
                        it.progress,
                        it.durations,
                        announcePage
                    )
                }?.distinctUntilChanged()
                ?.collect { (size, pageIndex, progress, durations, announcePage) ->
                    listener?.onUpdate(size, pageIndex, progress, durations, announcePage)
                }
        }
    }

    data class StoryIndicatorUpdate(
        val size: Int,
        val pageIndex: Int,
        val progress: Int,
        val durations: List<Int?>,
        val announcePage: Boolean
    )

    /** Returns a stable viewId for the indicator view at the given `position`.  */
    fun getIndicatorViewId(position: Int): Int =
        indicatorViewIds.getOrPut(position) { View.generateViewId() }
}
