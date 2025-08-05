package com.urbanairship.http

import android.util.Base64
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.Provider
import com.urbanairship.UAirship
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import com.urbanairship.util.UAStringUtil
import java.util.UUID
import kotlinx.coroutines.runBlocking

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
    private val platformProvider: Provider<UAirship.Platform>
    private val clock: Clock
    private val nonceTokenFactory: () -> String

    public constructor(configOptions: AirshipConfigOptions, platformProvider: Provider<UAirship.Platform>) : this(
        configOptions, platformProvider, DefaultHttpClient()
    )

    internal constructor(
        configOptions: AirshipConfigOptions,
        platformProvider:  Provider<UAirship.Platform>,
        httpClient: HttpClient,
        clock: Clock = Clock.DEFAULT_CLOCK,
        nonceTokenFactory: () -> String = { UUID.randomUUID().toString() }
    ) {
        this.configOptions = configOptions
        this.platformProvider = platformProvider
        this.httpClient = httpClient
        this.nonceTokenFactory = nonceTokenFactory
        this.clock = clock
    }

    public override var channelAuthTokenProvider: AuthTokenProvider? = null
    public override var contactAuthTokenProvider: AuthTokenProvider? = null

    private val defaultHeaders: Map<String, String>
        get() {
            return mapOf(
                "X-UA-App-Key" to configOptions.appKey,
                "User-Agent" to "(UrbanAirshipLib-${platformProvider.get().stringValue}/${UAirship.getVersion()}; ${configOptions.appKey})"
            )
        }

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
                headers += it.headers
            }

            val response = httpClient.execute(
                request.url, request.method, headers, request.body, request.followRedirects, parser
            )

            return if (response.status == 401 && auth != null && auth.authToken != null) {
                expireAuth(request.auth, auth.authToken)
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
                val token = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                ResolvedAuth(
                    headers = mapOf(
                        "Authorization" to "Basic $token"
                    )
                )
            }

            is RequestAuth.BasicAuth -> {
                val credentials = auth.user + ":" + auth.password
                val token = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                ResolvedAuth(
                    headers = mapOf(
                        "Authorization" to "Basic $token"
                    )
                )
            }

            is RequestAuth.BearerToken -> {
                ResolvedAuth(
                    headers = mapOf(
                        "Authorization" to "Bearer ${auth.token}"
                    )
                )
            }

            is RequestAuth.ChannelTokenAuth -> {
                val token = getToken(auth.channelId, requireNotNull(channelAuthTokenProvider))
                ResolvedAuth(
                    headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "X-UA-Appkey" to configOptions.appKey,
                    ),
                    authToken = token
                )
            }

            is RequestAuth.ContactTokenAuth -> {
                val token = getToken(auth.contactId, requireNotNull(contactAuthTokenProvider))
                ResolvedAuth(
                    headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "X-UA-Appkey" to configOptions.appKey,
                    ),
                    authToken = token
                )
            }

            is RequestAuth.GeneratedAppToken -> {
                val requestTime = clock.currentTimeMillis()
                val nonce = nonceTokenFactory()
                val timestamp = DateUtils.createIso8601TimeStamp(requestTime)

                val token = UAStringUtil.generateSignedToken(
                        configOptions.appSecret,
                        listOf(
                            configOptions.appKey, nonce, timestamp
                        )
                    )

                ResolvedAuth(
                    headers = mapOf(
                        "X-UA-Appkey" to configOptions.appKey,
                        "X-UA-Nonce" to nonce,
                        "X-UA-Timestamp" to timestamp,
                        "Authorization" to "Bearer $token",
                    )
                )
            }

            is RequestAuth.GeneratedChannelToken -> {
                val requestTime = clock.currentTimeMillis()
                val nonce = nonceTokenFactory()
                val timestamp = DateUtils.createIso8601TimeStamp(requestTime)

                val token = UAStringUtil.generateSignedToken(
                    configOptions.appSecret,
                    listOf(
                        configOptions.appKey, auth.channelId, nonce, timestamp
                    )
                )

                ResolvedAuth(
                    headers = mapOf(
                        "X-UA-Appkey" to configOptions.appKey,
                        "X-UA-Nonce" to nonce,
                        "X-UA-Channel-ID" to auth.channelId,
                        "X-UA-Timestamp" to timestamp,
                        "Authorization" to "Bearer $token",
                    )
                )
            }
        }
    }

    private fun expireAuth(auth: RequestAuth, token: String) {
        when (auth) {
            is RequestAuth.ChannelTokenAuth -> runBlocking {
                channelAuthTokenProvider?.expireToken(token)
            }
            is RequestAuth.ContactTokenAuth -> runBlocking {
                contactAuthTokenProvider?.expireToken(token)
            }
            else -> {}
        }
    }

    private fun getToken(identifier: String, provider: AuthTokenProvider): String {
        val result = runBlocking {
            val result = provider.fetchToken(identifier)
            result
        }

        return result.getOrThrow()
    }

    private data class RequestResult<T>(val shouldRetry: Boolean, val response: Response<T>)

    private data class ResolvedAuth(
        val headers: Map<String, String>,
        val authToken: String? = null,
    )
}
