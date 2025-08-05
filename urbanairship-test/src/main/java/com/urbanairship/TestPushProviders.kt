package com.urbanairship

import com.urbanairship.push.PushProvider

public class TestPushProviders public constructor(config: AirshipConfigOptions) :
    PushProviders(config) {

    override fun getProvider(platform: UAirship.Platform, providerClass: String): PushProvider? {
        return null
    }

    override fun getAvailableProviders(): List<PushProvider> {
        return emptyList()
    }

    override fun getBestProvider(platform: UAirship.Platform): PushProvider? {
        return null
    }

    override val bestProvider: PushProvider? = null
}
