/*
 Copyright Airship and Contributors
 */
package com.urbanairship.android.layout.property

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.urbanairship.UALog.w
import com.urbanairship.json.JsonMap

public object HexColor {

    @JvmStatic
    @ColorInt
    public fun fromJson(json: JsonMap?): Int? {
        if (json == null || json.isEmpty) {
            return null
        }
        val hex = json.opt("hex").optString()
        val alpha = json.opt("alpha").getFloat(1f).coerceIn(0.0f, 1.0f)

        if (hex.isEmpty()) {
            w("Invalid Color json: %s", json.toString())
            return null
        }

        var color = Color.parseColor(hex)
        if (alpha != 1f) {
            color = ColorUtils.setAlphaComponent(color, (alpha * 255).toInt())
        }
        return color
    }
}
