package com.urbanairship.http

import android.util.Base64
import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.Platform
import com.urbanairship.Provider
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import com.urbanairship.util.toSignedToken
import java.util.UUID

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
    private val platformProvider: Provider<Platform>
    private val clock: Clock
    private val nonceTokenFactory: () -> String

    public constructor(
        configOptions: AirshipConfigOptions,
        platformProvider: Provider<Platform>
    ) : this(configOptions, platformProvider, DefaultHttpClient())

    internal constructor(
        configOptions: AirshipConfigOptions,
        platformProvider:  Provider<Platform>,
        httpClient: HttpClient,
        clock: Clock = Clock.DEFAULT_CLOCK,
        nonceTokenFactory: () -> String = { UUID.randomUUID().toString() },
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
                "User-Agent" to "(UrbanAirshipLib-${platformProvider.get().stringValue}/${Airship.version}; ${configOptions.appKey})"
            )
        }

    @Throws(RequestException::class)
    public override suspend fun execute(request: Request): RequestResult<Unit> {
        return execute(request) { _, _, _ -> }
    }

    /**
     * Executes the request.
     *
     * @return The request response.
     */
    @Throws(RequestException::class)
    public override suspend fun <T> execute(
        request: Request,
        parser: ResponseParser<T?>
    ): RequestResult<T> {
        return try {
            val result = doExecute(request, parser)
            if (result.shouldRetry) {
                doExecute(request, parser)
            } else {
                result
            }
        } catch (e: Exception) {
            RequestResult(e)
        }
    }

    private suspend fun <T> doExecute(
        request: Request,
        parser: ResponseParser<T?>
    ): RequestResult<T> {
        if (request.url == null) {
            return RequestResult(RequestException("Missing URL"))
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
                url = request.url,
                method = request.method,
                headers = headers,
                body = request.body,
                followRedirects = request.followRedirects,
                parser = parser
            )

            return if (response.status == 401 && auth != null && auth.authToken != null) {
                expireAuth(request.auth, auth.authToken)
                RequestResult(
                    response = response,
                    shouldRetry = true
                )
            } else {
                RequestResult(
                    response = response,
                    shouldRetry = false
                )
            }
        } catch (e: Exception) {
            return RequestResult(RequestException("Request failed: $request", e))
        }
    }

    private suspend fun resolveAuth(auth: RequestAuth): ResolvedAuth {
        return when (auth) {
            is RequestAuth.BasicAppAuth -> {
                val credentials = configOptions.appKey + ":" + configOptions.appSecret
                val token = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                ResolvedAuth(
                    headers = mapOf("Authorization" to "Basic $token")
                )
            }

            is RequestAuth.BasicAuth -> {
                val credentials = auth.user + ":" + auth.password
                val token = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                ResolvedAuth(
                    headers = mapOf("Authorization" to "Basic $token")
                )
            }

            is RequestAuth.BearerToken -> {
                ResolvedAuth(
                    headers = mapOf("Authorization" to "Bearer ${auth.token}")
                )
            }

            is RequestAuth.ChannelTokenAuth -> {
                resolveTokenAuth(requireNotNull(channelAuthTokenProvider), auth.channelId)
            }

            is RequestAuth.ContactTokenAuth -> {
                resolveTokenAuth(requireNotNull(contactAuthTokenProvider), auth.contactId)
            }

            is RequestAuth.GeneratedAppToken -> {
                val requestTime = clock.currentTimeMillis()
                val nonce = nonceTokenFactory()
                val timestamp = DateUtils.createIso8601TimeStamp(requestTime)

                val token = configOptions.appSecret.toSignedToken(
                    values = listOf(configOptions.appKey, nonce, timestamp)
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

                val token = configOptions.appSecret.toSignedToken(
                    values = listOf(configOptions.appKey, auth.channelId, nonce, timestamp)
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

    private suspend fun resolveTokenAuth(provider: AuthTokenProvider, id: String): ResolvedAuth {
        val token = provider.fetchToken(id).getOrThrow()
        return ResolvedAuth(
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "X-UA-Appkey" to configOptions.appKey,
                "X-UA-Auth-Type" to "SDK-JWT",
            ),
            authToken = token
        )
    }

    private suspend fun expireAuth(auth: RequestAuth, token: String) {
        when (auth) {
            is RequestAuth.ChannelTokenAuth -> channelAuthTokenProvider?.expireToken(token)
            is RequestAuth.ContactTokenAuth -> contactAuthTokenProvider?.expireToken(token)
            else -> {}
        }
    }

    private data class ResolvedAuth(
        val headers: Map<String, String>,
        val authToken: String? = null,
    )
}
