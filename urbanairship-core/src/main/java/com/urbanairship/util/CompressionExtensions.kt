/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Decompresses zlib-wrapped deflate data (RFC 1950), matching typical `inflateInit2` with window bits 15.
 *
 * This is not raw DEFLATE (`Inflater(true)`) and not gzip.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Throws(JsonException::class)
public fun ByteArray.zlibInflate(): ByteArray {
    if (isEmpty()) {
        throw JsonException("Empty compressed payload")
    }
    val inflater = Inflater()
    return try {
        inflater.setInput(this)
        val buffer = ByteArray(16 * 1024)
        val out = ByteArrayOutputStream()
        while (!inflater.finished()) {
            val count = try {
                inflater.inflate(buffer)
            } catch (e: DataFormatException) {
                throw JsonException("Invalid zlib compressed data", e)
            }
            if (count > 0) {
                out.write(buffer, 0, count)
            }
        }
        out.toByteArray()
    } finally {
        inflater.end()
    }
}
