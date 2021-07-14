package com.urbanairship.preferencecenter.ui

import androidx.lifecycle.ViewModelProvider

internal class TestPreferenceCenterFragment(
    mockViewModelFactory: ViewModelProvider.Factory
) : PreferenceCenterFragment() {
    override val viewModelFactory: ViewModelProvider.Factory = mockViewModelFactory
}
