/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.json.JsonException
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CompressionExtensionsTest {

    @Test
    public fun zlibInflate_roundTripJson(): Unit {
        val original = """{"version":1,"display":{"type":"modal"}}"""
        val compressed = zlibCompress(original.toByteArray(Charsets.UTF_8))
        assertThat(compressed.zlibInflate().toString(Charsets.UTF_8)).isEqualTo(original)
    }

    @Test
    public fun zlibInflate_empty_throws(): Unit {
        assertThrows(JsonException::class.java) {
            ByteArray(0).zlibInflate()
        }
    }

    @Test
    public fun zlibInflate_invalidData_throws(): Unit {
        assertThrows(JsonException::class.java) {
            byteArrayOf(0x00, 0x01, 0x02, 0x03).zlibInflate()
        }
    }

    private fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val buffer = ByteArray(1024)
            ByteArrayOutputStream().use { out ->
                while (!deflater.finished()) {
                    val n = deflater.deflate(buffer)
                    if (n > 0) {
                        out.write(buffer, 0, n)
                    }
                }
                out.toByteArray()
            }
        } finally {
            deflater.end()
        }
    }
}
