package com.urbanairship.http

import android.net.Uri

internal interface HttpClient {
    fun <T> execute(
        url: Uri,
        method: String,
        headers: Map<String, String>,
        body: RequestBody?,
        followRedirects: Boolean,
        parser: ResponseParser<T>
    ): Response<T>
}
