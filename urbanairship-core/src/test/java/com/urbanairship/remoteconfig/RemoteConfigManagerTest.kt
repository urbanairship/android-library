/* Copyright Airship and Contributors */
package com.urbanairship.remoteconfig

import androidx.test.core.app.ApplicationProvider
import com.urbanairship.BaseTestCase
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataPayload
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RemoteConfigManager] tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class RemoteConfigManagerTest : BaseTestCase() {
    private val testDispatcher = StandardTestDispatcher()

    private var testModuleAdapter: TestModuleAdapter = TestModuleAdapter()
    private val updates: MutableStateFlow<List<RemoteDataPayload>> = MutableStateFlow(emptyList())
    private val config: AirshipRuntimeConfig = mockk(relaxed = true)

    private var remoteData: RemoteData = mockk(relaxed = true) {
        every { this@mockk.payloadFlow(listOf("app_config", "app_config:android")) } returns updates
    }

    private var privacyManager: PrivacyManager = PrivacyManager(
        TestApplication.getApplication().preferenceDataStore,
        PrivacyManager.FEATURE_ALL
    )

    private var remoteConfigManager: RemoteConfigManager = RemoteConfigManager(
        ApplicationProvider.getApplicationContext(),
        PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext()),
        config,
        privacyManager,
        remoteData,
        testModuleAdapter,
        testDispatcher
    )

    @Test
    public fun testMissingAirshipConfig(): TestResult = runTest {
        val json = JsonMap.EMPTY_MAP
        val remoteDataPayload = createRemoteDataPayload("app_config", 0, json)
        updates.emit(listOf(remoteDataPayload))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { config.updateRemoteConfig(RemoteConfig()) }
    }

    @Test
    public fun testAirshipConfig(): TestResult = runTest {

        val json = jsonMapOf(
            "airship_config" to jsonMapOf(
                "remote_data_url" to "https://remote-data.examaple.com",
                "device_api_url" to "https://deivce-api.examaple.com",
                "wallet_url" to "https://wallet-api.examaple.com",
                "analytics_url" to "https://analytics-api.examaple.com",
                "metered_usage_url" to "https://metered.usage.test"
            )
        )
        val airshipConfig = RemoteAirshipConfig(json.require("airship_config"))

        val remoteDataPayload = createRemoteDataPayload("app_config", 0, json)
        updates.emit(listOf(remoteDataPayload))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { config.updateRemoteConfig(RemoteConfig(airshipConfig = airshipConfig)) }
        assertEquals("https://metered.usage.test", airshipConfig.meteredUsageUrl)
    }

    @Test
    public fun testDisableComponents(): TestResult = runTest {
        val common = createDisablePayload("app_config", 0, Modules.PUSH_MODULE)
        var platform = createDisablePayload("app_config:android", 0, Modules.ALL_MODULES)

        // Notify the updates
        updates.emit(listOf(common, platform))
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify they are all disabled
        assertTrue(testModuleAdapter.disabledModules.containsAll(Modules.ALL_MODULES))

        // Clear common
        platform = createRemoteDataPayload("app_config:android", 0, JsonMap.EMPTY_MAP)
        testModuleAdapter.reset()

        // Notify the updates
        updates.emit(listOf(common, platform))
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify only push is disabled
        val modules: MutableList<String> = ArrayList(Modules.ALL_MODULES)
        modules.remove(Modules.PUSH_MODULE)
        assertTrue(testModuleAdapter.enabledModules.containsAll(modules))
        assertTrue(testModuleAdapter.disabledModules.contains(Modules.PUSH_MODULE))
    }

    @Test
    public fun testRemoteDataForegroundRefreshInterval(): TestResult = runTest {
        val common = createDisablePayload("app_config", 9, Modules.ALL_MODULES)
        val platform = createDisablePayload("app_config:android", 10, Modules.PUSH_MODULE)

        // Notify the updates
        updates.emit(listOf(common, platform))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { remoteData.foregroundRefreshInterval = 10000 }
    }

    @Test
    public fun testRemoteConfig(): TestResult = runTest {
        val commonRemoteConfig = RemoteConfig(
            meteredUsageConfig = MeteredUsageConfig(
                true, 10, 100
            ),
            contactConfig = ContactConfig(
                10, 100
            )
        )

        val platformRemoteConfig = RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                "some-url"
            ),
            contactConfig = ContactConfig(
                null, 200
            )
        )

        val common = createRemoteDataPayload("app_config", System.currentTimeMillis(), commonRemoteConfig.toJsonValue().requireMap())
        val android = createRemoteDataPayload("app_config:android", System.currentTimeMillis(), platformRemoteConfig.toJsonValue().requireMap())

        // Notify the updates
        updates.emit(listOf(common, android))
        testDispatcher.scheduler.advanceUntilIdle()

        val expected = RemoteConfig(
            airshipConfig = platformRemoteConfig.airshipConfig,
            meteredUsageConfig = commonRemoteConfig.meteredUsageConfig,
            contactConfig = platformRemoteConfig.contactConfig
        )

        verify { config.updateRemoteConfig(expected) }
    }

    @Test
    public fun testSubscription(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE)
        val common = createDisablePayload("app_config", 9, Modules.ALL_MODULES)
        updates.emit(listOf(common))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(testModuleAdapter.disabledModules.isEmpty())
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ANALYTICS)
        updates.emit(listOf(common))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(testModuleAdapter.disabledModules.isEmpty())
    }

    private fun createRemoteDataPayload(
        type: String,
        timeStamp: Long,
        data: JsonMap
    ): RemoteDataPayload {
        return RemoteDataPayload(type, timeStamp, data, null)
    }

    private fun createDisablePayload(
        type: String,
        refreshInterval: Long,
        modules: Collection<String>
    ): RemoteDataPayload {
        val disable = jsonMapOf(
            "modules" to modules,
            "remote_data_refresh_interval" to refreshInterval
        )

        val data = jsonMapOf("disable_features" to listOf(disable))

        return createRemoteDataPayload(type, System.currentTimeMillis(), data)
    }

    private fun createDisablePayload(
        type: String,
        refreshInterval: Long,
        vararg modules: String
    ): RemoteDataPayload {
        return createDisablePayload(type, refreshInterval, modules.toList())
    }

    /**
     * Module adapter that just tracks calls from the data manager to the adapter.
     */
    private inner class TestModuleAdapter : ModuleAdapter() {

        var enabledModules: MutableList<String> = ArrayList()
        var disabledModules: MutableList<String> = ArrayList()

        override fun setComponentEnabled(module: String, enabled: Boolean) {
            if (enabled) {
                enabledModules.add(module)
            } else {
                disabledModules.add(module)
            }
        }

        fun reset() {
            enabledModules.clear()
            disabledModules.clear()
        }
    }
}
