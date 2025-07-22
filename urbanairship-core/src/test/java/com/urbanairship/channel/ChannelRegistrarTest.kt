package com.urbanairship.channel

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestClock
import com.urbanairship.http.RequestResult
import java.util.UUID
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ChannelRegistrarTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceDataStore = PreferenceDataStore.inMemoryStore(context)

    private val testDispatcher = StandardTestDispatcher()
    private val mockClient = mockk<ChannelApiClient> {
        every { this@mockk.createLocation(any()) } returns Uri.parse("some://location")
    }

    private val testClock = TestClock()
    private val testActivityMonitor = TestActivityMonitor()
    private val mockPrivacyManager = mockk<PrivacyManager>(relaxed = true)

    private val emptyPayload = ChannelRegistrationPayload.Builder().build()

    private var createOption: ChannelGenerationMethod = ChannelGenerationMethod.Automatic

    private val registrar = ChannelRegistrar(
        preferenceDataStore, mockClient, testActivityMonitor,
        channelCreateOption = object : AirshipChannelCreateOption {
            override fun get(): ChannelGenerationMethod {
                return createOption
            }
        },
        clock = testClock,
        mockPrivacyManager
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
        registrar.payloadBuilder = { buildCraPayload() }
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testCreateChannel(): TestResult = runTest {
        assertNull(registrar.channelId)
        coEvery {
            mockClient.createChannel(emptyPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)

        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        assertEquals("some id", registrar.channelId)
        registrar.channelIdFlow.test {
            assertEquals("some id", this.awaitItem())
        }
    }

    @Test
    public fun testRestoreChannel(): TestResult = runTest {
        assertNull(registrar.channelId)

        val restoreChannelId = UUID.randomUUID().toString()
        createOption = ChannelGenerationMethod.Restore(restoreChannelId)

        // Update
        coEvery {
            mockClient.updateChannel(any(), any())
        } answers {
            assertEquals(restoreChannelId, firstArg())
            RequestResult(200, Channel("some id", "some://location"), null, null)
        }

        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        assertEquals(restoreChannelId, registrar.channelId)
        registrar.channelIdFlow.test {
            assertEquals(restoreChannelId, this.awaitItem())
        }

        coVerify(exactly = 0) { mockClient.createChannel(any()) }
        coVerify(exactly = 1) { mockClient.updateChannel(any(), any()) }
    }

    @Test
    public fun testRestoreFallbackToRegularOnInvalidChannelId(): TestResult = runTest {
        assertNull(registrar.channelId)

        createOption = ChannelGenerationMethod.Restore("invalid")

        coEvery {
            mockClient.createChannel(emptyPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)

        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        assertEquals("some id", registrar.channelId)
        registrar.channelIdFlow.test {
            assertEquals("some id", this.awaitItem())
        }
    }

    @Test
    public fun testUpdateChannel(): TestResult = runTest {
        // Create channel first
        coEvery {
            mockClient.createChannel(emptyPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        // Modify the payload so it will update
        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setContactId("some contact id")
            .build()

        registrar.payloadBuilder = { expectedPayload }

        // Update
        coEvery {
            mockClient.updateChannel("some id", expectedPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        coVerify(exactly = 1) { mockClient.updateChannel(any(), any()) }
        coVerify(exactly = 1) { mockClient.createChannel(any()) }
    }

    @Test
    public fun testRegistrationPayloadOutOfDate(): TestResult = runTest {
        // Make the payload unique every time
        registrar.payloadBuilder =
            { ChannelRegistrationPayload.Builder().setContactId(UUID.randomUUID().toString())
                .build() }

        // Create
        coEvery {
            mockClient.createChannel(any())
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.NEEDS_UPDATE, registrar.updateRegistration())

        // update
        coEvery {
            mockClient.updateChannel("some id", any())
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.NEEDS_UPDATE, registrar.updateRegistration())
    }

    @Test
    public fun testRegistrationLocationOutOfDate(): TestResult = runTest {
        // Make the location unique every time
        every { mockClient.createLocation(any()) } returns Uri.parse("some://other/${UUID.randomUUID()}")

        // Create
        coEvery {
            mockClient.createChannel(any())
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.NEEDS_UPDATE, registrar.updateRegistration())

        // update
        coEvery {
            mockClient.updateChannel("some id", any())
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.NEEDS_UPDATE, registrar.updateRegistration())
    }

    @Test
    public fun testRecreateChannelOnConflict(): TestResult = runTest {
        // Create channel first
        coEvery {
            mockClient.createChannel(any())
        } returnsMany listOf(
            RequestResult(200, Channel("some id", "some://location"), null, null),
            RequestResult(200, Channel("some other id", "some://location"), null, null)
        )

        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())
        assertEquals("some id", registrar.channelId)

        // Modify the payload so it will update
        val payload = ChannelRegistrationPayload.Builder()
            .setContactId("some contact id")
            .build()

        registrar.payloadBuilder = { payload }

        // Update
        coEvery {
            mockClient.updateChannel("some id", any())
        } returns RequestResult(409, null, null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        registrar.channelIdFlow.test {
            assertEquals("some other id", this.awaitItem())
        }
        assertEquals("some other id", registrar.channelId)
    }

    @Test
    public fun testUpdateChannelAlreadyUpdated(): TestResult = runTest {
        // Create channel first
        coEvery {
            mockClient.createChannel(emptyPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        coVerify(exactly = 0) { mockClient.updateChannel(any(), any()) }
        coVerify(exactly = 1) { mockClient.createChannel(any()) }
    }

    public fun testUpdateChannelAfter24Hrs(): TestResult = runTest {
        // Create channel first
        coEvery {
            mockClient.createChannel(emptyPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        testActivityMonitor.foreground()
        testClock.currentTimeMillis += 24 * 60 * 60 * 1000

        // Update
        coEvery {
            mockClient.updateChannel("some id", emptyPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        coVerify(exactly = 1) { mockClient.updateChannel(any(), any()) }
        coVerify(exactly = 1) { mockClient.createChannel(any()) }
    }

    @Test
    public fun testFullPayloadUploadAfter24Hours(): TestResult = runTest {
        // Create channel first
        val payload = ChannelRegistrationPayload.Builder()
            .setContactId(UUID.randomUUID().toString())
            .setAppVersion("test")
            .build()

        registrar.payloadBuilder = { payload }

        testClock.currentTimeMillis = 1

        coEvery {
            mockClient.createChannel(any())
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        val capturedPayload = slot<ChannelRegistrationPayload>()
        // Update
        coEvery {
            mockClient.updateChannel("some id", capture(capturedPayload))
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        assertFalse(capturedPayload.isCaptured)

        testActivityMonitor.foreground()
        testClock.currentTimeMillis += 24 * 60 * 60 * 1000 + 1

        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())
        assertEquals(payload, capturedPayload.captured)
    }

    public fun testUpdateMinimizedPayload(): TestResult = runTest {
        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setTimezone("neat time zone")
            .build()

        // Create channel first
        coEvery {
            mockClient.createChannel(expectedPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        testActivityMonitor.foreground()
        testClock.currentTimeMillis += 24 * 60 * 60 * 1000

        val minimizedPayload = expectedPayload.minimizedPayload(expectedPayload)
        assertNotEquals(minimizedPayload, expectedPayload)

        // Update
        coEvery {
            mockClient.updateChannel("some id", minimizedPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        coVerify(exactly = 1) { mockClient.updateChannel(any(), any()) }
        coVerify(exactly = 1) { mockClient.createChannel(any()) }
    }

    @Test
    public fun testUpdateLocationChangeUsesFullPayload(): TestResult = runTest {
        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setTimezone("neat time zone")
            .build()

        registrar.payloadBuilder = { expectedPayload }

        // Create channel first
        coEvery {
            mockClient.createChannel(expectedPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        every { mockClient.createLocation(any()) } returns Uri.parse("some://other/location")

        // Update
        coEvery {
            mockClient.updateChannel("some id", expectedPayload)
        } returns RequestResult(200, Channel("some id", "some://other/location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        coVerify(exactly = 1) { mockClient.updateChannel(any(), any()) }
        coVerify(exactly = 1) { mockClient.createChannel(any()) }
    }

    private suspend fun buildCraPayload(): ChannelRegistrationPayload {
        var builder = ChannelRegistrationPayload.Builder()
        return builder.build()
    }
}
