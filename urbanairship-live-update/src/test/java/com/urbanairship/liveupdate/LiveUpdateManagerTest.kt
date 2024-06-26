package com.urbanairship.liveupdate

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.TestRequestSession
import com.urbanairship.UAirship.ANDROID_PLATFORM
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.push.PushManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LiveUpdateManagerTest {

    private val config: AirshipRuntimeConfig = mockk {
        every { configOptions } returns mockk()
        every { platform } returns ANDROID_PLATFORM
        every { requestSession } returns TestRequestSession()
    }
    private val pushManager: PushManager = mockk(relaxUnitFun = true)
    private val channel: AirshipChannel = mockk(relaxed = true)
    private val dao: LiveUpdateDao = mockk()
    private val database: LiveUpdateDatabase = mockk {
        every { liveUpdateDao() } returns dao
    }
    private val registrar: LiveUpdateRegistrar = mockk(relaxUnitFun = true)

    private lateinit var dataStore: PreferenceDataStore
    private lateinit var privacyManager: PrivacyManager

    private lateinit var liveUpdateManager: LiveUpdateManager

    @Before
    public fun setUp() {
        dataStore = PreferenceDataStore.inMemoryStore(TestApplication.getApplication())
        privacyManager = PrivacyManager(dataStore, PrivacyManager.Feature.ALL)

        liveUpdateManager = LiveUpdateManager(
            context = TestApplication.getApplication(),
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
        verify { channel.addChannelListener(any()) }
    }
}
