/* Copyright Airship and Contributors */
package com.urbanairship

import com.urbanairship.http.AuthTokenProvider
import com.urbanairship.http.Request
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestResult
import com.urbanairship.http.RequestSession
import com.urbanairship.http.ResponseParser

/**
 * Request class used for testing.
 */
public class TestRequestSession : RequestSession {

    private data class HttpResponse(
        var statusCode: Int,
        var body: String? = null,
        var headers: Map<String, String>,
    )

    private var _requests: MutableList<Request> = mutableListOf()
    public val requests: List<Request> get() = _requests

    private var responses: MutableList<HttpResponse> = mutableListOf()

    public fun addResponse(
        statusCode: Int,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ) {
        responses.add(HttpResponse(statusCode, body, headers))
    }

    public val lastRequest: Request get() = _requests.last()

    override suspend fun execute(request: Request): RequestResult<Unit> {
        return execute(request) { _, _, _ -> }
    }

    override suspend fun <T> execute(request: Request, parser: ResponseParser<T?>): RequestResult<T> {
        _requests.add(request)
        if (responses.isEmpty()) {
            return RequestResult(RequestException("No responses set on the test request session"))
        }

        return try {
            requireNotNull(request.url) { "missing url" }
            val response = responses.removeAt(0)

            RequestResult(
                response.statusCode,
                parser.parseResponse(response.statusCode, response.headers, response.body),
                response.body,
                response.headers
            )
        } catch (e: Exception) {
            RequestResult(RequestException("parse error", e))
        }
    }

    override var channelAuthTokenProvider: AuthTokenProvider? = null
    override var contactAuthTokenProvider: AuthTokenProvider? = null
}
