/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CompressionExtensionsTest {

    @Test
    public fun rawDeflateInflate_roundTrip_matchesBackendEncoder(): Unit {
        val original = """{"version":1,"layout":{"presentation":{"type":"modal"}}}"""
        val compressed = encodeRawDeflateLikeBackend(original.toByteArray(Charsets.UTF_8))
        assertThat(compressed.rawDeflateInflate().toString(Charsets.UTF_8)).isEqualTo(original)
    }

    @Test
    public fun rawDeflateInflate_parsesBackendString() {
        val content = "jZNdT4MwFIbv9ytMjXdj0I1h4FbjjSaaZZnekRYOUMfapi37cNl/t/twwQ3Q3rXvw2mfQ862d3Ne" +
                "aAlKM8FRhPv1Y6lAAzfEHLJtLdqnZiMBRYgSzkGh/kWaQkaq0sSyJAksbJWrAntKsy9oCvbZiqWmsBeE3t" +
                "1l9RNRAMsLWxj5FrkidtcfISk0O9ogKowRi4bKiOVcKIg1ySAmCgiKjKqgAaRCpVa95f2KpKzStqfD5tdr" +
                "o8Qc4pMm7oQSUYq2i2rdbieO7Vpb79vssJpbeiJJKQurjVuQXe9fh00/gJJknitR8bTT6W+hHxvP82mHTa" +
                "dKw5N7rdtfNmjJYNU6EkRveBIfkMuxqFRpgcIYqSPXLQjPiXLSclApO0eEKV0wOUjEwqWME7VxZUVLlriz" +
                "98dV+vr0OZk+v0zWD/Axnb0FuRtQLxh6QJ0wxdjxcRg4IeDMwf44HFPvHo/IqD4YZ59d7xs="

        val decoded = content
            .base64Decoded()
            ?.rawDeflateInflate()
            ?.let { String(it, StandardCharsets.UTF_8) }
            ?.let(JsonValue::parseString)
            ?: throw AssertionError("Failed to decode base64 string")

        assertTrue(decoded != JsonValue.NULL)
    }

    @Test
    public fun rawDeflateInflate_empty_throws(): Unit {
        assertThrows(JsonException::class.java) {
            ByteArray(0).rawDeflateInflate()
        }
    }

    @Test
    public fun rawDeflateInflate_invalidData_throws(): Unit {
        assertThrows(JsonException::class.java) {
            byteArrayOf(0x78.toByte(), 0x9c.toByte()).rawDeflateInflate()
        }
    }

    /** Mirrors backend `encodeRawDeflateBase64` compression step (raw deflate, nowrap). */
    private fun encodeRawDeflateLikeBackend(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val baos = ByteArrayOutputStream()
        DeflaterOutputStream(baos, deflater).use { it.write(input) }
        return baos.toByteArray()
    }
}
