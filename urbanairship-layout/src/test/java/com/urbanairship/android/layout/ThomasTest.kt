package com.urbanairship.android.layout

import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(RobolectricTestRunner::class)
public class ThomasTest {

    private var modelProvider = ModelProvider()

    @Test
    @Throws(JsonException::class)
    public fun parsesSampleModal() {
        val layout = LayoutInfo(json = readJsonMapResource("modal.json"))
        Assert.assertNotNull(layout)

        val presentation = layout.presentation as ModalPresentation
        Assert.assertNotNull(presentation)

        val position = presentation.defaultPlacement.position
        Assert.assertNotNull(position)
        assertEquals(HorizontalPosition.CENTER, position!!.horizontal)
        assertEquals(VerticalPosition.CENTER, position.vertical)

        val view = modelProvider.create(layout.view) as ContainerLayoutModel
        Assert.assertNotNull(view)
        assertTrue(view.items.isNotEmpty())

        for ((info) in view.items) {
            Assert.assertNotSame(info.info.type, ViewType.UNKNOWN)
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

        val invalidMin =
            LayoutInfo(Thomas.MIN_SUPPORTED_VERSION - 1, valid.presentation, valid.view)
        assertFalse(Thomas.isValid(invalidMin))

        val invalidMax =
            LayoutInfo(Thomas.MAX_SUPPORTED_VERSION + 1, valid.presentation, valid.view)
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
            val json = BufferedReader(InputStreamReader(classLoader.getResourceAsStream(path)))
                .use { br ->
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
