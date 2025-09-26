package com.urbanairship.preferencecenter.compose.ui.theme

public data class PreferenceCenterOptions(
    val showTitleItem: Boolean,
    val showSwitchIcons: Boolean
) {
    public companion object {
        public fun defaults(): PreferenceCenterOptions = PreferenceCenterOptions(
            showTitleItem = false,
            showSwitchIcons = true
        )
    }
}
