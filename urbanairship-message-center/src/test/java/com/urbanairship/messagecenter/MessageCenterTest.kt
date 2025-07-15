/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowApplication

/** Tests for [MessageCenter] */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class MessageCenterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testDispatcher = StandardTestDispatcher()
    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()

    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val shadowApplication: ShadowApplication = Shadows.shadowOf(context as Application?)
    private val privacyManager = mockk<PrivacyManager>(relaxUnitFun = true) {
        every { isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns true
    }
    private val pushManager = mockk<PushManager>(relaxUnitFun = true) {}
    private val inbox = mockk<Inbox>(relaxUnitFun = true) {
        coEvery { fetchMessages() } returns true
    }

    private val messageCenter: MessageCenter = MessageCenter(
        context = context,
        dataStore = dataStore,
        privacyManager = privacyManager,
        inbox = inbox,
        pushManager = pushManager,
        dispatcher = unconfinedTestDispatcher
    )

    private lateinit var pushListener: PushListener
    private lateinit var privacyManagerListener: PrivacyManager.Listener

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
        messageCenter.initialize()

        val pushListenerSlot = slot<PushListener>()
        verify { pushManager.addInternalPushListener(capture(pushListenerSlot)) }
        pushListener = pushListenerSlot.captured

        val privacyListenerSlot = slot<PrivacyManager.Listener>()
        verify { privacyManager.addListener(capture(privacyListenerSlot)) }
        privacyManagerListener = privacyListenerSlot.captured
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testShowMessageCenter() {
        messageCenter.showMessageCenter()
        val intent = shadowApplication.nextStartedActivity
        assertEquals(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION, intent.action)
        assertEquals(context.packageName, intent.getPackage())
    }

    @Test
    public fun testShowMessageCenterWhenDisabled() {
        every { privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER) } returns false

        messageCenter.showMessageCenter()

        assertNull(shadowApplication.nextStartedActivity)
    }
}
