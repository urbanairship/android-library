package com.urbanairship.channel

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestClock
import com.urbanairship.http.RequestResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    private val emptyPayload = ChannelRegistrationPayload.Builder().build()

    private val registrar = ChannelRegistrar(
        preferenceDataStore, mockClient, testActivityMonitor, testClock
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
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
    public fun testUpdateChannel(): TestResult = runTest {
        // Create channel first
        coEvery {
            mockClient.createChannel(emptyPayload)
        } returns RequestResult(200, Channel("some id", "some://location"), null, null)
        assertEquals(RegistrationResult.SUCCESS, registrar.updateRegistration())

        // Modify the payload so it will update
        registrar.addChannelRegistrationPayloadExtender {
            it.setContactId("some contact id")
        }

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setContactId("some contact id")
            .build()

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
        registrar.addChannelRegistrationPayloadExtender {
            it.setContactId(UUID.randomUUID().toString())
        }

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
        registrar.addChannelRegistrationPayloadExtender {
            it.setContactId("some contact id")
        }

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

    public fun testUpdateMinimizedPayload(): TestResult = runTest {
        registrar.addChannelRegistrationPayloadExtender {
            it.setCarrier("some thing").setTimezone("neat time zone")
        }

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setCarrier("some thing")
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
        registrar.addChannelRegistrationPayloadExtender {
            it.setCarrier("some thing").setTimezone("neat time zone")
        }

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setCarrier("some thing")
            .setTimezone("neat time zone")
            .build()

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
}
