/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestRequestSession
import com.urbanairship.android.layout.ModelFactory
import com.urbanairship.android.layout.ModelFactoryException
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.LayoutEventHandler
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.AsyncViewControllerInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.util.ThomasViewIdResolver
import com.urbanairship.json.JsonValue
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class, DelicateLayoutApi::class)
@RunWith(AndroidJUnit4::class)
public class AsyncLayoutModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val session = TestRequestSession()

    private val mockLayoutState: LayoutState = mockk(relaxed = true)
    private val layoutEventHandler = LayoutEventHandler(testScope)
    private val mockEnv = ModelEnvironment(
        layoutState = mockLayoutState,
        reporter = mockk(relaxUnitFun = true),
        actionsRunner = mockk(relaxUnitFun = true),
        displayTimer = mockk { every { time } returns System.currentTimeMillis() },
        modelScope = testScope,
        eventHandler = layoutEventHandler,
        viewIdResolver = ThomasViewIdResolver()
    )

    private val factory: ModelFactory = mockk()
    private val placeholderModel: AnyModel = mockk(relaxed = true) {
        every { createView(any(), any(), any()) } answers {
            FrameLayout(invocation.args[0] as Context)
        }
    }
    private val loadedModel: AnyModel = mockk(relaxed = true) {
        every { createView(any(), any(), any()) } answers {
            FrameLayout(invocation.args[0] as Context)
        }
    }

    private lateinit var asyncState: SharedState<State.AsyncView>
    private var createCallIndex: Int = 0

    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun parseAsyncInfo(json: String): AsyncViewControllerInfo =
        ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap()) as AsyncViewControllerInfo

    private fun newModel(info: AsyncViewControllerInfo): AsyncLayoutModel {
        asyncState = SharedState(State.AsyncView.Idle(identifier = info.identifier))
        createCallIndex = 0
        every { factory.create(any(), any(), any()) } answers {
            when (createCallIndex++) {
                0 -> placeholderModel
                else -> loadedModel
            }
        }
        return AsyncLayoutModel(
            viewInfo = info,
            asyncState = asyncState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null),
            factory = factory,
            requestSession = session,
            contactIdFetcher = { "contact-token" },
            channelIdFetcher = { "channel-token" }
        )
    }

    @Test
    public fun successfulFetch_emitsPlaceholderThenLoadedContent_andUpdatesAsyncState(): TestResult = runTest(testDispatcher) {
        val info = parseAsyncInfo(asyncControllerJson())
        session.addResponse(200, body = """{"type":"empty_view"}""")
        val model = newModel(info)
        val context = RuntimeEnvironment.getApplication()

        model.state.test {
            model.createView(context, viewEnvironment, itemProperties = null)
            advanceUntilIdle()

            assertEquals(placeholderModel, awaitItem().model)
            val loaded = awaitItem()
            assertEquals(loadedModel, loaded.model)
            assertEquals(viewEnvironment, loaded.viewEnvironment)

            ensureAllEventsConsumed()
        }

        val loadedState = asyncState.value as State.AsyncView.Loaded
        assertEquals(info.identifier, loadedState.identifier)
        assertTrue(loadedState.data.isNull)

        assertEquals("GET", session.lastRequest.method)
        assertEquals("https://example.com/layout.json", session.lastRequest.url.toString())
    }

    @Test
    public fun httpClientError_setsAsyncError_andDoesNotRetry(): TestResult = runTest(testDispatcher) {
        val info = parseAsyncInfo(asyncControllerJson())
        session.addResponse(404, body = """{"type":"empty_view"}""")
        val model = newModel(info)
        val context = RuntimeEnvironment.getApplication()

        model.state.test {
            model.createView(context, viewEnvironment, null)
            advanceUntilIdle()
            assertEquals(placeholderModel, awaitItem().model)
            ensureAllEventsConsumed()
        }

        val err = asyncState.value as State.AsyncView.Error
        assertEquals(State.AsyncView.Error.ErrorData.Server(404), err.data)
        assertEquals(1, session.requests.size)
    }

    @Test
    public fun malformedJsonBody_setsClientError(): TestResult = runTest(testDispatcher) {
        val info = parseAsyncInfo(asyncControllerJson())
        session.addResponse(200, body = "not json {")
        val model = newModel(info)
        val context = RuntimeEnvironment.getApplication()

        model.state.test {
            model.createView(context, viewEnvironment, null)
            advanceUntilIdle()
            awaitItem()
            ensureAllEventsConsumed()
        }

        assertEquals(
            State.AsyncView.Error.ErrorData.Client,
            (asyncState.value as State.AsyncView.Error).data
        )
    }

    @Test
    public fun factoryFailureWhileInflatingResponse_asyncStateEndsLoadedAfterReportLayoutLoaded(): TestResult = runTest(testDispatcher) {
        val info = parseAsyncInfo(asyncControllerJson())
        session.addResponse(200, body = """{"type":"empty_view"}""")
        createCallIndex = 0
        every { factory.create(any(), any(), any()) } answers {
            if (createCallIndex++ == 0) {
                placeholderModel
            } else {
                throw ModelFactoryException("inflate failed")
            }
        }

        val failureAsyncState = SharedState<State.AsyncView>(State.AsyncView.Idle(info.identifier))
        val model = AsyncLayoutModel(
            viewInfo = info,
            asyncState = failureAsyncState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null),
            factory = factory,
            requestSession = session,
            contactIdFetcher = { "contact-id" },
            channelIdFetcher = { "channel-id" }
        )
        val context = RuntimeEnvironment.getApplication()

        model.state.test {
            model.createView(context, viewEnvironment, null)
            advanceUntilIdle()
            assertEquals(placeholderModel, awaitItem().model)
            ensureAllEventsConsumed()
        }

        assertEquals(
            State.AsyncView.Error.ErrorData.Client,
            (failureAsyncState.value as State.AsyncView.Error).data
        )
        verify(exactly = 2) { factory.create(any(), any(), any()) }
    }

    @Test
    public fun channelAuthWithoutChannelId_skipsRequest_andSetsClientError(): TestResult = runTest(testDispatcher) {
        val json = """
            {
                "type": "async_view_controller",
                "identifier": "async-1",
                "placeholder": { "type": "empty_view" },
                "request": {
                    "type": "content",
                    "auth": "channel",
                    "url": "https://example.com/layout.json"
                },
                "retry": { "max_retries": 0 }
            }
        """.trimIndent()
        val info = parseAsyncInfo(json)
        every { factory.create(any(), any(), any()) } returns placeholderModel
        val channelAsyncState = SharedState<State.AsyncView>(State.AsyncView.Idle(info.identifier))
        val model = AsyncLayoutModel(
            viewInfo = info,
            asyncState = channelAsyncState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null),
            factory = factory,
            requestSession = session,
            channelIdFetcher = { null }
        )
        val context = RuntimeEnvironment.getApplication()

        model.state.test {
            model.createView(context, viewEnvironment, null)
            advanceUntilIdle()
            awaitItem()
            ensureAllEventsConsumed()
        }

        assertEquals(0, session.requests.size)
        assertEquals(
            State.AsyncView.Error.ErrorData.Client,
            (channelAsyncState.value as State.AsyncView.Error).data
        )
    }

    @Test
    public fun serverErrorThenSuccess_retriesAndLoads(): TestResult = runTest(testDispatcher) {
        val info = parseAsyncInfo(asyncControllerJson(maxRetries = 1))
        session.addResponse(500, body = null)
        session.addResponse(200, body = """{"type":"empty_view"}""")
        val model = newModel(info)
        val context = RuntimeEnvironment.getApplication()

        model.state.test {
            model.createView(context, viewEnvironment, null)
            advanceUntilIdle()
            skipItems(2)
            ensureAllEventsConsumed()
        }

        assertEquals(2, session.requests.size)
        assertTrue(asyncState.value is State.AsyncView.Loaded)
    }

    @Test
    public fun asyncViewReload_triggersSecondFetch(): TestResult = runTest(testDispatcher) {
        val info = parseAsyncInfo(asyncControllerJson())
        session.addResponse(200, body = """{"type":"empty_view"}""")
        val model = newModel(info)
        val context = RuntimeEnvironment.getApplication()

        model.createView(context, viewEnvironment, null)
        advanceUntilIdle()
        assertEquals(1, session.requests.size)

        session.addResponse(200, body = """{"type":"empty_view"}""")
        layoutEventHandler.broadcast(LayoutEvent.AsyncViewReload(info.identifier))
        advanceUntilIdle()

        assertEquals(2, session.requests.size)
    }

    private fun asyncControllerJson(
        url: String = "https://example.com/layout.json",
        maxRetries: Int = 0
    ): String = """
        {
            "type": "async_view_controller",
            "identifier": "async-1",
            "placeholder": { "type": "empty_view" },
            "request": {
                "type": "content",
                "url": "$url"
            },
            "retry": {
                "max_retries": $maxRetries,
                "initial_backoff_seconds": 1,
                "max_backoff_seconds": 10
            }
        }
    """.trimIndent()
}
