/* Copyright Airship and Contributors */

package com.urbanairship.push

/** Types that indicate which Push Provider is currently in use by the Airship SDK. */
public enum class PushProviderType(
    private val deliveryType: String?
) {
    /** The Airship SDK is currently using the Amazon Device Messaging (ADM) push provider. */
    ADM(PushProvider.ADM_DELIVERY_TYPE),

    /** The Airship SDK is currently using the Firebase Cloud Messaging (FCM) push provider. */
    FCM(PushProvider.FCM_DELIVERY_TYPE),

    /** The Airship SDK is currently using the HUAWEI Mobile Services (HMS) push provider. */
    HMS(PushProvider.HMS_DELIVERY_TYPE),

    /**
     * The Airship SDK is not currently using any push providers.
     *
     * If this is unexpected, please verify your push configuration and ensure you've included at
     * least one Airship push provider module as a library dependency.
     */
    NONE(null);

    internal companion object {
        private val VALUES_BY_TYPE by lazy { values().associateBy(PushProviderType::deliveryType) }

        /** Returns the `PushProviderType` corresponding to the given [PushProvider]. */
        fun from(provider: PushProvider?): PushProviderType =
            VALUES_BY_TYPE[provider?.deliveryType] ?: NONE
    }
}
