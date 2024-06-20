/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ContactChannelTest {

    @Test
    public fun testRegisteredSmsSerialization() {
        val channel = ContactChannel.Sms(
            ContactChannel.Sms.RegistrationInfo.Registered(
                channelId = UUID.randomUUID().toString(),
                maskedAddress = UUID.randomUUID().toString(),
                senderId = UUID.randomUUID().toString(),
                isOptIn = true
            )
        )

        val fromJson = ContactChannel.fromJson(channel.toJsonValue())
        assertEquals(channel, fromJson)
    }

    @Test
    public fun testPendingSmsSerialization() {
        val channel = ContactChannel.Sms(
            ContactChannel.Sms.RegistrationInfo.Pending(
                address = UUID.randomUUID().toString(),
                registrationOptions = SmsRegistrationOptions.options(UUID.randomUUID().toString())
            )
        )

        val fromJson = ContactChannel.fromJson(channel.toJsonValue())
        assertEquals(channel, fromJson)
    }

    @Test
    public fun testRegisteredEmailSerialization() {
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Registered(
                channelId = UUID.randomUUID().toString(),
                maskedAddress = UUID.randomUUID().toString(),
                transactionalOptedIn = 1000,
                transactionalOptedOut = 2000,
                commercialOptedIn = 3000,
                commercialOptedOut = 4000
            )
        )

        val fromJson = ContactChannel.fromJson(channel.toJsonValue())
        assertEquals(channel, fromJson)
    }

    @Test
    public fun testRegisteredEmailSerializationEmptyDates() {
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Registered(
                channelId = UUID.randomUUID().toString(),
                maskedAddress = UUID.randomUUID().toString()
            )
        )

        val fromJson = ContactChannel.fromJson(channel.toJsonValue())
        assertEquals(channel, fromJson)
    }

    @Test
    public fun testPendingEmailSerialization() {
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Pending(
                address = UUID.randomUUID().toString(),
                registrationOptions = EmailRegistrationOptions.options(doubleOptIn = true)
            )
        )

        val fromJson = ContactChannel.fromJson(channel.toJsonValue())
        assertEquals(channel, fromJson)
    }
}
