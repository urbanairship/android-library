/* Copyright Airship and Contributors */
package com.urbanairship.remoteconfig

import androidx.test.core.app.ApplicationProvider
import com.urbanairship.BaseTestCase
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestApplication
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.meteredusage.Config
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
import org.junit.Assert.assertNull
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

    private var remoteData: RemoteData = mockk(relaxed = true) {
        every { this@mockk.payloadFlow(listOf("app_config", "app_config:android")) } returns updates
    }

    private var meteredUsage: AirshipMeteredUsage = mockk(relaxed = true)

    private var privacyManager: PrivacyManager = PrivacyManager(
        TestApplication.getApplication().preferenceDataStore,
        PrivacyManager.FEATURE_ALL
    )

    private var remoteConfigManager: RemoteConfigManager = RemoteConfigManager(
        ApplicationProvider.getApplicationContext(),
        PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext()),
        TestAirshipRuntimeConfig.newTestConfig(),
        privacyManager,
        remoteData,
        testModuleAdapter,
        meteredUsage,
        testDispatcher
    )

    @Test
    public fun testMissingAirshipConfig(): TestResult = runTest {
        val listener: RemoteAirshipConfigListener = mockk()
        remoteConfigManager.addRemoteAirshipConfigListener(listener)

        val json = JsonMap.EMPTY_MAP
        val remoteDataPayload = createRemoteDataPayload("app_config", 0, json)
        updates.emit(listOf(remoteDataPayload))
        testDispatcher.scheduler.advanceUntilIdle()
        verify { listener.onRemoteConfigUpdated(RemoteAirshipConfig()) }
    }

    @Test
    public fun testAirshipConfig(): TestResult = runTest {
        val listener: RemoteAirshipConfigListener = mockk()
        remoteConfigManager.addRemoteAirshipConfigListener(listener)

        val json = jsonMapOf(
            "airship_config" to jsonMapOf(
                "remote_data_url" to "https://remote-data.examaple.com",
                "device_api_url" to "https://deivce-api.examaple.com",
                "wallet_url" to "https://wallet-api.examaple.com",
                "analytics_url" to "https://analytics-api.examaple.com",
                "metered_usage_url" to "https://metered.usage.test"
            )
        )

        val remoteDataPayload = createRemoteDataPayload("app_config", 0, json)
        updates.emit(listOf(remoteDataPayload))
        testDispatcher.scheduler.advanceUntilIdle()
        verify { listener.onRemoteConfigUpdated(RemoteAirshipConfig(json.require("airship_config"))) }
        assertEquals("https://metered.usage.test", RemoteAirshipConfig(json.require("airship_config")).meteredUsageUrl)
    }

    @Test
    public fun testMeteredUsageConfig(): TestResult = runTest {
        updates.emit(listOf(createRemoteDataPayload("app_config", 0, JsonMap.EMPTY_MAP)))
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { meteredUsage.setConfig(any()) }

        updates.emit(listOf(createRemoteDataPayload("app_config", 0, jsonMapOf("metered_usage" to JsonMap.EMPTY_MAP))))
        testDispatcher.scheduler.advanceUntilIdle()
        verify { meteredUsage.setConfig(Config.default()) }

        val json = jsonMapOf(
            "metered_usage" to jsonMapOf(
                "isEnabled" to true,
                "initialDelay" to 22L,
                "interval" to 12L
            )
        )

        val remoteDataPayload = createRemoteDataPayload("app_config", 0, json)
        updates.emit(listOf(remoteDataPayload))
        testDispatcher.scheduler.advanceUntilIdle()
        verify { meteredUsage.setConfig(Config(true, 22, 12)) }
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
        assertTrue(testModuleAdapter.sentConfig.keys.containsAll(Modules.ALL_MODULES))
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
    public fun testEnableContactSource(): TestResult = runTest {
        val json = jsonMapOf("fetch_contact_remote_data" to true)
        val common = createRemoteDataPayload("app_config", 0, json)

        // Notify the updates
        updates.emit(listOf(common))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { remoteData.setContactSourceEnabled(true) }
    }

    @Test
    public fun testDisableContactSource(): TestResult = runTest {
        val json = jsonMapOf("fetch_contact_remote_data" to false)
        val common = createRemoteDataPayload("app_config", 0, json)

        // Notify the updates
        updates.emit(listOf(common))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { remoteData.setContactSourceEnabled(false) }
    }

    @Test
    public fun testDisableContactSourceNotSet(): TestResult = runTest {
        val common = createRemoteDataPayload("app_config", 0, JsonMap.EMPTY_MAP)

        // Notify the updates
        updates.emit(listOf(common))
        testDispatcher.scheduler.advanceUntilIdle()

        verify { remoteData.setContactSourceEnabled(false) }
    }

    @Test
    public fun testRemoteConfig(): TestResult = runTest {
        val fooConfig = jsonMapOf("some_config_name" to "some_config_value")
        val barConfig = jsonMapOf("some_other_config_name" to "some_other_config_value")
        val commonData = jsonMapOf("foo" to fooConfig, "bar" to barConfig)

        val androidFooOverrideConfig = jsonMapOf("some_other_config_name" to "some_other_config_value")
        val androidData = jsonMapOf("foo" to androidFooOverrideConfig)
        val common = createRemoteDataPayload("app_config", System.currentTimeMillis(), commonData)
        val android = createRemoteDataPayload("app_config:android", System.currentTimeMillis(), androidData)

        // Notify the updates
        updates.emit(listOf(common, android))
        testDispatcher.scheduler.advanceUntilIdle()

        val config: Map<String, JsonMap?> = testModuleAdapter.sentConfig
        assertEquals(2 + Modules.ALL_MODULES.size, config.size)
        assertEquals(androidFooOverrideConfig, config["foo"])
        assertEquals(barConfig, config["bar"])

        // All modules without config payloads should be called with a null config
        for (module in Modules.ALL_MODULES) {
            assertNull(config[module])
            assertTrue(testModuleAdapter.sentConfig.keys.contains(module))
        }
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
        var sentConfig: MutableMap<String, JsonMap?> = HashMap()
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
            sentConfig.clear()
        }

        override fun onNewConfig(module: String, config: JsonMap?) {
            check(sentConfig[module] == null) { "Make sure to reset test sender between checks" }
            sentConfig[module] = config
        }
    }
}
