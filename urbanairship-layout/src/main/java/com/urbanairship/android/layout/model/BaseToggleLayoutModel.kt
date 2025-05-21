package com.urbanairship.android.layout.model

import android.view.View
import androidx.annotation.CallSuper
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.info.BaseToggleLayoutInfo
import com.urbanairship.android.layout.property.EventHandler.Type
import com.urbanairship.android.layout.property.hasTapHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal abstract class BaseToggleLayoutModel<T : View, I : BaseToggleLayoutInfo>(
    viewInfo: I,
    val view: AnyModel,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<T, I, BaseModel.Listener>(viewInfo, environment, properties) {

    private var _isOn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    fun toggle() {
        this._isOn.update { !it }
    }

    fun setChecked(isChecked: Boolean) {
        this._isOn.update { isChecked }
    }

    @CallSuper
    override fun onViewAttached(view: T) {
        super.onViewAttached(view)
        // Handle taps if enabled for this view.
        // We drop the first update to ignore the change caused by restoring toggle state.
        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                isOn.drop(1).collect { handleViewEvent(Type.TAP) }
            }
        }

        viewScope.launch {
            formState.formUpdates.collect { state ->
                listener?.setEnabled(state.isEnabled)
            }
        }

        viewScope.launch {
            isOn.collect { isOn ->
                onChange(isOn)
            }
        }
    }

    private fun onChange(isOn: Boolean) {
        val actions = when(isOn) {
            true -> viewInfo.onToggleOn
            false -> viewInfo.onToggleOff
        }

        runStateActions(actions = actions.stateActions)
    }
}
