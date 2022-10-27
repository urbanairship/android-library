package com.urbanairship.android.layout.ui

import androidx.lifecycle.ViewModel
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.model.BaseModel

internal class LayoutViewModel : ViewModel() {
    var model: BaseModel? = null
    var environment: ModelEnvironment? = null
}
