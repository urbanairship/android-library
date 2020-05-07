package com.urbanairship.debug.extensions

import com.urbanairship.json.JsonSerializable
import org.json.JSONObject

fun JsonSerializable.toFormattedJsonString(): String {
    return JSONObject(this.toJsonValue().toString()).toString(4)
}
