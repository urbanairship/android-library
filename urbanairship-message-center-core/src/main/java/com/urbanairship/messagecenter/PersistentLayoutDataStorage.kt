package com.urbanairship.messagecenter

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.android.layout.LayoutDataStorage
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PersistentLayoutDataStorage(
    private val onSave: suspend (Message.AssociatedData.ViewState?) -> Unit,
    private val onFetch: suspend () -> Message.AssociatedData.ViewState?,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
): LayoutDataStorage {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val _restorationId = MutableStateFlow<String?>(null)
    private val _cache = MutableStateFlow<JsonValue?>(null)

    override suspend fun prepare(restorationId: String) {
        scope.launch {
            _restorationId.update { restorationId }

            val state = onFetch()
            if (state?.restorationId != restorationId){
                clear()
                return@launch
            }

            _cache.update { state.state }
        }.join()
    }

    override fun getSavedState(): JsonValue? = _cache.value

    override fun saveState(state: JsonValue?) {
        _cache.update { state }
        flush()
    }

    private fun clear() {
        _cache.update { null }
        flush()
    }

    private fun flush() {
        scope.launch {
            val restorationId = _restorationId.value
            val state = _cache.value

            if(restorationId == null || state == null) {
                onSave(null)
            } else {
                onSave(Message.AssociatedData.ViewState(
                    restorationId = restorationId,
                    state = state
                ))
            }
        }
    }
}
