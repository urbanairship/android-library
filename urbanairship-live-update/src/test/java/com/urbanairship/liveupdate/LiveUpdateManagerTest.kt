package com.urbanairship.liveupdate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestRequestSession
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.push.PushManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LiveUpdateManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val config: AirshipRuntimeConfig = mockk {
        every { configOptions } returns mockk()
        every { platform } returns Platform.ANDROID
        every { requestSession } returns TestRequestSession()
    }
    private val pushManager: PushManager = mockk(relaxUnitFun = true)
    private val channel: AirshipChannel = mockk(relaxed = true)
    private val dao: LiveUpdateDao = mockk()
    private val database: LiveUpdateDatabase = mockk {
        every { liveUpdateDao() } returns dao
    }
    private val registrar: LiveUpdateRegistrar = mockk(relaxUnitFun = true)

    private lateinit var dataStore: PreferenceStore
    private lateinit var privacyManager: PrivacyManager

    private lateinit var liveUpdateManager: LiveUpdateManager

    @Before
    public fun setUp() {
        dataStore = PreferenceStore.inMemoryStore(context)
        privacyManager = PrivacyManager(dataStore, PrivacyManager.Feature.ALL)

        liveUpdateManager = LiveUpdateManager(
            context = context,
            dataStore = dataStore,
            config = config,
            privacyManager = privacyManager,
            channel = channel,
            pushManager = pushManager,
            db = database,
            registrar = registrar
        )
    }

    @Test
    public fun testInit() {
        liveUpdateManager.init()

        verify { pushManager.addPushListener(any()) }
        verify(exactly = 0) { registrar.clearAll(any()) }

        // Not during init: handlers are registered from the takeOff onReady callback, which runs
        // after init(), so the cleanup could not yet tell which Live Updates use notifications.
        verify(exactly = 0) { registrar.endStaleLiveUpdates() }
    }

    @Test
    public fun testOnAirshipReadyEndsStaleLiveUpdates() {
        liveUpdateManager.init()
        liveUpdateManager.onAirshipReady()

        // Exactly once per process.
        verify(exactly = 1) { registrar.endStaleLiveUpdates() }
    }

    @Test
    public fun testInitWithPushDisabled() {
        val store = PreferenceStore.inMemoryStore(context)
        val manager = LiveUpdateManager(
            context = context,
            dataStore = store,
            config = config,
            privacyManager = PrivacyManager(store, PrivacyManager.Feature.NONE),
            channel = channel,
            pushManager = pushManager,
            db = database,
            registrar = registrar
        )

        manager.init()
        manager.onAirshipReady()

        verify(exactly = 1) { registrar.clearAll(any()) }
        // Push is disabled, so there is no need to check staleness.
        verify(exactly = 0) { registrar.endStaleLiveUpdates() }
    }
}
