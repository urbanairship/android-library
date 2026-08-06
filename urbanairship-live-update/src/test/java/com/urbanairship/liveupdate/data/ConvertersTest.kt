package com.urbanairship.liveupdate.data

import com.urbanairship.BaseTestCase
import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.util.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test

public class ConvertersTest : BaseTestCase() {
    private val converters = Converters()

    @Test
    public fun testToJsonMapValidJson() {
        val map = jsonMapOf("key" to "value", "num" to 42)
        val result = converters.toJsonMap("""{"key":"value","num":42}""")
        assertEquals(map, result)
    }

    @Test
    public fun testToJsonMapCorruptDataReturnsEmpty() {
        val result = converters.toJsonMap("this is not valid json {{{{")
        assertEquals(JsonMap.EMPTY_MAP, result)
    }

    @Test
    public fun testToJsonMapNullStringReturnsEmpty() {
        val result = converters.toJsonMap("null")
        assertEquals(JsonMap.EMPTY_MAP, result)
    }

    @Test
    public fun testFromJsonMapRoundTrip() {
        val map = jsonMapOf("foo" to "bar")
        val json = converters.fromJsonMap(map)
        val result = converters.toJsonMap(json)
        assertEquals(map, result)
    }
}
