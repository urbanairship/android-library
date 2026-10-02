/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.ThomasModelFactory
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.LinearLayoutModel
import com.urbanairship.android.layout.model.PagerController
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.android.layout.util.UI_TEST_RANDOM_SEED
import com.urbanairship.android.layout.util.randomShuffleSeed
import com.urbanairship.images.ImageLoader
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** The vectors are web-thomas's, so every platform orders a scene the same way. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class LinearLayoutOrderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { imageCache() } returns null
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(Airship)
        every { Airship.imageLoader } returns mockk<ImageLoader>(relaxed = true)
    }

    @After
    public fun tearDown() {
        unmockkObject(Airship)
        Dispatchers.resetMain()
    }

    @Test
    public fun testSortsByOrderAndKeepsListOrderAmongEqualOnes() {
        val stack = stack(
            items = listOf(
                item("a", """"config": {"order": 1},"""),
                item("b"),
                item("c", """"config": {"order": -1},"""),
                item("d", """"config": {"order": 1},"""),
                item("e")
            )
        )
        assertEquals(listOf("c", "b", "e", "a", "d"), texts(stack))
    }

    @Test
    public fun testSortsAFractionalOrderBetweenWholeOnes() {
        val stack = stack(
            items = listOf(
                item("a", """"config": {"order": 1},"""),
                item("b", """"config": {"order": 0.5},"""),
                item("c")
            )
        )
        assertEquals(listOf("c", "b", "a"), texts(stack))
    }

    @Test
    public fun testKeepsListOrderWithoutOrders() {
        assertEquals(LETTERS, texts(stack(items = LETTERS.map { item(it) })))
    }

    @Test
    public fun testShufflesWithinAnOrderAndKeepsAPinnedItemLast() {
        // ui-test:options:6 shuffles to e d None c a b; sorting by order then moves None to the end.
        val stack = stack(
            items = LETTERS.map { item(it) } + item("None", """"config": {"order": 1},"""),
            extra = """"identifier": "options", "randomize_children": true,"""
        )
        assertEquals(listOf("e", "d", "c", "a", "b", "None"), texts(stack))
    }

    @Test
    public fun testShufflesByTheSeedAndItemCountWithoutAnIdentifier() {
        // ui-test:5 → 1, 3, 2, 0, 4
        val stack = stack(items = LETTERS.map { item(it) }, extra = """"randomize_children": true,""")
        assertEquals(listOf("b", "d", "c", "a", "e"), texts(stack))
    }

    @Test
    public fun testOrdersLinearLayoutsApartByTheirIdentifiers() {
        val root = host(
            """
            {"type": "linear_layout", "direction": "vertical",
             "items": [${nested(shuffled("p1", "first"))}, ${nested(shuffled("p2", "second"))}]}
            """.trimIndent()
        )
        val (first, second) = root.descendants().filterIsInstance<LinearLayoutView>().drop(1).toList()
        // ui-test:first:5 → 4, 1, 0, 2, 3 and ui-test:second:5 → 1, 0, 3, 2, 4
        assertEquals(listOf("p1-e", "p1-b", "p1-a", "p1-c", "p1-d"), texts(first))
        assertEquals(listOf("p2-b", "p2-a", "p2-d", "p2-c", "p2-e"), texts(second))
    }

    @Test
    public fun testGivesUnidentifiedLayoutsOfOneItemCountOneOrderOnAnyPage() {
        val json = """
            {"type": "pager_controller", "identifier": "pager",
             "view": {"type": "pager", "items": [
               {"identifier": "first", "view": ${shuffled("p1")}},
               {"identifier": "second", "view": ${shuffled("p2")}}
             ]}}
        """.trimIndent()
        val controller = ThomasModelFactory().create(
            ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap()), environment()
        ) as PagerController
        testDispatcher.scheduler.advanceUntilIdle()

        // ui-test:5 → 1, 3, 2, 0, 4
        val pages = (controller.view as PagerModel).pages.map { labels(it as LinearLayoutModel) }
        assertEquals(listOf("p1-b", "p1-d", "p1-c", "p1-a", "p1-e"), pages[0])
        assertEquals(listOf("p2-b", "p2-d", "p2-c", "p2-a", "p2-e"), pages[1])
    }

    @Test
    public fun testGivesADifferentSeedADifferentOrder() {
        // seed-b:5 → 4, 1, 2, 0, 3
        val model = build(shuffled("x"), environment(seed = "seed-b"))
        assertEquals(listOf("x-e", "x-b", "x-c", "x-a", "x-d"), labels(model))
    }

    @Test
    public fun testKeepsAnUnseededOrderWhenBuiltAgain() {
        val environment = environment(seed = null)
        val first = labels(build(shuffled("x"), environment))
        assertEquals(LETTERS.map { "x-$it" }, first.sorted())
        assertEquals(first, labels(build(shuffled("x"), environment)))
    }

    @Test
    public fun testAppliesAnOverrideThatAlreadyMatchesBeforeItIsAttached() {
        val always = """"overrides": {"config": [{"value": {"order": -1}}]},"""
        val json = """
            {"type": "linear_layout", "direction": "vertical",
             "items": [${item("a")}, ${item("b")}, ${item("c", always)}]}
        """.trimIndent()
        val view = LayoutViewModel().getOrCreateModel(ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap()), environment())
            .createView(ApplicationProvider.getApplicationContext(), viewEnv, null) as ViewGroup
        assertEquals(listOf("c", "a", "b"), texts(view))
    }

    @Test
    public fun testAButtonDescribesAShuffledLayoutInTheOrderItIsShown() {
        // ui-test:options:6 shuffles to e d None c a b; sorting by order then moves None to the end.
        val button = """
            {"type": "button_layout", "identifier": "button",
             "view": {"type": "linear_layout", "direction": "vertical", "identifier": "options", "randomize_children": true,
                      "items": [${(LETTERS.map { item(it) } + item("None", """"config": {"order": 1},""")).joinToString()}]}}
        """.trimIndent()
        val model = ThomasModelFactory().create(
            ViewInfo.viewInfoFromJson(JsonValue.parseString(button).requireMap()), environment()
        )
        assertEquals("e, d, c, a, b, None", model.contentDescription(ApplicationProvider.getApplicationContext()))
    }

    @Test
    public fun testFollowsAnOverrideOnState() {
        val flip = """
            "overrides": {"config": [{
              "when_state_matches": {"scope": ["flipped"], "value": {"equals": true}},
              "value": {"order": -1}
            }]},
        """.trimIndent()
        val button = """
            {"size": {"width": "100%", "height": 40},
             "view": {"type": "label_button", "identifier": "flip",
                      "label": {"type": "label", "text": "flip",
                                "text_appearance": {"font_size": 14, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}},
                      "event_handlers": [{"type": "tap", "state_actions": [{"type": "set", "key": "flipped", "value": true}]}]}}
        """.trimIndent()
        val inner = """
            {"size": {"width": "100%", "height": "auto"},
             "view": {"type": "linear_layout", "direction": "vertical",
                      "items": [${item("a")}, ${item("b")}, ${item("c", flip)}]}}
        """.trimIndent()
        val root = host(
            """
            {"type": "state_controller",
             "view": {"type": "linear_layout", "direction": "vertical", "items": [$button, $inner]}}
            """.trimIndent()
        )
        val stack = root.descendants().filterIsInstance<LinearLayoutView>().last()
        assertEquals(listOf("a", "b", "c"), texts(stack))

        root.descendants().filterIsInstance<LabelButtonView>().single().performClick()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("c", "a", "b"), texts(stack))
    }

    private fun stack(items: List<String>, extra: String = ""): ViewGroup = host(
        """
        {"type": "linear_layout", "direction": "vertical", $extra
         "items": [${items.joinToString()}]}
        """.trimIndent()
    ) as ViewGroup

    private fun host(json: String): View {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val view = LayoutViewModel().getOrCreateModel(info, environment())
            .createView(ApplicationProvider.getApplicationContext(), viewEnv, null)
        Robolectric.buildActivity(Activity::class.java).setup().get()
            .setContentView(FrameLayout(view.context).apply { addView(view) })
        testDispatcher.scheduler.advanceUntilIdle()
        return view
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
        }
    }

    /** The labels' text in stacking order. */
    private fun texts(stack: ViewGroup): List<String> =
        (0 until stack.childCount).map { (stack.getChildAt(it) as TextView).text.toString() }

    private fun environment(seed: String? = UI_TEST_RANDOM_SEED) = ModelEnvironment(
        layoutState = LayoutState.EMPTY,
        reporter = mockk(relaxUnitFun = true),
        actionsRunner = mockk(relaxUnitFun = true),
        displayTimer = mockk(relaxed = true),
        modelScope = testScope,
        shuffleSeed = seed ?: randomShuffleSeed()
    )

    private fun build(json: String, environment: ModelEnvironment): LinearLayoutModel =
        ThomasModelFactory().create(
            ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap()), environment
        ) as LinearLayoutModel

    /** The labels' text in a linear layout model's order. */
    private fun labels(model: LinearLayoutModel): List<String> =
        model.items.map { (it.model.viewInfo as LabelInfo).text }

    /** Five shuffled labels, `<prefix>-a` to `<prefix>-e`. */
    private fun shuffled(prefix: String, identifier: String? = null) = """
        {"type": "linear_layout", "direction": "vertical", "randomize_children": true,
         ${identifier?.let { """"identifier": "$it",""" } ?: ""}
         "items": [${LETTERS.joinToString { item("$prefix-$it") }}]}
    """.trimIndent()

    private fun nested(view: String) = """{"size": {"width": "100%", "height": "auto"}, "view": $view}"""

    private fun item(text: String, fields: String = "") = """
        {"size": {"width": "100%", "height": "auto"}, $fields
         "view": {"type": "label", "text": "$text",
                  "text_appearance": {"font_size": 14, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}}}
    """.trimIndent()

    private companion object {
        val LETTERS = listOf("a", "b", "c", "d", "e")
    }
}
