package com.urbanairship.http

import android.net.Uri
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.json.JsonValue
import com.urbanairship.util.ConnectionUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.zip.GZIPOutputStream

internal class DefaultHttpClient : HttpClient {
    override fun <T> execute(
        url: Uri,
        method: String,
        headers: Map<String, String>,
        body: RequestBody?,
        followRedirects: Boolean,
        parser: ResponseParser<T>
    ): Response<T> {
        val actualUrl: URL = try {
            URL(url.toString())
        } catch (e: MalformedURLException) {
            throw RequestException("Failed to build URL", e)
        }

        var conn: HttpURLConnection? = null

        return try {
            conn = ConnectionUtils.openSecureConnection(
                UAirship.applicationContext, actualUrl
            ) as HttpURLConnection

            conn.apply {
                requestMethod = method
                doInput = true
                useCaches = false
                connectTimeout = 60000
                readTimeout = 60000
                allowUserInteraction = false
                instanceFollowRedirects = followRedirects
            }

            headers.forEach { entry ->
                conn.setRequestProperty(entry.key, entry.value)
            }

            body?.let { requestBody ->
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", requestBody.contentType)

                if (requestBody.compress) {
                    conn.setRequestProperty("Content-Encoding", "gzip")
                }

                conn.outputStream.use { out -> out.write(requestBody.content, gzip = requestBody.compress) }
            }

            val responseBody: String? = try {
                conn.inputStream.readFully()
            } catch (ex: IOException) {
                conn.errorStream?.readFully() ?: run {
                    UALog.e("Error stream was null for response code: ${conn.responseCode}")
                    null
                }
            }

            val responseHeaders = mapHeaders(conn.headerFields)
            val parsedResult = parser.parseResponse(
                conn.responseCode,
                responseHeaders,
                responseBody
            )

            Response(
                conn.responseCode,
                parsedResult,
                responseBody,
                responseHeaders,
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun mapHeaders(headers: Map<String, List<String>>): Map<String, String> {
        return headers.mapValues { (_, value) ->
            if (value.isEmpty()) {
                ""
            } else if (value.size > 1) {
                JsonValue.wrapOpt(value).toString()
            } else {
                value.first()
            }
        }
    }
}

private fun InputStream.readFully(): String {
    return bufferedReader().useLines { lines ->
        lines.fold(StringBuilder()) { builder, line ->
            builder.append(line).append('\n')
        }.toString()
    }
}

private fun OutputStream.write(content: String, gzip: Boolean = false) {
    if (gzip) {
        GZIPOutputStream(this).use { gos ->
            gos.writer().use {
                it.write(content)
            }
        }
    } else {
        writer().use { it.write(content) }
    }
}
