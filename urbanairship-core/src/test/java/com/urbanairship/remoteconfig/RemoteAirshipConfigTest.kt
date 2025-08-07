/* Copyright Airship and Contributors */
package com.urbanairship.remoteconfig

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteAirshipConfigTest {

    @Test
    fun testJson() {
        val json = jsonMapOf(
            "device_api_url" to "https://deivce-api.examaple.com",
            "remote_data_url" to "https://remote-data.examaple.com",
            "wallet_url" to "https://wallet-api.examaple.com",
            "analytics_url" to "https://analytics-api.examaple.com",
            "metered_usage_url" to "https://metere.usage.test"
        ).toJsonValue()

        val airshipConfig = RemoteAirshipConfig.fromJson(json)
        Assert.assertEquals("https://deivce-api.examaple.com", airshipConfig.deviceApiUrl)
        Assert.assertEquals("https://remote-data.examaple.com", airshipConfig.remoteDataUrl)
        Assert.assertEquals("https://wallet-api.examaple.com", airshipConfig.walletUrl)
        Assert.assertEquals("https://analytics-api.examaple.com", airshipConfig.analyticsUrl)
        Assert.assertEquals("https://metere.usage.test", airshipConfig.meteredUsageUrl)

        Assert.assertEquals(json, airshipConfig.toJsonValue())
    }

    @Test
    fun testEmptyJson() {
        val airshipConfig = RemoteAirshipConfig.fromJson(JsonValue.NULL)
        Assert.assertNull(airshipConfig.deviceApiUrl)
        Assert.assertNull(airshipConfig.remoteDataUrl)
        Assert.assertNull(airshipConfig.walletUrl)
        Assert.assertNull(airshipConfig.analyticsUrl)
        Assert.assertNull(airshipConfig.meteredUsageUrl)
    }
}
