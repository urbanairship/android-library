package com.urbanairship.http

import android.util.Base64
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.UAirship
import com.urbanairship.util.PlatformUtils

/**
 * Parses a response.
 *
 * @param <T> The result type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DefaultRequestSession : RequestSession {

    private val configOptions: AirshipConfigOptions
    private val httpClient: HttpClient
    private val platform: Int

    public constructor(configOptions: AirshipConfigOptions, platform: Int) : this(
        configOptions, platform, DefaultHttpClient()
    )

    internal constructor(
        configOptions: AirshipConfigOptions,
        platform: Int,
        httpClient: HttpClient
    ) {
        this.configOptions = configOptions
        this.platform = platform
        this.httpClient = httpClient

        this.defaultHeaders = mapOf(
            "X-UA-App-Key" to configOptions.appKey,
            "User-Agent" to "(UrbanAirshipLib-${PlatformUtils.asString(platform)}/${UAirship.getVersion()}; $configOptions.appKey)"
        )
    }

    public override var channelAuthTokenProvider: AuthTokenProvider? = null
    public override var contactAuthTokenProvider: AuthTokenProvider? = null

    private val defaultHeaders: Map<String, String>

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
        if (request.url == null) {
            throw RequestException("Missing URL")
        }

        val headers = mutableMapOf<String, String>()
        headers += defaultHeaders
        headers += request.headers

        try {
            val auth = request.auth?.let {
                resolveAuth(it)
            }

            auth?.let {
                headers["Authorization"] = "${auth.prefix} ${auth.token}"
            }

            val response = httpClient.execute(
                request.url, request.method, headers, request.body, request.followRedirects, parser
            )

            return if (response.status == 401 && auth != null && auth.isAuthToken) {
                expireAuth(request.auth, auth.token)
                RequestResult(true, response)
            } else {
                RequestResult(false, response)
            }
        } catch (e: Exception) {
            throw RequestException("Request failed: $request", e)
        }
    }

    private fun resolveAuth(auth: RequestAuth): ResolvedAuth {
        return when (auth) {
            is RequestAuth.BasicAppAuth -> {
                val credentials = configOptions.appKey + ":" + configOptions.appSecret
                ResolvedAuth(
                    prefix = "Basic", token = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                )
            }

            is RequestAuth.BasicAuth -> {
                val credentials = auth.user + ":" + auth.password
                ResolvedAuth(
                    prefix = "Basic", token = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                )
            }

            is RequestAuth.BearerToken -> {
                ResolvedAuth(
                    prefix = "Bearer", token = auth.token
                )
            }

            is RequestAuth.ChannelTokenAuth -> {
                val token = requireNotNull(channelAuthTokenProvider).fetchToken(auth.channelId)
                ResolvedAuth(
                    prefix = "Bearer", token = token, isAuthToken = true
                )
            }

            is RequestAuth.ContactTokenAuth -> {
                val token = requireNotNull(contactAuthTokenProvider).fetchToken(auth.contactId)
                ResolvedAuth(
                    prefix = "Bearer", token = token, isAuthToken = true
                )
            }
        }
    }

    private fun expireAuth(auth: RequestAuth, token: String) {
        when (auth) {
            is RequestAuth.ChannelTokenAuth -> channelAuthTokenProvider?.expireToken(token)
            is RequestAuth.ContactTokenAuth -> contactAuthTokenProvider?.expireToken(token)
            else -> {}
        }
    }

    private data class RequestResult<T>(val shouldRetry: Boolean, val response: Response<T>)

    private data class ResolvedAuth(
        val prefix: String,
        val token: String,
        val isAuthToken: Boolean = false
    )
}
