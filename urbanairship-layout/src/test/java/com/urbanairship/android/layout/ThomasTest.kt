package com.urbanairship.android.layout

import com.urbanairship.android.layout.environment.ActionsRunner
import com.urbanairship.android.layout.environment.AttributeHandler
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ThomasTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockReporter: Reporter = mockk(relaxUnitFun = true)
    private val mockActionsRunner: ActionsRunner = mockk()
    private val mockAttributeHandler: AttributeHandler = mockk()
    private val mockDisplayTimer: DisplayTimer = mockk {
        every { time } returns System.currentTimeMillis()
    }
    private val mockEnv: ModelEnvironment = mockk {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { attributeHandler } returns mockAttributeHandler
        every { displayTimer } returns mockDisplayTimer
        every { layoutState } returns LayoutState.EMPTY
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope

        every { withState(any()) } returns this
    }

    private val viewModel: LayoutViewModel = LayoutViewModel()

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @Throws(JsonException::class)
    public fun parsesSampleModal() {
        val layout = LayoutInfo(json = readJsonMapResource("modal.json"))
        assertNotNull(layout)

        val presentation = layout.presentation as ModalPresentation
        assertNotNull(presentation)

        val position = presentation.defaultPlacement.position
        assertNotNull(position)
        assertEquals(HorizontalPosition.CENTER, position!!.horizontal)
        assertEquals(VerticalPosition.CENTER, position.vertical)

        val view = viewModel.getOrCreateModel(layout.view, mockEnv) as ContainerLayoutModel
        assertNotNull(view)
        assertTrue(view.items.isNotEmpty())

        for ((info) in view.items) {
            assertNotSame(info.info.type, ViewType.UNKNOWN)
        }
    }

    @Test
    @Throws(JsonException::class)
    public fun validateVersion() {
        val valid = LayoutInfo(json = readJsonMapResource("modal.json"))
        assertTrue(Thomas.isValid(valid))
        assertEquals(1, valid.version.toLong())

        for (i in Thomas.MIN_SUPPORTED_VERSION until Thomas.MAX_SUPPORTED_VERSION) {
            val layout = LayoutInfo(i, valid.presentation, valid.view)
            assertTrue(Thomas.isValid(layout))
        }

        val invalidMin = LayoutInfo(Thomas.MIN_SUPPORTED_VERSION - 1, valid.presentation, valid.view)
        assertFalse(Thomas.isValid(invalidMin))

        val invalidMax = LayoutInfo(Thomas.MAX_SUPPORTED_VERSION + 1, valid.presentation, valid.view)
        assertFalse(Thomas.isValid(invalidMax))
    }

    @Test
    @Throws(JsonException::class)
    public fun validatePresentation() {
        val banner = LayoutInfo(json = readJsonMapResource("banner.json"))
        assertTrue(Thomas.isValid(banner))

        val modal = LayoutInfo(json = readJsonMapResource("modal.json"))
        assertTrue(Thomas.isValid(modal))
    }

    private fun readJsonMapResource(path: String): JsonMap {
        val classLoader = javaClass.classLoader!!
        try {
            val json = classLoader.getResourceAsStream(path).reader().buffered().use { br ->
                val builder = StringBuilder()
                var line = br.readLine()
                while (line != null) {
                    builder.append(line)
                    line = br.readLine()
                }
                builder.toString()
            }
            return JsonValue.parseString(json).optMap()
        } catch (e: IOException) {
            e.printStackTrace()
            Assert.fail("Failed to read json from file: $path")
        } catch (e: JsonException) {
            Assert.fail("Unable to parse json from file: $path")
        }
        return JsonMap.EMPTY_MAP
    }
}
