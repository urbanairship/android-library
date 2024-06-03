package com.urbanairship.android.layout.ui

import android.view.View
import androidx.lifecycle.ViewModel
import com.urbanairship.UALog
import com.urbanairship.android.layout.ModelFactory
import com.urbanairship.android.layout.ModelFactoryException
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.ThomasModelFactory
import com.urbanairship.android.layout.environment.ExternalActionsRunner
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.reporting.DisplayTimer
import kotlinx.coroutines.cancel

internal class LayoutViewModel : ViewModel() {

    private var model: AnyModel? = null
    private var environment: ModelEnvironment? = null

    val rootViewId = View.generateViewId()

    @JvmOverloads
    fun getOrCreateEnvironment(
        reporter: Reporter,
        listener: ThomasListenerInterface,
        displayTimer: DisplayTimer,
        layoutState: LayoutState = LayoutState.EMPTY
    ): ModelEnvironment =
        environment ?: ModelEnvironment(
            layoutState = layoutState,
            reporter = reporter,
            actionsRunner = ExternalActionsRunner(listener),
            displayTimer = displayTimer,
        ).also {
            environment = it
        }

    @JvmOverloads
    @Throws(ModelFactoryException::class)
    fun getOrCreateModel(
        viewInfo: ViewInfo,
        modelEnvironment: ModelEnvironment,
        factory: ModelFactory = ThomasModelFactory()
    ): AnyModel =
        model ?: factory.create(
            info = viewInfo,
            environment = modelEnvironment
        ).also {
            model = it
        }

    override fun onCleared() {
        UALog.v("Lifecycle: CLEARED")
        environment?.modelScope?.cancel()
    }
}
