package com.urbanairship.preferencecenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.push.PushManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ConditionStateMonitorTest {

    private val testDispatcher = TestCoroutineDispatcher()

    private val mockChannel = mock<AirshipChannel> {}
    private val mockPushManager = mock<PushManager> {}

    private lateinit var conditionStateMonitor: ConditionStateMonitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        conditionStateMonitor = ConditionStateMonitor(mockChannel, mockPushManager)
    }

    @After
    fun tearDown() {
        testDispatcher.cleanupTestCoroutines()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = testDispatcher.runBlockingTest {
        whenever(mockPushManager.isOptIn).thenReturn(true)

        conditionStateMonitor.states.test {
            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = true))
        }
    }

    @Test
    fun testChannelCreatedStateChange() = testDispatcher.runBlockingTest {
        whenever(mockPushManager.isOptIn).thenReturn(false)

        conditionStateMonitor.states.test {
            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = false))

            whenever(mockPushManager.isOptIn).thenReturn(true)

            withChannelListener {
                onChannelCreated("created-channel-id")
            }
            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = true))
        }
    }

    @Test
    fun testChannelUpdatedStateChange() = testDispatcher.runBlockingTest {
        whenever(mockPushManager.isOptIn).thenReturn(false)

        conditionStateMonitor.states.test {
            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = false))

            whenever(mockPushManager.isOptIn).thenReturn(true)

            withChannelListener {
                onChannelUpdated("updated-channel-id")
            }
            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = true))
        }
    }

    @Test
    fun testCurrentState() {
        whenever(mockPushManager.isOptIn).thenReturn(false)
        assertThat(conditionStateMonitor.currentState)
            .isEqualTo(Condition.State(isOptedIn = false))

        whenever(mockPushManager.isOptIn).thenReturn(true)
        assertThat(conditionStateMonitor.currentState)
            .isEqualTo(Condition.State(isOptedIn = true))
    }

    private fun withChannelListener(block: AirshipChannelListener.() -> Unit) {
        argumentCaptor<AirshipChannelListener>().apply {
            verify(mockChannel).addChannelListener(capture())
            block.invoke(firstValue)
        }
    }
}
