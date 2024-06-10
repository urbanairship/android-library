/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.isoDateAsMilliseconds
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.DateUtils

/**
 * Channels associated with a contact.
 */
public sealed class ContactChannel: JsonSerializable {

    /**
     * Masked address.
     */
    public abstract val maskedAddress: String

    /**
     * Channel type.
     */
    public abstract val channelType: ChannelType

    /**
     * If the channel is registered with the contact.
     */
    public abstract val isRegistered: Boolean

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        TYPE_KEY to this.channelType.name,
        INFO_KEY to when (this) {
            is Sms -> this.registrationInfo
            is Email -> this.registrationInfo
        }
    ).toJsonValue()

    public companion object {

        private const val INFO_KEY = "info"
        private const val TYPE_KEY = "type"

        private const val PENDING_TYPE = "pending"
        private const val REGISTERED_TYPE = "registered"

        private const val ADDRESS_KEY = "address"
        private const val OPTIONS_KEY = "options"
        private const val CHANNEL_ID_KEY = "channel_id"

        // EMAIL
        private const val COMMERCIAL_OPTED_IN_KEY = "commercial_opted_in"
        private const val COMMERCIAL_OPTED_OUT_KEY = "commercial_opted_out"
        private const val TRANSACTIONAL_OPTED_IN_KEY = "transactional_opted_in"
        private const val TRANSACTIONAL_OPTED_OUT_KEY = "transactional_opted_out"

        // SMS
        private const val OPT_IN_KEY = "opt_in"
        private const val SENDER_ID_KEY = "sender"

        /**
         * Parses channel contact from JSON.
         * @param jsonValue The json value.
         * @return Contact channel.
         * @throws JsonException
         */
        @Throws(JsonException::class)
        public fun fromJson(jsonValue: JsonValue): ContactChannel {
            val map = jsonValue.requireMap()
            return when (val type = ChannelType.fromJson(map.require(TYPE_KEY))) {
                ChannelType.SMS -> Sms(
                    registrationInfo = Sms.RegistrationInfo.fromJson(
                        map.require(INFO_KEY)
                    )
                )
                ChannelType.EMAIL-> Email(
                    registrationInfo = Email.RegistrationInfo.fromJson(
                        map.require(INFO_KEY)
                    )
                )
                else -> throw JsonException("unexpected type $type")
            }
        }
    }

    /**
     * Sms channel.
     */
    public class Sms @VisibleForTesting @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
        /**
         * Registration info.
         */
        public val registrationInfo: RegistrationInfo
    ) : ContactChannel() {

        public override val channelType: ChannelType = ChannelType.SMS

        public override val maskedAddress: String
            get() {
                return when (this.registrationInfo) {
                    is RegistrationInfo.Pending -> this.registrationInfo.address.maskPhoneNumber()
                    is RegistrationInfo.Registered -> this.registrationInfo.maskedAddress.replaceAsterisks()
                }
            }

        public override val isRegistered: Boolean
            get() {
                return when (this.registrationInfo) {
                    is RegistrationInfo.Pending -> false
                    is RegistrationInfo.Registered -> true
                }
            }

        /**
         * Sender ID.
         */
        public val senderId: String
            get() {
                return when (this.registrationInfo) {
                    is RegistrationInfo.Pending -> this.registrationInfo.registrationOptions.senderId
                    is RegistrationInfo.Registered -> this.registrationInfo.senderId
                }
            }

        /**
         * Sms registration info.
         */
        public sealed class RegistrationInfo : JsonSerializable {

            /**
             * Indicates the SMS has been registered.
             */
            public class Registered @VisibleForTesting @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
                /**
                 * Channel ID.
                 */
                public val channelId: String,

                /**
                 * Masked MSISDN address.
                 */
                public val maskedAddress: String,

                /**
                 * If its opted in or not.
                 */
                public val isOptIn: Boolean,

                /**
                 * Sender ID.
                 */
                public val senderId: String
            ) : RegistrationInfo() {

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as Registered

                    if (channelId != other.channelId) return false
                    if (maskedAddress != other.maskedAddress) return false
                    if (isOptIn != other.isOptIn) return false
                    if (senderId != other.senderId) return false

                    return true
                }

                override fun hashCode(): Int = ObjectsCompat.hash(
                    channelId, maskedAddress, isOptIn, senderId
                )

                override fun toString(): String {
                    return "Registered(channelId='$channelId', maskedAddress='$maskedAddress', isOptIn=$isOptIn, senderId='$senderId')"
                }

            }

            /**
             * Indicates the SMS is pending registration.
             */
            public class Pending @VisibleForTesting @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
                /**
                 * The MSISDN address.
                 */
                public val address: String,

                /**
                 * Registration options.
                 */
                public val registrationOptions: SmsRegistrationOptions
            ) : RegistrationInfo() {

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as Pending

                    if (address != other.address) return false
                    if (registrationOptions != other.registrationOptions) return false

                    return true
                }

                override fun hashCode(): Int = ObjectsCompat.hash(
                    address, registrationOptions
                )

                override fun toString(): String {
                    return "Pending(address='$address', registrationOptions=$registrationOptions)"
                }
            }

            @Throws(JsonException::class)
            override fun toJsonValue(): JsonValue = when (this) {
                is Pending -> jsonMapOf(
                    TYPE_KEY to PENDING_TYPE,
                    ADDRESS_KEY to address,
                    OPTIONS_KEY to registrationOptions
                )

                is Registered -> jsonMapOf(
                    TYPE_KEY to REGISTERED_TYPE,
                    ADDRESS_KEY to maskedAddress,
                    OPT_IN_KEY to isOptIn,
                    CHANNEL_ID_KEY to channelId,
                    SENDER_ID_KEY to senderId
                )
            }.toJsonValue()

            public companion object {

                /**
                 * Parses registration info from JSON.
                 * @param jsonValue The json value.
                 * @return Registration info.
                 * @throws JsonException
                 */
                @Throws(JsonException::class)
                public fun fromJson(jsonValue: JsonValue): RegistrationInfo {
                    val map = jsonValue.requireMap()
                    return when (val type = map.requireField<String>(TYPE_KEY)) {
                        PENDING_TYPE -> Pending(
                            address = map.requireField(ADDRESS_KEY),
                            registrationOptions = SmsRegistrationOptions.fromJson(
                                map.require(OPTIONS_KEY)
                            )
                        )

                        REGISTERED_TYPE -> Registered(
                            maskedAddress = map.requireField(ADDRESS_KEY),
                            isOptIn = map.requireField(OPT_IN_KEY),
                            channelId = map.requireField(CHANNEL_ID_KEY),
                            senderId = map.requireField(SENDER_ID_KEY)
                        )

                        else -> throw JsonException("Unexpected type $type")
                    }
                }
            }
        }

        override fun toString(): String {
            return "Sms(registrationInfo=$registrationInfo)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Sms

            if (registrationInfo != other.registrationInfo) return false
            if (channelType != other.channelType) return false

            return true
        }

        override fun hashCode(): Int = ObjectsCompat.hash(
            registrationInfo
        )

    }

    /**
     * Email channel
     */
    public class Email @VisibleForTesting @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
        /**
         * Registration info.
         */
        public val registrationInfo: RegistrationInfo
    ) : ContactChannel() {

        public override val channelType: ChannelType = ChannelType.EMAIL

        public override val maskedAddress: String
            get() {
                return when (this.registrationInfo) {
                    is RegistrationInfo.Pending -> this.registrationInfo.address.maskEmail()
                    is RegistrationInfo.Registered -> this.registrationInfo.maskedAddress.replaceAsterisks()
                }
            }

        public override val isRegistered: Boolean
            get() {
                return when (this.registrationInfo) {
                    is RegistrationInfo.Pending -> false
                    is RegistrationInfo.Registered -> true
                }
            }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Email

            if (registrationInfo != other.registrationInfo) return false
            if (channelType != other.channelType) return false

            return true
        }

        override fun hashCode(): Int = ObjectsCompat.hashCode(registrationInfo)
        override fun toString(): String {
            return "Email(registrationInfo=$registrationInfo)"
        }

        /**
         * Email registration info.
         */
        public sealed class RegistrationInfo : JsonSerializable {

            /**
             * Indicates the Email is registered.
             */
            public class Registered @VisibleForTesting @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
                /**
                 * Channel ID.
                 */
                public val channelId: String,

                /**
                 * Masked email address.
                 */
                public val maskedAddress: String,

                /** Date the user opted in to transactional emails. */
                public val transactionalOptedIn: Long? = null,

                /** Date the user opted out of transactional emails. */
                public val transactionalOptedOut: Long? = null,
                /**
                 * Date the user opted in to commercial emails.
                 *
                 * This field determines the email opted-in state.
                 */
                public val commercialOptedIn: Long? = null,

                /** Date the user opted out of commercial emails. */
                public val commercialOptedOut: Long? = null
            ) : RegistrationInfo() {

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as Registered

                    if (channelId != other.channelId) return false
                    if (maskedAddress != other.maskedAddress) return false
                    if (transactionalOptedIn != other.transactionalOptedIn) return false
                    if (transactionalOptedOut != other.transactionalOptedOut) return false
                    if (commercialOptedIn != other.commercialOptedIn) return false
                    if (commercialOptedOut != other.commercialOptedOut) return false

                    return true
                }

                override fun hashCode(): Int = ObjectsCompat.hash(
                    channelId, maskedAddress, transactionalOptedIn, transactionalOptedOut,
                    commercialOptedIn, commercialOptedOut
                )

                override fun toString(): String {
                    return "Registered(channelId='$channelId', maskedAddress='$maskedAddress', transactionalOptedIn=$transactionalOptedIn, transactionalOptedOut=$transactionalOptedOut, commercialOptedIn=$commercialOptedIn, commercialOptedOut=$commercialOptedOut)"
                }
            }

            /**
             * Indicates the Email is pending registration.
             */
            public class Pending @VisibleForTesting @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
                /**
                 * Email address.
                 */
                public val address: String,
                /**
                 * Registration options.
                 */
                public val registrationOptions: EmailRegistrationOptions
            ) : RegistrationInfo() {

                override fun toString(): String {
                    return "Pending(address='$address', registrationOptions=$registrationOptions)"
                }

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as Pending

                    if (address != other.address) return false
                    if (registrationOptions != other.registrationOptions) return false

                    return true
                }

                override fun hashCode(): Int = ObjectsCompat.hash(
                    address, registrationOptions
                )
            }

            @Throws(JsonException::class)
            override fun toJsonValue(): JsonValue = when (this) {
                is Pending -> jsonMapOf(
                    TYPE_KEY to PENDING_TYPE,
                    ADDRESS_KEY to address,
                    OPTIONS_KEY to registrationOptions
                )

                is Registered -> jsonMapOf(TYPE_KEY to REGISTERED_TYPE,
                    ADDRESS_KEY to maskedAddress,
                    CHANNEL_ID_KEY to channelId,
                    COMMERCIAL_OPTED_IN_KEY to this.commercialOptedIn?.let {
                        DateUtils.createIso8601TimeStamp(it)
                    },
                    COMMERCIAL_OPTED_OUT_KEY to this.commercialOptedOut?.let {
                        DateUtils.createIso8601TimeStamp(it)
                    },
                    TRANSACTIONAL_OPTED_IN_KEY to this.transactionalOptedIn?.let {
                        DateUtils.createIso8601TimeStamp(it)
                    },
                    TRANSACTIONAL_OPTED_OUT_KEY to this.transactionalOptedOut?.let {
                        DateUtils.createIso8601TimeStamp(it)
                    })
            }.toJsonValue()

            public companion object {

                /**
                 * Parses registration info from JSON.
                 * @param jsonValue The json value.
                 * @return Registration info.
                 * @throws JsonException
                 */
                @Throws(JsonException::class)
                public fun fromJson(jsonValue: JsonValue): RegistrationInfo {
                    val map = jsonValue.requireMap()
                    return when (val type = map.requireField<String>(TYPE_KEY)) {
                        PENDING_TYPE -> Pending(
                            address = map.requireField(ADDRESS_KEY),
                            registrationOptions = EmailRegistrationOptions.fromJson(
                                map.require(OPTIONS_KEY)
                            )
                        )

                        REGISTERED_TYPE -> Registered(
                            maskedAddress = map.requireField(ADDRESS_KEY),
                            channelId = map.requireField(CHANNEL_ID_KEY),
                            commercialOptedIn = map.isoDateAsMilliseconds(COMMERCIAL_OPTED_IN_KEY),
                            commercialOptedOut = map.isoDateAsMilliseconds(COMMERCIAL_OPTED_OUT_KEY),
                            transactionalOptedIn = map.isoDateAsMilliseconds(
                                TRANSACTIONAL_OPTED_IN_KEY
                            ),
                            transactionalOptedOut = map.isoDateAsMilliseconds(
                                TRANSACTIONAL_OPTED_OUT_KEY
                            )
                        )

                        else -> throw JsonException("Unexpected type $type")
                    }
                }
            }

        }
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

private fun String.replaceAsterisks(): String = replace("*", "●")
