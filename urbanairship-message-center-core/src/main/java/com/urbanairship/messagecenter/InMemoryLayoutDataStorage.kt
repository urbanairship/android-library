package com.urbanairship.messagecenter

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.LayoutDataStorage
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InMemoryLayoutDataStorage: LayoutDataStorage {
    private val _state = MutableStateFlow<JsonValue?>(null)

    override fun getSavedState(): JsonValue? = _state.value
    override fun saveState(state: JsonValue?) {
        _state.update { state }
    }
}
