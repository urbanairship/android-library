package com.urbanairship.preferencecenter.ui

import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope

internal class TestPreferenceCenterFragment(
    mockViewModelFactory: ViewModelProvider.Factory,
    mockViewModelScopeProvider: () -> CoroutineScope
) : PreferenceCenterFragment() {
    override val viewModelFactory: ViewModelProvider.Factory = mockViewModelFactory
    override val viewModelScopeProvider = mockViewModelScopeProvider
}
