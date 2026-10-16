/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.Airship
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions
import com.urbanairship.android.layout.property.SmsLocale
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.contacts.SmsRegistrationOptions
import com.urbanairship.util.Clock
import java.time.Instant

internal class ThomasChannelRegistrar(
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val registerEmail: (String, EmailRegistrationOptions) -> Unit = { address, options ->
        Airship.contact.registerEmail(address, options)
    },
    private val registerSms: (String, SmsRegistrationOptions) -> Unit = { msisdn, options ->
        Airship.contact.registerSms(msisdn, options)
    }
) {
    fun register(channels: List<ThomasChannelRegistration>) {
        channels.forEach { channelRegistration ->
            when(channelRegistration) {
                is ThomasChannelRegistration.Email -> {
                    registerEmail(channelRegistration)
                }

                is ThomasChannelRegistration.Sms -> {
                    registerSms(channelRegistration)
                }
            }
        }
    }

    private fun registerSms(channelRegistration: ThomasChannelRegistration.Sms) {
        val options = when(channelRegistration.registration) {
            is SmsLocale.Registration.OptIn -> {
                SmsRegistrationOptions.options(senderId = channelRegistration.registration.data.senderId)
            }
        }

        registerSms(channelRegistration.address, options)
    }

    private fun registerEmail(channelRegistration: ThomasChannelRegistration.Email) {
        val now = clock.now()
        val openTracking = channelRegistration.options.openTrackingOptedIn.trackingConsent(now)
        val clickTracking = channelRegistration.options.clickTrackingOptedIn.trackingConsent(now)

        val options = when(channelRegistration.options) {
            is ThomasEmailRegistrationOptions.Commercial -> {
                EmailRegistrationOptions.commercialOptions(
                    commercialOptedIn = if (channelRegistration.options.optedIn) { now } else { null },
                    properties = channelRegistration.options.properties,
                    openTracking = openTracking,
                    clickTracking = clickTracking
                )
            }
            is ThomasEmailRegistrationOptions.DoubleOptIn -> {
                EmailRegistrationOptions.options(
                    properties = channelRegistration.options.properties,
                    doubleOptIn = true,
                    openTracking = openTracking,
                    clickTracking = clickTracking
                )
            }
            is ThomasEmailRegistrationOptions.Transactional -> {
                EmailRegistrationOptions.options(
                    properties = channelRegistration.options.properties,
                    doubleOptIn = false,
                    openTracking = openTracking,
                    clickTracking = clickTracking
                )
            }
        }

        registerEmail(channelRegistration.address, options)
    }
}

private fun Boolean?.trackingConsent(now: Instant): EmailRegistrationOptions.TrackingConsent? = when (this) {
    true -> EmailRegistrationOptions.TrackingConsent.OptedIn(now)
    false -> EmailRegistrationOptions.TrackingConsent.OptedOut(now)
    null -> null
}
