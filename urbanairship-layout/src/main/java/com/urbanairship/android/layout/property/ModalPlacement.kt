/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.model.SafeAreaAware
import com.urbanairship.android.layout.model.SafeAreaAware.Companion.ignoreSafeAreaFromJson
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

public class ModalPlacement(
    public val size: ConstrainedSize,
    public val margin: Margin?,
    public val position: Position?,
    public val shadeColor: Color?,
    private val ignoreSafeArea: Boolean,
    public val orientationLock: Orientation?,
    public val border: Border?,
    public val backgroundColor: Color?,
    public val shadow: Shadow?
) : SafeAreaAware {

    public override fun shouldIgnoreSafeArea(): Boolean {
        return ignoreSafeArea
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonMap): ModalPlacement {
            val size = ConstrainedSize.fromJson(json.requireField("size"))

            val position = json.optionalMap("position")?.let { Position.fromJson(it) }
            val margin = json.optionalMap("margin")?.let { Margin.fromJson(it) }
            val border = json.optionalMap("border")?.let { Border.fromJson(it) }
            val backgroundColor = json.optionalMap("background_color")?.let { Color.fromJson(it) }
            val shadeColor = json.optionalMap("shade_color")?.let { Color.fromJson(it) }
            val orientationLock = json.optionalMap("device")?.optionalField<String>("lock_orientation")?.let { Orientation.from(it) }
            val ignoreSafeArea = ignoreSafeAreaFromJson(json)

            val shadow = json.optionalMap("shadow")?.let { map ->
                val selectorShadow = map.optionalList("selectors")?.map {
                    ShadowSelector.fromJson(it)
                }?.first {
                   it.platform == null || it.platform == Platform.ANDROID
                }?.shadow

                selectorShadow ?: Shadow.fromJson(map.requireField("default"))
            }

            return ModalPlacement(
                size,
                margin,
                position,
                shadeColor,
                ignoreSafeArea,
                orientationLock,
                border,
                backgroundColor,
                shadow
            )
        }
    }
}
