/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.UAirship
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions
import com.urbanairship.util.Clock
import java.util.Date

internal class ThomasChannelRegistrar(
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val registerEmail: (String, com.urbanairship.contacts.EmailRegistrationOptions) -> Unit = { address, options ->
        UAirship.shared().contact.registerEmail(address, options)
    }
) {
    fun register(channels: List<ThomasChannelRegistration>) {
        channels.forEach { channelRegistration ->
            when(channelRegistration) {
                is ThomasChannelRegistration.Email -> {
                    registerEmail(channelRegistration)
                }

                is ThomasChannelRegistration.Sms -> {
                    //TODO: add implementation
                }
            }
        }
    }

    private fun registerEmail(channelRegistration: ThomasChannelRegistration.Email) {
        val now = Date(clock.currentTimeMillis())

        val options = when(channelRegistration.options) {
            is ThomasEmailRegistrationOptions.Commercial -> {
                com.urbanairship.contacts.EmailRegistrationOptions.commercialOptions(
                    commercialOptedIn = if (channelRegistration.options.optedIn) { now } else { null },
                    properties = channelRegistration.options.properties
                )
            }
            is ThomasEmailRegistrationOptions.DoubleOptIn -> {
                com.urbanairship.contacts.EmailRegistrationOptions.options(
                    properties = channelRegistration.options.properties,
                    doubleOptIn = true
                )
            }
            is ThomasEmailRegistrationOptions.Transactional -> {
                com.urbanairship.contacts.EmailRegistrationOptions.options(
                    properties = channelRegistration.options.properties,
                    doubleOptIn = false
                )
            }
        }

        registerEmail(channelRegistration.address, options)
    }
}
