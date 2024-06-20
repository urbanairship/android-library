/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PrivacyManager
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RemoteConfigTest {

    @Test
    public fun testParseEmpty() {
        assert(RemoteConfig() == RemoteConfig.fromJson(JsonValue.NULL))
        assert(RemoteConfig() == RemoteConfig.fromJson(JsonMap.EMPTY_MAP))
    }

    @Test
    public fun testJson() {
        val json = """
            {
               "metered_usage":{
                  "initial_delay_ms":100,
                  "interval_ms":200,
                  "enabled":true
               },
               "airship_config":{
                  "device_api_url":"device-api-url",
                  "analytics_url":"analytics-url",
                  "wallet_url":"wallet-url",
                  "remote_data_url":"remote-data-url",
                  "metered_usage_url":"metered-usage-url"
               },
               "contact_config":{
                  "max_cra_resolve_age_ms":300,
                  "foreground_resolve_interval_ms":400
               },
               "fetch_contact_remote_data":true,
               "disabled_features": ["push", "analytics"],
               "remote_data_refresh_interval": 21,
               "in_app_config": {
                    "additional_audience_check": {
                        "enabled": true,
                        "context": "json-value",
                        "url": "https://test.url"
                    },
                    "queue": {
                        "max_concurrent_operations": 3,
                        "max_pending_results": 4,
                        "initial_back_off_seconds": 5,
                        "max_back_off_seconds": 6
                    }
                }
            }
        """

        val expected = RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                remoteDataUrl = "remote-data-url",
                deviceApiUrl = "device-api-url",
                walletUrl = "wallet-url",
                analyticsUrl = "analytics-url",
                meteredUsageUrl = "metered-usage-url"
            ),
            meteredUsageConfig = MeteredUsageConfig(
                isEnabled = true,
                initialDelayMs = 100,
                intervalMs = 200
            ),
            contactConfig = ContactConfig(
                foregroundIntervalMs = 400,
                channelRegistrationMaxResolveAgeMs = 300
            ),
            fetchContactRemoteData = true,
            disabledFeatures = PrivacyManager.Feature.PUSH or PrivacyManager.Feature.ANALYTICS,
            remoteDataRefreshInterval = 21L,
            iaaConfig = IAAConfig(
                retryingQueue = RetryingQueueConfig(
                    maxConcurrentOperations = 3,
                    maxPendingResults = 4,
                    initialBackoff = 5,
                    maxBackOff = 6
                ),
                additionalAudienceCheck = AdditionalAudienceCheckConfig(
                    isEnabled = true,
                    context = JsonValue.wrap("json-value"),
                    url = "https://test.url"
                )
            )
        )

        assert(expected == RemoteConfig.fromJson(JsonValue.parseString(json)))
        assert(expected == RemoteConfig.fromJson(expected.toJsonValue()))
    }
}
