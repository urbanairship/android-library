package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField



public data class Shadow(
    val androidShadow: ElevationShadow?
) {

    public companion object {
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): Shadow {
            return Shadow(
                androidShadow = json.requireMap()["android_shadow"]?.let { ElevationShadow.fromJson(it) }
            )
        }
    }

    public data class ElevationShadow(
        val color: Color, val elevation: Float
    ) {
        public companion object {
            @Throws(JsonException::class)
            public fun fromJson(json: JsonValue): ElevationShadow {
                val map = json.requireMap()
                return ElevationShadow(
                    color = Color.fromJson(map.requireField("color")),
                    elevation = map.requireField("elevation")
                )
            }
        }
    }
}
