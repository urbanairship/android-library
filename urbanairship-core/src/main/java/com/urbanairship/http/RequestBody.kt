package com.urbanairship.http

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

public sealed class RequestBody(
    public val content: String,
    public val contentType: String,
    public val compress: Boolean
) {
    public data class Json(val json: JsonValue) : RequestBody(
        json.toString(),
        "application/json",
        false
    ) {
        public constructor(json: JsonSerializable) : this(json.toJsonValue())
        @Throws(JsonException::class)
        public constructor(json: String) : this(JsonValue.parseString(json))
    }

    public data class GzippedJson(val json: JsonValue) : RequestBody(
        json.toString(),
        "application/json",
        true
    ) {
        public constructor(json: JsonSerializable) : this(json.toJsonValue())
        @Throws(JsonException::class)
        public constructor(json: String) : this(JsonValue.parseString(json))
    }
}
