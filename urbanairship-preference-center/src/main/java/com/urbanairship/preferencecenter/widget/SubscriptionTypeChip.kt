/* Copyright Airship and Contributors */

package com.urbanairship.preferencecenter.widget

import android.content.Context
import com.google.android.material.chip.Chip
import com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap
import com.urbanairship.preferencecenter.R

/**
 * Custom themed Material `Chip` used to toggle contact-level subscription types.
 *
 * Style is applied via `defStyleAttr` reference to:
 * `@style/UrbanAirship.PreferenceCenter.Item.ContactSubscriptionGroup.Chip`
 */
internal class SubscriptionTypeChip(
    context: Context,
    defStyleAttr: Int = R.attr.urbanAirshipPreferenceCenterSubscriptionTypeChipStyle
) : Chip(
    // Wrap the context to add support for materialThemeOverlay to override default styles.
    wrap(context, null, defStyleAttr, R.style.UrbanAirship_PreferenceCenter_Item_Widget_SubscriptionTypeChip),
    null,
    defStyleAttr
) {
    init {
        id = generateViewId()
    }
}
