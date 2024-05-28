@file:Suppress("ConvertObjectToDataObject")

package com.urbanairship.contacts

import com.urbanairship.contacts.ContactChannel.Pending.PendingInfo
import com.urbanairship.contacts.ContactChannel.Registered.RegisteredInfo
import java.util.Objects

// TODO: not implemented from the swift version yet...
//      - RegistrationOptions and associated EmailRegistrationOptions and SMSRegistrationOptions
//      - ContactChannelUpdates

/** Representation of a channel and its registration state after being associated to a contact. */
public sealed class ContactChannel {
    public abstract val maskedAddress: String

    public class Registered(
        public val channelId: String,
        public override val maskedAddress: String,
        public val info: RegisteredInfo
    ): ContactChannel() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Registered) return false
            return channelId == other.channelId && maskedAddress == other.maskedAddress && info == other.info
        }

        override fun hashCode(): Int = Objects.hash(channelId, maskedAddress, info)

        /** Registration info for a channel. */
        public sealed class RegisteredInfo() {
            /** Email registration info. */
            public class Email(
                /** Date the user opted in to transactional emails. */
                public val transactionalOptedIn: Long?,
                /** Date the user opted out of transactional emails. */
                public val transactionalOptedOut: Long?,
                /**
                 * Date the user opted in to commercial emails.
                 *
                 * This field determines the email opted-in state.
                 */
                public val commercialOptedIn: Long?,
                /** Date the user opted out of commercial emails. */
                public val commercialOptedOut: Long?
            ) : RegisteredInfo() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is Email) return false
                    return transactionalOptedIn == other.transactionalOptedIn &&
                            transactionalOptedOut == other.transactionalOptedOut &&
                            commercialOptedIn == other.commercialOptedIn &&
                            commercialOptedOut == other.commercialOptedOut
                }

                override fun hashCode(): Int = Objects.hash(
                    transactionalOptedIn, transactionalOptedOut, commercialOptedIn, commercialOptedOut
                )
            }

            /** SMS registration info. */
            public class SMS(
                public val isOptIn: Boolean,
                public val senderID: String
            ) : RegisteredInfo() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is SMS) return false
                    return isOptIn == other.isOptIn && senderID == other.senderID
                }

                override fun hashCode(): Int = Objects.hash(isOptIn, senderID)
            }
        }
    }

    public class Pending(
        /** The email or phone number that is being registered. */
        public val address: String,
        public val info: PendingInfo
    ): ContactChannel() {

        public override val maskedAddress: String
            get() = when (info) {
                is PendingInfo.Email -> address.maskEmail()
                is PendingInfo.Sms -> address.maskPhoneNumber()
            }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pending) return false
            return address == other.address && maskedAddress == other.maskedAddress && info == other.info
        }

        override fun hashCode(): Int = Objects.hash(address, maskedAddress, info)

        /** Pending registration info for a channel. */
        public sealed class PendingInfo {

            /** Pending email registration info. */
            public object Email : PendingInfo() {
                override fun equals(other: Any?): Boolean = other is Email
                override fun hashCode(): Int = Email::class.hashCode()
            }

            /** Pending SMS registration info. */
            public class Sms(
                /** Identifier from which the SMS opt-in message is received. */
                public val senderId: String
            ) : PendingInfo() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is Sms) return false
                    return senderId == other.senderId
                }

                override fun hashCode(): Int = Objects.hash(senderId)
            }
        }
    }
}

/** Returns the underlying type of a contact channel. */
public val ContactChannel.type: ChannelType
    get() = when (this) {
        is ContactChannel.Registered -> when (info) {
            is RegisteredInfo.Email -> ChannelType.EMAIL
            is RegisteredInfo.SMS -> ChannelType.SMS
        }
        is ContactChannel.Pending -> when (info) {
            is PendingInfo.Email -> ChannelType.EMAIL
            is PendingInfo.Sms -> ChannelType.SMS
        }
    }

private fun String.maskEmail(): String {
    if (isNotEmpty()) {
        val firstLetter = take(1)
        if (contains("@")) {
            val parts = split("@")
            val suffix = parts.last()
            val maskedLength = parts.first().length - 1
            val mask = "●".repeat(maskedLength)
            return "${firstLetter}${mask}@${suffix}"
        }
    }
    return this
}

private fun String.maskPhoneNumber(): String {
    return if (isNotEmpty() && length > 4) {
        "●".repeat(length - 4) + takeLast(4)
    } else {
        this
    }
}
