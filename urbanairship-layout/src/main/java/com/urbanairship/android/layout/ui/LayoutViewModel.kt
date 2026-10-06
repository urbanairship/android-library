package com.urbanairship.android.layout.ui

import android.view.View
import androidx.lifecycle.ViewModel
import com.urbanairship.UALog
import com.urbanairship.android.layout.LayoutStateStorage
import com.urbanairship.android.layout.ModelFactory
import com.urbanairship.android.layout.ModelFactoryException
import com.urbanairship.android.layout.ThomasModelFactory
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.ai.ThomasAIInference
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.Conditions
import com.urbanairship.android.layout.property.EnvironmentConditionSelector
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.util.randomShuffleSeed
import kotlinx.coroutines.cancel

internal class LayoutViewModel : ViewModel() {

    private var model: AnyModel? = null
    private var environment: ModelEnvironment? = null

    val rootViewId = View.generateViewId()

    fun getOrCreateEnvironment(
        reporter: Reporter,
        displayTimer: DisplayTimer,
        actionRunner: ThomasActionRunner,
        layoutState: LayoutState = LayoutState.EMPTY,
        stateStorage: LayoutStateStorage? = null,
        aiInference: ThomasAIInference? = null,
        shuffleSeed: String? = null,
        environmentConditions: Conditions<EnvironmentConditionSelector> = Conditions(emptyMap()),
        selectsOnEnvironmentConditions: Boolean = false
    ): ModelEnvironment =
        environment ?: ModelEnvironment(
            layoutState = layoutState,
            reporter = reporter,
            actionsRunner = actionRunner,
            displayTimer = displayTimer,
            stateStorage = stateStorage,
            aiInference = aiInference,
            shuffleSeed = shuffleSeed ?: randomShuffleSeed(),
            environmentConditions = environmentConditions,
            selectsOnEnvironmentConditions = selectsOnEnvironmentConditions
        ).also {
            environment = it
        }

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
