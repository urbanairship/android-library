/* Copyright Airship and Contributors */

package com.urbanairship.contacts

internal sealed class ContactChannelMutation {
    data class Associate(val channel: ContactChannel, val channelId: String? = null): ContactChannelMutation()
    data class Disassociated(val channel: ContactChannel): ContactChannelMutation()
}
