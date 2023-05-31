package com.urbanairship.liveupdate

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.liveupdate.util.jsonMapOf
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.TestCase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// TODO: This is probably not all that useful outside of development...
//   Consider removing it once things feel solid, or if this is flaky in CI.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class LiveUpdateStressTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    @OptIn(DelicateCoroutinesApi::class)
    private val mainDispatcher = newSingleThreadContext("UI")

    @OptIn(DelicateCoroutinesApi::class)
    private val dbDispatcher = newFixedThreadPoolContext(16, "DB")

    @OptIn(DelicateCoroutinesApi::class)
    private val ioDispatcher = newFixedThreadPoolContext(16, "I/O")

    @OptIn(DelicateCoroutinesApi::class)
    private val registrarDispatcher = newFixedThreadPoolContext(16, "C/B")

    private val processorDispatcher = AirshipDispatchers.newSingleThreadDispatcher()

    private lateinit var database: LiveUpdateDatabase
    private lateinit var dao: LiveUpdateDao
    private lateinit var processor: LiveUpdateProcessor
    private lateinit var registrar: LiveUpdateRegistrar
    private val notificationManager: NotificationManagerCompat = mockk(relaxed = true)
    private val channel: AirshipChannel = mockk(relaxed = true)

    @Before
    public fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        database = LiveUpdateDatabase.createInMemoryDatabase(context, dbDispatcher)
        dao = spyk(database.liveUpdateDao())
        processor = spyk(LiveUpdateProcessor(dao, processorDispatcher))

        registrar = LiveUpdateRegistrar(
            context = context,
            channel = channel,
            dao = dao,
            dispatcher = registrarDispatcher,
            processor = processor,
            notificationManager = notificationManager
        )
    }

    @After
    public fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    public fun stressTestLiveUpdates(): TestResult = runBlocking {
        val handler1 = TestHandler()
        val handler2 = TestHandler()

        registrar.register("type-1", handler1)
        registrar.register("type-2", handler2)

        val updateCount = 25

        val job1 = asyncLiveUpdates(id = 1, repeat = updateCount)
        val job2 = asyncLiveUpdates(id = 2, repeat = updateCount)

        // Log processor status for debugging.
        val job3 = async(context = ioDispatcher) {
            while (processor.isProcessing.not()) {
                delay(300)
                println("waiting...")
            }
            while (processor.isProcessing) {
                delay(300)
                println("processing...")
            }

            delay(1000)
        }

        awaitAll(job1, job2, job3)

        val expected = updateCount + 2 // updates + start + stop
        TestCase.assertEquals(expected, handler1.events.size)
        TestCase.assertEquals(expected, handler2.events.size)
    }

    private suspend fun asyncLiveUpdates(id: Int, repeat: Int, sleep: Long? = 5) = coroutineScope {
        async {
            val name = "test-$id"
            val type = "type-$id"

            sleep?.let { delay(it) }

            start(name, type, 0)

            repeat(repeat) { i ->
                sleep?.let { delay(it) }
                update(name, i + 1)
            }

            sleep?.let { delay(it) }
            stop(name, repeat + 2)
        }
    }

    private fun start(name: String, type: String, n: Int): Unit =
        registrar.start(
            name = name,
            type = type,
            content = jsonMapOf("n" to n),
            timestamp = System.currentTimeMillis(),
            dismissalTimestamp = null
        )

    private fun stop(name: String, n: Int): Unit =
        registrar.stop(
            name = name,
            content = jsonMapOf("n" to n),
            timestamp = System.currentTimeMillis(),
            dismissalTimestamp = null
        )

    private fun update(name: String, n: Int): Unit =
        registrar.update(
            name = name,
            content = jsonMapOf("n" to n),
            timestamp = System.currentTimeMillis(),
            dismissalTimestamp = null
        )
}
