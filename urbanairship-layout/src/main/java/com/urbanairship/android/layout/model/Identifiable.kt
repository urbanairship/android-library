/*
 Copyright Airship and Contributors
 */
package com.urbanairship.android.layout.model

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal interface Identifiable {

    val identifier: String

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun identifierFromJson(json: JsonMap): String =
            json.opt("identifier").string ?: throw JsonException(
                "Failed to parse identifier from json: $json"
            )
    }
}
