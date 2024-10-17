package com.urbanairship.android.layout.property

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.requireField

internal sealed class TapEffect(
    val type: String
) {

    data object None : TapEffect("none")
    data object Default : TapEffect("default")

    companion object {
        private val valueMap = listOf(None, Default).associateBy { it.type }

        fun fromJson(json: JsonMap?): TapEffect = try {
            json?.requireField<String>("type")?.let { valueMap[it] } ?: Default
        } catch (e: JsonException) {
            UALog.w("Failed to parse tap effect! Using default. json: $json", e)
            Default
        }
    }
}
