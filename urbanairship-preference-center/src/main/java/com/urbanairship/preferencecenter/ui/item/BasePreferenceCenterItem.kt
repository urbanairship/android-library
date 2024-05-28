package com.urbanairship.preferencecenter.ui.item

import com.urbanairship.preferencecenter.data.Conditions

internal sealed class PrefCenterItem(val type: Int) {

    companion object {
        const val TYPE_DESCRIPTION = 0
        const val TYPE_SECTION = 1
        const val TYPE_SECTION_BREAK = 2
        const val TYPE_PREF_CHANNEL_SUBSCRIPTION = 3
        const val TYPE_PREF_CONTACT_SUBSCRIPTION = 4
        const val TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP = 5
        const val TYPE_ALERT = 6
        const val TYPE_CONTACT_MANAGEMENT = 7
    }

    abstract val id: String
    abstract val conditions: Conditions

    abstract fun areItemsTheSame(otherItem: PrefCenterItem): Boolean
    abstract fun areContentsTheSame(otherItem: PrefCenterItem): Boolean
}
