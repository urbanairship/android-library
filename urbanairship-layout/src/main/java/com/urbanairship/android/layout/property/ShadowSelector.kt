package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

public data class ShadowSelector(
    val platform: Platform?,
    val shadow: Shadow
) {
    public companion object {
        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ShadowSelector {
            return ShadowSelector(
                platform = json.requireMap()["platform"]?.let(Platform::from),
                shadow = Shadow.fromJson(json.requireMap().requireField("shadow"))
            )
        }
    }
}
