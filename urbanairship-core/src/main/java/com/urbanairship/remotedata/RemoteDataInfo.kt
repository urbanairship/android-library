/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/**
 * Remote data info
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RemoteDataInfo @JvmOverloads public constructor(
    val url: String,
    val lastModified: String?,
    val source: RemoteDataSource,
    val contactId: String? = null
) : JsonSerializable {

    @Throws(JsonException::class)
    public constructor(json: JsonValue) : this(
        url = json.requireMap().requireField("url"),
        lastModified = json.requireMap().optionalField("lastModified"),
        source = json.requireMap().requireField<String>("source").let {
            try {
                RemoteDataSource.valueOf(it)
            } catch (e: Exception) {
                throw JsonException("Invalid source", e)
            }
        },
        contactId = json.requireMap().optionalField("contactId")
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
        "url" to url,
        "lastModified" to lastModified,
        "source" to source.name,
        "contactId" to contactId
    ).toJsonValue()
}
