/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.LayoutStateStorage
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.ThomasModelFactory
import com.urbanairship.android.layout.info.StateControllerInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DerivedStateFlow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ThomasStateScopesTest {

    @Test
    public fun `factory builds one state flow per controller scope`(): TestResult = runTest {
        val environment = ModelEnvironment(
            layoutState = LayoutState.EMPTY,
            reporter = mockk(relaxUnitFun = true),
            actionsRunner = mockk(relaxUnitFun = true),
            displayTimer = mockk(relaxed = true),
            modelScope = backgroundScope
        )
        val info = ViewInfo.viewInfoFromJson(
            JsonValue.parseString(ROOT_WITH_NESTED_STATE_CONTROLLER).requireMap()
        )

        ThomasModelFactory().create(info, environment)

        val scopes = environment.stateScopes.scopes
        assertEquals(2, scopes.size)
        assertNotSame(scopes[0], scopes[1])
        assertEquals(StateControllerInfo.IDENTIFIER, scopes[0].value.layout?.identifier)
        assertEquals("inner_state", scopes[1].value.layout?.identifier)
    }

    @Test
    public fun `rebuilding async content replaces its scopes instead of adding to them`(): TestResult = runTest {
        val environment = ModelEnvironment(
            layoutState = LayoutState.EMPTY,
            reporter = mockk(relaxUnitFun = true),
            actionsRunner = mockk(relaxUnitFun = true),
            displayTimer = mockk(relaxed = true),
            modelScope = backgroundScope
        )
        val factory = ThomasModelFactory()
        factory.create(
            ViewInfo.viewInfoFromJson(JsonValue.parseString(ROOT_WITH_NESTED_STATE_CONTROLLER).requireMap()),
            environment
        )
        val staticScopes = environment.stateScopes.scopes
        val content = ViewInfo.viewInfoFromJson(JsonValue.parseString(ASYNC_CONTENT).requireMap())

        val firstBuild = environment.stateScopes.owner()
        factory.create(content, environment, LayoutState.EMPTY, firstBuild)
        val firstContentScope = environment.stateScopes.scopes.last()
        val secondBuild = environment.stateScopes.owner()
        factory.create(content, environment, LayoutState.EMPTY, secondBuild)
        firstBuild.release()

        val scopes = environment.stateScopes.scopes
        assertEquals(staticScopes + scopes.last(), scopes)
        assertFalse(firstContentScope in environment.stateScopes)
        assertEquals("async_state", scopes.last().value.layout?.identifier)
    }

    @Test
    public fun `releasing an owner stops its scopes and its children's from reporting`(): TestResult =
        runTest(UnconfinedTestDispatcher()) {
            val owned = SharedState(State.Layout(identifier = "owned"))
            val nested = SharedState(State.Layout(identifier = "nested"))
            val static = SharedState(State.Layout(identifier = "static"))
            val scopes = ThomasStateScopes()
            val owner = scopes.owner()
            scopes.add(makeThomasState(null, owned, null, null, null), owner)
            scopes.add(makeThomasState(null, nested, null, null, null), owner.child())
            scopes.add(makeThomasState(null, static, null, null, null))
            val reported = mutableListOf<JsonValue>()
            backgroundScope.launch { scopes.changes().collect { reported.add(it) } }

            owner.release()
            owned.update { it.copyWithState(jsonMapOf("from" to "owned")) }
            nested.update { it.copyWithState(jsonMapOf("from" to "nested")) }
            static.update { it.copyWithState(jsonMapOf("from" to "static")) }

            assertEquals(1, scopes.scopes.size)
            assertEquals(listOf(jsonMapOf("from" to "static").toJsonValue()), reported)
        }

    @Test
    public fun `a scope reports until every owner that uses it is released`(): TestResult = runTest {
        val scopes = ThomasStateScopes()
        val shared = makeThomasState(null, SharedState(State.Layout(identifier = "shared")), null, null, null)
        val first = scopes.owner()
        val second = scopes.owner()
        scopes.add(shared, first)
        scopes.add(shared, second)

        first.release()
        assertTrue(shared in scopes)

        second.release()
        assertFalse(shared in scopes)
    }

    @Test
    public fun `releasing either owner first leaves a shared scope with the other`(): TestResult = runTest {
        val scopes = ThomasStateScopes()
        val shared = makeThomasState(null, SharedState(State.Layout(identifier = "shared")), null, null, null)
        val displayed = scopes.owner()
        val failedRebuild = scopes.owner()
        scopes.add(shared, displayed)
        scopes.add(shared, failedRebuild)

        failedRebuild.release()

        assertTrue(shared in scopes)
    }

    @Test
    public fun `async content using a static scope never releases it`(): TestResult = runTest {
        val scopes = ThomasStateScopes()
        val static = makeThomasState(null, SharedState(State.Layout(identifier = "static")), null, null, null)
        scopes.add(static)
        val build = scopes.owner()
        scopes.add(static, build)

        build.release()

        assertTrue(static in scopes)
    }

    @Test
    public fun `a rebuild with stored states reuses the scope and keeps it reporting`(): TestResult = runTest {
        val storage = LayoutStateStorage(
            storage = mockk(relaxed = true) { every { getSavedState() } returns null },
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val environment = ModelEnvironment(
            layoutState = LayoutState.EMPTY,
            reporter = mockk(relaxUnitFun = true),
            actionsRunner = mockk(relaxUnitFun = true),
            displayTimer = mockk(relaxed = true),
            modelScope = backgroundScope,
            stateStorage = storage
        )
        val factory = ThomasModelFactory()
        val content = ViewInfo.viewInfoFromJson(JsonValue.parseString(ASYNC_CONTENT).requireMap())

        val firstBuild = environment.stateScopes.owner()
        factory.create(content, environment, LayoutState.EMPTY, firstBuild)
        val scope = environment.stateScopes.scopes.single()
        val secondBuild = environment.stateScopes.owner()
        factory.create(content, environment, LayoutState.EMPTY, secondBuild)

        assertEquals(listOf(scope), environment.stateScopes.scopes)
        firstBuild.release()
        assertTrue(scope in environment.stateScopes)
        secondBuild.release()
        assertFalse(scope in environment.stateScopes)
        storage.clear()
    }

    @Test
    public fun `content built under a released owner never reports`(): TestResult = runTest {
        val scopes = ThomasStateScopes()
        val replaced = scopes.owner().apply { release() }
        val lateNested = replaced.child()

        scopes.add(makeThomasState(null, SharedState(State.Layout(identifier = "late")), null, null, null), lateNested)

        assertTrue(lateNested.isReleased)
        assertEquals(emptyList<StateFlow<ThomasState>>(), scopes.scopes)
    }

    @Test
    public fun `changes reports each scope once per serialized change`(): TestResult =
        runTest(UnconfinedTestDispatcher()) {
            val first = SharedState(State.Layout(identifier = "first"))
            val second = SharedState(State.Layout(identifier = "second"))
            val scopes = ThomasStateScopes().apply {
                add(makeThomasState(null, first, null, null, null))
                add(makeThomasState(null, second, null, null, null))
            }
            val reported = mutableListOf<JsonValue>()
            backgroundScope.launch { scopes.changes().collect { reported.add(it) } }

            assertEquals(emptyList<JsonValue>(), reported)

            first.update { it.copyWithState(jsonMapOf("answer" to "yes")) }
            // New mutation ids, so the state isn't equal, but it serializes the same.
            first.update { it.copyWithState(jsonMapOf("answer" to "yes")) }
            second.update { it.copyWithState(jsonMapOf("answer" to "yes")) }

            val third = SharedState(
                State.Layout(identifier = "third").copyWithState(jsonMapOf("seen" to true))
            )
            scopes.add(makeThomasState(null, third, null, null, null))

            assertEquals(
                listOf(
                    jsonMapOf("answer" to "yes").toJsonValue(),
                    jsonMapOf("answer" to "yes").toJsonValue(),
                    jsonMapOf("seen" to true).toJsonValue()
                ),
                reported
            )
        }

    @Test
    public fun `reportTo collects nothing when the listener doesn't want state changes`(): TestResult =
        runTest(UnconfinedTestDispatcher()) {
            var collected = false
            val scopes = ThomasStateScopes().apply { add(spyScope { collected = true }) }
            val listener = mockk<ThomasListenerInterface>(relaxed = true) {
                every { wantsStateChanges } returns false
            }

            val job = scopes.reportTo(listener, backgroundScope)

            assertNull(job)
            assertFalse(collected)
            verify(exactly = 0) { listener.onStateChanged(any()) }
        }

    @Test
    public fun `reportTo reports to a listener that wants state changes`(): TestResult =
        runTest(UnconfinedTestDispatcher()) {
            var collected = false
            val scopes = ThomasStateScopes().apply { add(spyScope { collected = true }) }
            val reported = mutableListOf<JsonSerializable>()
            val listener = mockk<ThomasListenerInterface>(relaxed = true) {
                every { wantsStateChanges } returns true
                every { onStateChanged(any()) } answers { reported.add(firstArg()) }
            }

            val job = scopes.reportTo(listener, backgroundScope)

            assertNotNull(job)
            assertTrue(collected)
            assertEquals(listOf(jsonMapOf("x" to 1).toJsonValue()), reported.map { it.toJsonValue() })
        }

    /** A scope with a non-empty state that calls [onCollect] when anything collects it. */
    private fun spyScope(onCollect: () -> Unit): DerivedStateFlow<ThomasState> {
        val state = ThomasState(
            layout = State.Layout(identifier = "spy").copyWithState(jsonMapOf("x" to 1)),
            form = null,
            pager = null,
            video = null,
            asyncView = null,
            capabilities = ThomasCapabilities.NONE
        )
        return DerivedStateFlow(
            onValue = { state },
            updates = flow {
                onCollect()
                emit(state)
                awaitCancellation()
            }
        )
    }

    private companion object {
        private const val TEXT_APPEARANCE =
            """"text_appearance": { "color": { "default": { "hex": "#000000", "alpha": 1 } } }"""

        private val LABEL = """
            {
                "size": { "width": "100%", "height": "auto" },
                "view": { "type": "label", "text": "Label", $TEXT_APPEARANCE }
            }
        """.trimIndent()

        private val ROOT_WITH_NESTED_STATE_CONTROLLER = """
            {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                    $LABEL,
                    $LABEL,
                    {
                        "size": { "width": "100%", "height": "auto" },
                        "view": {
                            "type": "state_controller",
                            "identifier": "inner_state",
                            "view": {
                                "type": "linear_layout",
                                "direction": "vertical",
                                "items": [ $LABEL, $LABEL ]
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        private val ASYNC_CONTENT = """
            {
                "type": "state_controller",
                "identifier": "async_state",
                "view": {
                    "type": "linear_layout",
                    "direction": "vertical",
                    "items": [ $LABEL ]
                }
            }
        """.trimIndent()
    }
}
