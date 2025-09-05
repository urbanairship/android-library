package com.urbanairship
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.base.Supplier
import com.urbanairship.config.RemoteConfigObserver
import com.urbanairship.remoteconfig.RemoteConfig
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.never

@RunWith(AndroidJUnit4::class)
public class PrivacyManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private lateinit var privacyManager: PrivacyManager
    private val remoteConfigObserver: RemoteConfigObserver = mockk(relaxed = true)
    private var disabledFeatures: PrivacyManager.Feature? = null

    @Before
    public fun setup() {
        configurePrivacyManager()
    }

    @After
    public fun tearDown() {
        dataStore.tearDown()
    }

    @Test
    public fun testResetEnabled() {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.ALL)

        assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))

        configurePrivacyManager()
        assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))

        configurePrivacyManager(resetEnabled = true)
        assertFalse(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))
    }

    @Test
    public fun testMigrateDataCollectionEnabled() {
        assertEquals(PrivacyManager.Feature.NONE, privacyManager.enabledFeatures)
        dataStore.put(PrivacyManager.DATA_COLLECTION_ENABLED_KEY, true)

        privacyManager.migrateData()
        assertEquals(PrivacyManager.Feature.ALL, privacyManager.enabledFeatures)

        assertFalse(dataStore.isSet(PrivacyManager.DATA_COLLECTION_ENABLED_KEY))
    }

    @Test
    public fun testMigrateDataCollectionDisabled() {
        privacyManager.enable(PrivacyManager.Feature.ALL)
        dataStore.put(PrivacyManager.DATA_COLLECTION_ENABLED_KEY, false)

        privacyManager.migrateData()
        assertEquals(PrivacyManager.Feature.NONE, privacyManager.enabledFeatures)

        assertFalse(dataStore.isSet(PrivacyManager.DATA_COLLECTION_ENABLED_KEY))

        privacyManager.enabledFeatures = PrivacyManager.Feature.NONE
        privacyManager.migrateData()
        assertEquals(PrivacyManager.Feature.NONE, privacyManager.enabledFeatures)
    }

    @Test
    public fun testMigrateModuleEnableFlagsWhenDisabled() {
        dataStore.put(PrivacyManager.PUSH_ENABLED_KEY, false)
        dataStore.put(PrivacyManager.ANALYTICS_ENABLED_KEY, false)
        dataStore.put(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY, false)
        dataStore.put(PrivacyManager.IAA_ENABLED_KEY, false)

        privacyManager.enable(PrivacyManager.Feature.ALL)
        privacyManager.migrateData()

        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY))
        assertFalse(dataStore.isSet(PrivacyManager.ANALYTICS_ENABLED_KEY))
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY))
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY))

        assertFalse(
            privacyManager.isAnyEnabled(
                PrivacyManager.Feature.PUSH,
                PrivacyManager.Feature.ANALYTICS,
                PrivacyManager.Feature.IN_APP_AUTOMATION
            )
        )

        privacyManager.enable(PrivacyManager.Feature.ALL)
        Assert.assertTrue(
            privacyManager.isEnabled(
                PrivacyManager.Feature.PUSH,
                PrivacyManager.Feature.ANALYTICS,
                PrivacyManager.Feature.IN_APP_AUTOMATION
            )
        )
    }

    @Test
    public fun testMigrateModuleEnableFlagsWhenEnabled() {
        dataStore.put(PrivacyManager.PUSH_ENABLED_KEY, true)
        dataStore.put(PrivacyManager.ANALYTICS_ENABLED_KEY, true)
        dataStore.put(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY, true)
        dataStore.put(PrivacyManager.IAA_ENABLED_KEY, true)

        privacyManager.enable(PrivacyManager.Feature.NONE)
        privacyManager.migrateData()

        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY))
        assertFalse(dataStore.isSet(PrivacyManager.ANALYTICS_ENABLED_KEY))
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY))
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY))

        Assert.assertFalse(
            privacyManager.isAnyEnabled(
                PrivacyManager.Feature.PUSH,
                PrivacyManager.Feature.ANALYTICS,
                PrivacyManager.Feature.IN_APP_AUTOMATION
            )
        )

        privacyManager.enable(PrivacyManager.Feature.ALL)
        Assert.assertTrue(
            privacyManager.isEnabled(
                PrivacyManager.Feature.PUSH,
                PrivacyManager.Feature.ANALYTICS,
                PrivacyManager.Feature.IN_APP_AUTOMATION
            )
        )
    }

    @Test
    public fun testEnabledSubtractConfigFeatures() {
        configurePrivacyManager(defaultFeatures = PrivacyManager.Feature.ALL)

        assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))

        disabledFeatures = PrivacyManager.Feature.PUSH
        assertFalse(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))
    }

    @Test
    public fun testDefaults() {
        Assert.assertEquals(PrivacyManager.Feature.NONE, privacyManager.enabledFeatures)

        configurePrivacyManager(defaultFeatures = PrivacyManager.Feature.ALL)
        Assert.assertEquals(PrivacyManager.Feature.ALL, privacyManager.enabledFeatures)
    }

    @Test
    public fun testEnable() {
        privacyManager.enable(PrivacyManager.Feature.CONTACTS, PrivacyManager.Feature.PUSH)
        assertEquals(
            PrivacyManager.Feature.CONTACTS or PrivacyManager.Feature.PUSH,
            privacyManager.enabledFeatures
        )

        privacyManager.enable(PrivacyManager.Feature.PUSH)
        assertEquals(
            PrivacyManager.Feature.CONTACTS or PrivacyManager.Feature.PUSH,
            privacyManager.enabledFeatures
        )

        privacyManager.enable(PrivacyManager.Feature.NONE)
        assertEquals(
            PrivacyManager.Feature.CONTACTS or PrivacyManager.Feature.PUSH,
            privacyManager.enabledFeatures
        )

        privacyManager.enable(PrivacyManager.Feature.ANALYTICS)
        assertEquals(
            PrivacyManager.Feature.CONTACTS or PrivacyManager.Feature.PUSH or PrivacyManager.Feature.ANALYTICS,
            privacyManager.enabledFeatures
        )

        privacyManager.enable(PrivacyManager.Feature.ALL)
        Assert.assertEquals(PrivacyManager.Feature.ALL, privacyManager.enabledFeatures)
    }

    @Test
    public fun testDisable() {
        privacyManager.setEnabledFeatures(
            PrivacyManager.Feature.CONTACTS,
            PrivacyManager.Feature.PUSH
        )

        privacyManager.disable(PrivacyManager.Feature.NONE)
        assertEquals(
            PrivacyManager.Feature.CONTACTS or PrivacyManager.Feature.PUSH,
            privacyManager.enabledFeatures
        )

        privacyManager.disable(PrivacyManager.Feature.ANALYTICS)
        assertEquals(
            PrivacyManager.Feature.CONTACTS or PrivacyManager.Feature.PUSH,
            privacyManager.enabledFeatures
        )

        privacyManager.disable(PrivacyManager.Feature.CONTACTS)
        Assert.assertEquals(PrivacyManager.Feature.PUSH, privacyManager.enabledFeatures)

        privacyManager.disable(PrivacyManager.Feature.ALL)
        Assert.assertEquals(PrivacyManager.Feature.NONE, privacyManager.enabledFeatures)
    }

    @Test
    public fun testIsEnabled() {
        privacyManager.enabledFeatures = PrivacyManager.Feature.NONE
        Assert.assertFalse(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))
        Assert.assertFalse(privacyManager.isEnabled(PrivacyManager.Feature.ALL))
        Assert.assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.NONE))

        privacyManager.enable(PrivacyManager.Feature.PUSH)
        Assert.assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))
        Assert.assertFalse(privacyManager.isEnabled(PrivacyManager.Feature.ALL))
        Assert.assertFalse(privacyManager.isEnabled(PrivacyManager.Feature.NONE))
        Assert.assertFalse(
            privacyManager.isEnabled(
                PrivacyManager.Feature.PUSH,
                PrivacyManager.Feature.ANALYTICS
            )
        )

        privacyManager.enable(PrivacyManager.Feature.ALL)
        Assert.assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))
        Assert.assertTrue(
            privacyManager.isEnabled(
                PrivacyManager.Feature.ANALYTICS,
                PrivacyManager.Feature.ANALYTICS
            )
        )
        Assert.assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.ALL))
        Assert.assertFalse(privacyManager.isEnabled(PrivacyManager.Feature.NONE))
        Assert.assertTrue(
            privacyManager.isEnabled(
                PrivacyManager.Feature.ANALYTICS,
                PrivacyManager.Feature.NONE
            )
        )
    }

    @Test
    public fun testIsAnyFeatureEnabled() {
        Assert.assertFalse(privacyManager.isAnyFeatureEnabled)

        privacyManager.enable(PrivacyManager.Feature.PUSH)
        Assert.assertTrue(privacyManager.isAnyFeatureEnabled)

        privacyManager.disable(PrivacyManager.Feature.PUSH)
        Assert.assertFalse(privacyManager.isAnyFeatureEnabled)
    }

    @Test
    public fun testIsAnyEnabled() {
        Assert.assertFalse(privacyManager.isAnyEnabled(PrivacyManager.Feature.ANALYTICS))

        privacyManager.enable(
            PrivacyManager.Feature.IN_APP_AUTOMATION,
            PrivacyManager.Feature.PUSH
        )
        Assert.assertFalse(privacyManager.isAnyEnabled(PrivacyManager.Feature.ANALYTICS))
        Assert.assertTrue(
            privacyManager.isAnyEnabled(
                PrivacyManager.Feature.ANALYTICS,
                PrivacyManager.Feature.PUSH
            )
        )
    }

    @Test
    public fun testSetEmptyFeatures() {
        privacyManager.enabledFeatures = PrivacyManager.Feature.ALL
        privacyManager.enable()
        assertEquals(privacyManager.enabledFeatures, PrivacyManager.Feature.ALL)

        privacyManager.disable()
        assertEquals(privacyManager.enabledFeatures, PrivacyManager.Feature.ALL)

        assertFalse(privacyManager.isEnabled())

        privacyManager.setEnabledFeatures()
        assertEquals(privacyManager.enabledFeatures, PrivacyManager.Feature.NONE)
    }

    @Test
    public fun testSingleFeature() {
        privacyManager.enabledFeatures = PrivacyManager.Feature.NONE

        privacyManager.enable(PrivacyManager.Feature.PUSH)
        assertEquals(privacyManager.enabledFeatures, PrivacyManager.Feature.PUSH)
        assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))

        privacyManager.disable(PrivacyManager.Feature.PUSH)
        assertEquals(privacyManager.enabledFeatures, PrivacyManager.Feature.NONE)

        privacyManager.setEnabledFeatures(PrivacyManager.Feature.PUSH)
        assertEquals(privacyManager.enabledFeatures, PrivacyManager.Feature.PUSH)
        assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))
    }

    @Test
    public fun testListener() {
        val listener: PrivacyManager.Listener = mockk(relaxed = true)

        privacyManager.addListener(listener)

        privacyManager.enable(PrivacyManager.Feature.PUSH)
        privacyManager.disable(PrivacyManager.Feature.PUSH)
        privacyManager.enabledFeatures = PrivacyManager.Feature.ALL

        verify(exactly = 3) { listener.onEnabledFeaturesChanged() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testUpdatesFlow(): TestResult = runTest {
        privacyManager.featureUpdates.test {
            privacyManager.enable(PrivacyManager.Feature.PUSH)
            assertEquals(PrivacyManager.Feature.PUSH, awaitItem())

            privacyManager.disable(PrivacyManager.Feature.PUSH)
            assertEquals(PrivacyManager.Feature.NONE, awaitItem())

            privacyManager.enabledFeatures = PrivacyManager.Feature.ALL
            assertEquals(PrivacyManager.Feature.ALL, awaitItem())

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testListenerOnlyCalledOnChange() {
        val listener: PrivacyManager.Listener = mockk(relaxed = true)

        privacyManager.disable(PrivacyManager.Feature.ALL)
        privacyManager.addListener(listener)

        privacyManager.disable(PrivacyManager.Feature.IN_APP_AUTOMATION)
        verify(exactly = 0) { listener.onEnabledFeaturesChanged() }

        privacyManager.enabledFeatures = PrivacyManager.Feature.ALL
        privacyManager.enable(PrivacyManager.Feature.PUSH)

        verify(exactly = 1) { listener.onEnabledFeaturesChanged() }
    }

    @Test
    public fun testIsAnyFeatureEnableIgnoreRemote() {
        disabledFeatures = PrivacyManager.Feature.PUSH
        configurePrivacyManager()
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.PUSH)

        assertTrue(privacyManager.isAnyFeatureEnabled(ignoringRemoteConfig = true))
        assertFalse(privacyManager.isAnyFeatureEnabled(ignoringRemoteConfig = false))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun configurePrivacyManager(
        defaultFeatures: PrivacyManager.Feature = PrivacyManager.Feature.NONE,
        resetEnabled: Boolean = false) {

        val remoteConfig: RemoteConfig = mockk()
        every { remoteConfig.disabledFeatures } answers { disabledFeatures }
        every { remoteConfigObserver.remoteConfig } returns remoteConfig

        privacyManager = PrivacyManager(
            dataStore = dataStore,
            defaultEnabledFeatures = defaultFeatures,
            configObserver = remoteConfigObserver,
            resetEnabledFeatures = resetEnabled,
            dispatcher = UnconfinedTestDispatcher()
        )
    }
}
