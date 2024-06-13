package com.urbanairship.preferencecenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushNotificationStatus
import com.urbanairship.push.pushNotificationStatusFlow
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ConditionStateMonitorTest {

    private val stateFlow = MutableStateFlow(PushNotificationStatus(
        isUserNotificationsEnabled = true,
        areNotificationsAllowed = true,
        isPushPrivacyFeatureEnabled = true,
        isPushTokenRegistered = true
    ))

    private val testDispatcher = StandardTestDispatcher()
    private val mockPushManager = mockk<PushManager> {
        mockkStatic(PushManager::pushNotificationStatusFlow)
        every { this@mockk.pushNotificationStatusFlow } returns stateFlow
    }

    private lateinit var conditionStateMonitor: ConditionStateMonitor

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        conditionStateMonitor = ConditionStateMonitor(mockPushManager)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testInitialState(): TestResult = runTest {
        conditionStateMonitor.states.test {
            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = true))
        }
    }

    @Test
    public fun testCurrentState(): TestResult = runTest {
        every { mockPushManager.pushNotificationStatus } returnsMany listOf(
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = true
            ),
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = false
            ),
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = false,
                isPushTokenRegistered = true
            ),
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = false,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = true
            ),
            PushNotificationStatus(
                isUserNotificationsEnabled = false,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = true
            )
        )

        assertThat(conditionStateMonitor.currentState).isEqualTo(Condition.State(isOptedIn = true))
        assertThat(conditionStateMonitor.currentState).isEqualTo(Condition.State(isOptedIn = true))
        assertThat(conditionStateMonitor.currentState).isEqualTo(Condition.State(isOptedIn = false))
        assertThat(conditionStateMonitor.currentState).isEqualTo(Condition.State(isOptedIn = false))
        assertThat(conditionStateMonitor.currentState).isEqualTo(Condition.State(isOptedIn = false))
    }

    @Test
    public fun testUpdates(): TestResult = runTest {
        conditionStateMonitor.states.test {
            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = true))

            stateFlow.tryEmit(PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = false,
                isPushTokenRegistered = true
            ))

            assertThat(awaitItem()).isEqualTo(Condition.State(isOptedIn = false))
        }
    }
}
