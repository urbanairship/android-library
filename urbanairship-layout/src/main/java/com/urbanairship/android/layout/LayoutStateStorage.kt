package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.json.JsonValue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LayoutDataStorage {
    public suspend fun prepare(restorationId: String)
    public fun getSavedState(): JsonValue?
    public fun saveState(state: JsonValue?)
}

@OptIn(FlowPreview::class)
internal class LayoutStateStorage(
    private val storage: LayoutDataStorage,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {
    private val _states = MutableStateFlow<Map<String, SharedState<State>>>(emptyMap())
    private val valueChangedFlow = MutableSharedFlow<Unit>()

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    init {
        val saved = storage.getSavedState()
            ?.optMap()
            ?.map
            ?.mapNotNull {
                try {
                    it.key to SharedState(State.fromJson(it.value))
                } catch (ex: Exception) {
                    UALog.w(ex) { "Failed to restore state"}
                    null
                }
            }
            ?.toMap()
            ?: emptyMap()

        _states.update { saved }
        saved.values.forEach(::saveUpdatesFor)

        scope.launch {
            valueChangedFlow
                .debounce(300.milliseconds)
                .collect { saveToStorage() }
        }
    }

    internal fun getState(identifier: String, orPut: () -> SharedState<State>): SharedState<State> {
        return _states.value[identifier]
            ?: orPut().also { saveState(identifier, it) }
    }
    internal fun saveState(identifier: String, state: SharedState<State>) {
        _states.update { it + (identifier to state) }
        saveUpdatesFor(state)
    }

    internal fun saveUpdatesFor(stream: SharedState<State>) {
        scope.launch {
            stream.changes.collect { valueChangedFlow.emit(Unit) }
        }
    }

    internal fun saveToStorage() {
        val toStore = _states.value
            .map { it.key to it.value.changes.value }
            .toMap()

        storage.saveState(JsonValue.wrap(toStore))
    }

    public fun clear() {
        _states.update { emptyMap() }
        scope.cancel()
        saveToStorage()
    }
}
