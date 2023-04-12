package com.urbanairship.http

import com.urbanairship.json.JsonSerializable

public sealed class RequestBody(
    public val content: String,
    public val contentType: String,
    public val compress: Boolean
) {
    public data class Json(val json: String) : RequestBody(
        json,
        "application/json",
        false
    ) {
        public constructor(json: JsonSerializable) : this(json.toJsonValue().toString())
    }

    public data class GzippedJson(val json: String) : RequestBody(
        json.toString(),
        "application/json",
        true
    ) {
        public constructor(json: JsonSerializable) : this(json.toJsonValue().toString())
    }
}
