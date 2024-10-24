/* Copyright Airship and Contributors */
package com.urbanairship.permission

import android.app.Activity
import android.content.Context
import androidx.core.util.Consumer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class PermissionsManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val systemSettingsLauncher: SystemSettingsLauncher = mockk()

    private val activityMonitor = TestActivityMonitor()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val permissionsManager = PermissionsManager(context, activityMonitor, systemSettingsLauncher, testDispatcher)

    private var mockDelegateStatus = PermissionRequestResult.notDetermined()
    private val mockDelegate: PermissionDelegate = mockk {
        every { checkPermissionStatus(any(), any()) } answers {
            secondArg<Consumer<PermissionStatus>>().accept(mockDelegateStatus.permissionStatus)
        }

        every { requestPermission(any(), any()) } answers {
            secondArg<Consumer<PermissionRequestResult>>().accept(mockDelegateStatus)
        }
    }

    private val mockStatusListener: OnPermissionStatusChangedListener = mockk(relaxed = true)

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testCheckPermissionNoDelegate(): TestResult = runTest {
        assertEquals(
            PermissionStatus.NOT_DETERMINED,
            permissionsManager.suspendingCheckPermissionStatus(Permission.LOCATION)
        )
    }

    @Test
    public fun testRequestPermissionNoDelegate(): TestResult = runTest {
        assertEquals(
            PermissionRequestResult.notDetermined(),
            permissionsManager.suspendingRequestPermission(Permission.LOCATION)
        )
    }

    @Test
    public fun testConfiguredPermissions(): TestResult = runTest {
        val expected: MutableSet<Permission> = HashSet()
        assertEquals(expected, permissionsManager.configuredPermissions)

        expected.add(Permission.LOCATION)
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)
        assertEquals(expected, permissionsManager.configuredPermissions)

        expected.add(Permission.DISPLAY_NOTIFICATIONS)
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)
        assertEquals(expected, permissionsManager.configuredPermissions)
    }

    @Test
    public fun testRequestPermission(): TestResult = runTest {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)
        mockDelegateStatus = PermissionRequestResult.granted()

        assertEquals(
            mockDelegateStatus,
            permissionsManager.suspendingRequestPermission(Permission.LOCATION)
        )
    }

    @Test
    public fun testOnEnableAirship(): TestResult = runTest {
        val enabler = mockk<Consumer<Permission>>(relaxed = true)

        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)
        mockDelegateStatus = PermissionRequestResult.granted()

        permissionsManager.addAirshipEnabler(enabler)
        permissionsManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, true)

        verify {
            enabler.accept(Permission.DISPLAY_NOTIFICATIONS)
        }
    }

    @Test
    public fun testOnEnableAirshipDenied(): TestResult = runTest {
        val enabler = mockk<Consumer<Permission>>(relaxed = true)

        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)
        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.addAirshipEnabler(enabler)
        permissionsManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, true)

        verify(exactly = 0) {
            enabler.accept(Permission.DISPLAY_NOTIFICATIONS)
        }
    }

    @Test
    public fun testOnEnableAirshipNotDetermined(): TestResult = runTest {
        val enabler = mockk<Consumer<Permission>>(relaxed = true)

        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)
        mockDelegateStatus = PermissionRequestResult.notDetermined()
        permissionsManager.addAirshipEnabler(enabler)
        permissionsManager.suspendingRequestPermission(Permission.LOCATION, true)

        verify(exactly = 0) {
            enabler.accept(Permission.DISPLAY_NOTIFICATIONS)
        }
    }

    @Test
    public fun testStatusChangeCheckOnRequest(): TestResult = runTest {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)
        permissionsManager.suspendingRequestPermission(Permission.LOCATION)

        permissionsManager.addOnPermissionStatusChangedListener(mockStatusListener)

        mockDelegateStatus = PermissionRequestResult.granted()
        permissionsManager.suspendingRequestPermission(Permission.LOCATION)

        verify {
            mockStatusListener.onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.GRANTED)
        }

        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.suspendingCheckPermissionStatus(Permission.LOCATION)

        verify {
            mockStatusListener.onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.DENIED)
        }
    }

    @Test
    public fun testStatusChangeCheckOnCheck(): TestResult = runTest {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)
        permissionsManager.suspendingRequestPermission(Permission.LOCATION)

        permissionsManager.addOnPermissionStatusChangedListener(mockStatusListener)

        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.suspendingRequestPermission(Permission.LOCATION)

        verify {
            mockStatusListener.onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.DENIED)
        }

        mockDelegateStatus = PermissionRequestResult.granted()
        permissionsManager.suspendingCheckPermissionStatus(Permission.LOCATION)

        verify {
            mockStatusListener.onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.GRANTED)
        }
    }

    @Test
    public fun testStatusChangeCheckOnActivityResume(): TestResult = runTest {
        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)
        permissionsManager.suspendingCheckPermissionStatus(Permission.LOCATION)

        permissionsManager.addOnPermissionStatusChangedListener(mockStatusListener)
        mockDelegateStatus = PermissionRequestResult.notDetermined()
        activityMonitor.resumeActivity(Activity())

        advanceUntilIdle()

        verify {
            mockStatusListener.onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.NOT_DETERMINED)
        }
    }

    @Test
    public fun testDedupeRequests(): TestResult = runTest {
        val resultFlow = MutableStateFlow<PermissionRequestResult?>(null)
        val delegate: PermissionDelegate = mockk {
            every { requestPermission(any(), any()) } answers {
                launch {
                    secondArg<Consumer<PermissionRequestResult?>>().accept(resultFlow.first { it != null })
                }
            }
            every { checkPermissionStatus(any(), any()) } answers {
                launch {
                    secondArg<Consumer<PermissionStatus>>().accept(
                        resultFlow.first { it != null }?.permissionStatus ?: PermissionStatus.NOT_DETERMINED
                    )
                }

            }
        }

        permissionsManager.setPermissionDelegate(Permission.LOCATION, delegate)

        val requestJobs = (0..6).map {
            async {
                return@async permissionsManager.suspendingRequestPermission(Permission.LOCATION)
            }
        }

        val checkStatusJobs = (0..10).map {
            async {
                return@async permissionsManager.suspendingCheckPermissionStatus(Permission.LOCATION)
            }
        }

        resultFlow.value = PermissionRequestResult.granted()

        assertEquals((0..6).map { PermissionRequestResult.granted() }, requestJobs.map { it.await() })
        assertEquals((0..10).map { PermissionStatus.GRANTED }, checkStatusJobs.map { it.await() })

        verify(exactly = 1) {
            delegate.requestPermission(any(), any())
            delegate.checkPermissionStatus(any(), any())
        }
    }

    @Test
    public fun testFallbackSystemSettings(): TestResult = runTest {
        val settingsLaunched = MutableStateFlow(false)

        every {
            systemSettingsLauncher.openAppSettings(any())
        } answers {
            settingsLaunched.value = true
            true
        }

        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)

        val job = async {
            permissionsManager.suspendingRequestPermission(Permission.LOCATION, fallback = PermissionPromptFallback.SystemSettings)
        }
        advanceUntilIdle()

        settingsLaunched.first { it }
        mockDelegateStatus = PermissionRequestResult.granted()
        activityMonitor.resumeActivity(Activity())
        advanceUntilIdle()

        assertEquals(
            PermissionRequestResult.granted(),
            job.await()
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
            systemSettingsLauncher.openAppSettings(context)
            mockDelegate.checkPermissionStatus(context, any())
        }
    }

    @Test
    public fun testFallbackSystemSettingsFails(): TestResult = runTest {
        val settingsLaunched = MutableStateFlow(false)

        every {
            systemSettingsLauncher.openAppSettings(any())
        } answers {
            settingsLaunched.value = true
            false
        }

        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate)

        val job = async(Dispatchers.Default) {
            permissionsManager.suspendingRequestPermission(Permission.LOCATION, fallback = PermissionPromptFallback.SystemSettings)
        }
        advanceUntilIdle()

        settingsLaunched.first { it }
        mockDelegateStatus = PermissionRequestResult.granted()

        MainScope().launch {
            activityMonitor.resumeActivity(Activity())
        }
        advanceUntilIdle()

        assertEquals(
            PermissionRequestResult.denied(true),
            job.await()
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
            systemSettingsLauncher.openAppSettings(context)
        }
    }

    @Test
    public fun testFallbackSystemSettingsNotifications(): TestResult = runTest {
        val settingsLaunched = MutableStateFlow(false)

        every {
            systemSettingsLauncher.openAppNotificationSettings(any())
        } answers {
            settingsLaunched.value = true
            true
        }

        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)

        val job = async {
            permissionsManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, fallback = PermissionPromptFallback.SystemSettings)
        }
        advanceUntilIdle()

        settingsLaunched.first { it }

        mockDelegateStatus = PermissionRequestResult.granted()

        MainScope().launch {
            activityMonitor.resumeActivity(Activity())
        }
        advanceUntilIdle()

        assertEquals(
            PermissionRequestResult.granted(),
            job.await()
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
            systemSettingsLauncher.openAppNotificationSettings(context)
            mockDelegate.checkPermissionStatus(context, any())
        }
    }

    @Test
    public fun testFallbackNone(): TestResult = runTest {
        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)

        val result = permissionsManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, fallback = PermissionPromptFallback.None)

        assertEquals(
            PermissionRequestResult.denied(true),
            result
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
        }
    }

    @Test
    public fun testFallbackCallback(): TestResult = runTest {
        val callbackCalled = MutableStateFlow(false)

        mockDelegateStatus = PermissionRequestResult.denied(true)
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)

        val job = async(Dispatchers.Default) {
            permissionsManager.suspendingRequestPermission(
                Permission.DISPLAY_NOTIFICATIONS,
                fallback = PermissionPromptFallback.Callback {
                    mockDelegateStatus = PermissionRequestResult.granted()
                    callbackCalled.value = true
                }
            )
        }

        callbackCalled.first { it }

        assertEquals(
            PermissionRequestResult.granted(),
            job.await()
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
            mockDelegate.checkPermissionStatus(context, any())
        }
    }

    @Test
    public fun testFallbackIgnoredNotDetermined(): TestResult = runTest {
        mockDelegateStatus = PermissionRequestResult.notDetermined()
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)

        val result = permissionsManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, fallback = PermissionPromptFallback.SystemSettings)

        assertEquals(
            PermissionRequestResult.notDetermined(),
            result
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
        }
    }

    @Test
    public fun testFallbackIgnoredGranted(): TestResult = runTest {
        mockDelegateStatus = PermissionRequestResult.granted()
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)

        val result = permissionsManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, fallback = PermissionPromptFallback.SystemSettings)

        assertEquals(
            PermissionRequestResult.granted(),
            result
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
        }
    }

    @Test
    public fun testFallbackIgnoredDeniedFromPrompt(): TestResult = runTest {
        mockDelegateStatus = PermissionRequestResult.denied(false)
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)

        val result = permissionsManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, fallback = PermissionPromptFallback.SystemSettings)

        assertEquals(
            PermissionRequestResult.denied(false),
            result
        )

        verifyOrder {
            mockDelegate.requestPermission(any(), any())
        }
    }

    @Test
    public fun testPermissionUpdates(): TestResult = runTest {
        mockDelegateStatus = PermissionRequestResult.denied(false)
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate)
        permissionsManager.suspendingCheckPermissionStatus(Permission.DISPLAY_NOTIFICATIONS)

        permissionsManager.permissionsUpdate(Permission.DISPLAY_NOTIFICATIONS).test {
            assertEquals(PermissionStatus.DENIED, awaitItem())

            mockDelegateStatus = PermissionRequestResult.granted()
            permissionsManager.suspendingCheckPermissionStatus(Permission.DISPLAY_NOTIFICATIONS)
            assertEquals(PermissionStatus.GRANTED, awaitItem())

            mockDelegateStatus = PermissionRequestResult.notDetermined()
            permissionsManager.suspendingCheckPermissionStatus(Permission.DISPLAY_NOTIFICATIONS)
            assertEquals(PermissionStatus.NOT_DETERMINED, awaitItem())
        }
    }
}
