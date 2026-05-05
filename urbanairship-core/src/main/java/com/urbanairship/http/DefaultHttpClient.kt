package com.urbanairship.http

import android.net.Uri
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.json.JsonValue
import com.urbanairship.util.ConnectionUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DefaultHttpClient : HttpClient {
    override suspend fun <T> execute(
        url: Uri,
        method: String,
        headers: Map<String, String>,
        body: RequestBody?,
        followRedirects: Boolean,
        parser: ResponseParser<T>
    ): Response<T> = suspendCancellableCoroutine { continuation ->
        val actualUrl: URL = try {
            URL(url.toString())
        } catch (e: MalformedURLException) {
            continuation.resumeWithException(RequestException("Failed to build URL", e))
            return@suspendCancellableCoroutine
        }

        val connection = openConnection(actualUrl) as HttpURLConnection
        continuation.invokeOnCancellation { connection.disconnect() }

        try {
            connection.apply {
                requestMethod = method
                doInput = true
                useCaches = false
                connectTimeout = 60000
                readTimeout = 60000
                allowUserInteraction = false
                instanceFollowRedirects = followRedirects
            }

            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            body?.let { requestBody ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", requestBody.contentType)

                if (requestBody.compress) {
                    connection.setRequestProperty("Content-Encoding", "gzip")
                }

                connection.outputStream.use { out -> out.write(requestBody.content, gzip = requestBody.compress) }
            }

            val responseBody: String? = try {
                connection.inputStream.readFully()
            } catch (ex: IOException) {
                connection.errorStream?.readFully() ?: run {
                    UALog.e("Error stream was null for response code: ${connection.responseCode}")
                    null
                }
            }

            val responseHeaders = mapHeaders(connection.headerFields)
            val parsedResult = parser.parseResponse(connection.responseCode, responseHeaders, responseBody)

            if (continuation.isActive) {
                continuation.resume(Response(connection.responseCode, parsedResult, responseBody, responseHeaders))
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: URL): URLConnection {
        return if (Airship.isFlyingOrTakingOff) {
            ConnectionUtils.openSecureConnection(
                context = Airship.application, url = url
            )
        } else {
            url.openConnection()
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
