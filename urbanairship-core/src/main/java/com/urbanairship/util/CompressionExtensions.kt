/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Decompresses **raw** DEFLATE bytes (no zlib wrapper, no gzip), matching
 * `Deflater(Deflater.BEST_COMPRESSION, true)` with [java.util.zip.DeflaterOutputStream] on the backend.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Throws(JsonException::class)
public fun ByteArray.rawDeflateInflate(): ByteArray {
    if (isEmpty()) {
        throw JsonException("Empty compressed payload")
    }
    val inflater = Inflater(true)
    return try {
        inflater.setInput(this)
        val buffer = ByteArray(16 * 1024)
        val out = ByteArrayOutputStream()
        while (!inflater.finished()) {
            val count = try {
                inflater.inflate(buffer)
            } catch (e: DataFormatException) {
                throw JsonException("Invalid raw deflate compressed data", e)
            }
            if (count > 0) {
                out.write(buffer, 0, count)
            } else if (!inflater.finished()) {
                // Corrupt or truncated stream: inflate() can return 0 forever without finishing.
                if (inflater.needsInput()) {
                    throw JsonException("Invalid raw deflate compressed data: truncated payload")
                }
                throw JsonException("Invalid raw deflate compressed data")
            }
        }
        out.toByteArray()
    } finally {
        inflater.end()
    }
}
