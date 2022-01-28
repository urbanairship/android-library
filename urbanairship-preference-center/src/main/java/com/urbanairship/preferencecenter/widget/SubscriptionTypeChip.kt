/* Copyright Airship and Contributors */

package com.urbanairship.preferencecenter.widget

import android.content.Context
import com.google.android.material.chip.Chip
import com.urbanairship.preferencecenter.R

/**
 * Custom themed Material `Chip` used to toggle contact-level subscription types.
 *
 * Style is applied via `defStyleAttr` reference to:
 * `@style/UrbanAirship.PreferenceCenter.Item.ContactSubscriptionGroup.Chip`
 */
class SubscriptionTypeChip(
    context: Context
) : Chip(context, null, R.attr.urbanAirshipPreferenceCenterSubscriptionTypeChipStyle) {
    init {
        id = generateViewId()
    }
}
