package com.urbanairship.debug.ui.push

import androidx.annotation.RestrictTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class PushViewModel(repository: PushRepository) : ViewModel() {
    var pushes: MutableStateFlow<List<PushEntity>> = MutableStateFlow(emptyList())

    init {
        CoroutineScope(Dispatchers.IO).launch {
            pushes = repository.getPushes()
        }
    }
}

internal class PushViewModelFactory(private val pushRepository: PushRepository) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PushViewModel(pushRepository) as T
}
