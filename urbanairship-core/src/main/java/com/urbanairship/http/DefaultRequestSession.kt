package com.urbanairship.http

import android.util.Base64
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.UAirship
import com.urbanairship.json.JsonValue
import com.urbanairship.util.ConnectionUtils
import com.urbanairship.util.PlatformUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.zip.GZIPOutputStream

/**
 * Parses a response.
 *
 * @param <T> The result type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DefaultRequestSession(
    private val configOptions: AirshipConfigOptions,
    platform: Int,
) : RequestSession {

    public override var channelAuthTokenProvider: AuthTokenProvider? = null
    public override var contactAuthTokenProvider: AuthTokenProvider? = null

    private val defaultHeaders: Map<String, String> = mapOf(
        "X-UA-App-Key" to configOptions.appKey,
        "User-Agent" to "(UrbanAirshipLib-${PlatformUtils.asString(platform)}/${UAirship.getVersion()}; $configOptions.appKey)"
    )

    @Throws(RequestException::class)
    public override fun execute(request: Request): Response<Unit> {
        return execute(request) { _, _, _ -> }
    }

    /**
     * Executes the request.
     *
     * @return The request response.
     */
    @Throws(RequestException::class)
    public override fun <T> execute(
        request: Request,
        parser: ResponseParser<T>
    ): Response<T> {
        val result = doExecute(request, parser)
        return if (result.shouldRetry) {
            doExecute(request, parser).response
        } else {
            result.response
        }
    }

    @Throws(RequestException::class)
    private fun <T> doExecute(
        request: Request,
        parser: ResponseParser<T>
    ): RequestResult<T> {
        val url: URL = try {
            URL(requireNotNull(request.url).toString())
        } catch (e: MalformedURLException) {
            throw RequestException("Failed to build URL", e)
        }

        val auth = request.auth?.let {
            resolveAuth(it)
        }

        var conn: HttpURLConnection? = null
        val response = try {
            conn = ConnectionUtils.openSecureConnection(
                UAirship.getApplicationContext(), url
            ) as HttpURLConnection

            conn.requestMethod = request.method
            conn.connectTimeout = NETWORK_TIMEOUT_MS
            conn.doInput = true
            conn.useCaches = false
            conn.allowUserInteraction = false
            conn.instanceFollowRedirects = request.followRedirects

            defaultHeaders.forEach { entry ->
                conn.setRequestProperty(entry.key, entry.value)
            }

            request.headers.forEach { entry ->
                conn.setRequestProperty(entry.key, entry.value)
            }

            auth?.let {
                conn.setRequestProperty("Authorization", auth)
            }

            request.body?.let { body ->
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", body.contentType)

                if (body.compress) {
                    conn.setRequestProperty("Content-Encoding", "gzip")
                }

                conn.outputStream.use { out -> out.write(body.content, gzip = body.compress) }
            }

            val responseBody: String? = try {
                conn.inputStream.readFully()
            } catch (ex: IOException) {
                conn.errorStream.readFully()
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
        } catch (e: Exception) {
            throw RequestException("Request failed URL: $url method: $request", e)
        } finally {
            conn?.disconnect()
        }

        return if (response.status == 401 && auth != null && request.auth.isAuthTokenAuth) {
            expireAuth(request.auth, auth)
            RequestResult(true, response)
        } else {
            RequestResult(false, response)
        }
    }

    private fun resolveAuth(auth: RequestAuth): String {
        return when (auth) {
            is RequestAuth.BasicAppAuth -> {
                val credentials = configOptions.appKey + ":" + configOptions.appSecret
                "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"
            }

            is RequestAuth.BasicAuth -> {
                val credentials = auth.user + ":" + auth.password
                "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"
            }

            is RequestAuth.BearerToken -> {
                "Bearer ${auth.token}"
            }

            is RequestAuth.ChannelTokenAuth -> {
                val token = requireNotNull(channelAuthTokenProvider).fetchToken(auth.channelId)
                "Bearer $token"
            }

            is RequestAuth.ContactTokenAuth -> {
                val token = requireNotNull(channelAuthTokenProvider).fetchToken(auth.contactId)
                "Bearer $token"
            }
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

    private fun expireAuth(auth: RequestAuth, token: String) {
        when (auth) {
            is RequestAuth.ChannelTokenAuth -> channelAuthTokenProvider?.expireToken(token)
            is RequestAuth.ContactTokenAuth -> channelAuthTokenProvider?.expireToken(token)
            else -> {}
        }
    }

    private companion object {
        private const val NETWORK_TIMEOUT_MS = 60000
    }

    private data class RequestResult<T>(val shouldRetry: Boolean, val response: Response<T>)
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
