/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.model.SafeAreaAware
import com.urbanairship.android.layout.model.SafeAreaAware.Companion.ignoreSafeAreaFromJson
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ModalPlacement(
    public val size: ConstrainedSize,
    public val margin: Margin?,
    public val position: Position?,
    public val shadeColor: Color?,
    private val ignoreSafeArea: Boolean,
    public val border: Border?,
    public val backgroundColor: Color?,
    public val shadow: Shadow?,
    public val animation: ModalAnimation?
) : SafeAreaAware {

    public override fun shouldIgnoreSafeArea(): Boolean {
        return ignoreSafeArea
    }

    public companion object {
        private const val KEY_SIZE = "size"
        private const val KEY_POSITION = "position"
        private const val KEY_MARGIN = "margin"
        private const val KEY_BORDER = "border"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_SHADE_COLOR = "shade_color"
        private const val KEY_SHADOW = "shadow"
        private const val KEY_SELECTORS = "selectors"
        private const val KEY_ANIMATION = "animation"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ModalPlacement {
            val content = json.requireMap()

            val shadow = content
                .optionalMap(KEY_SHADOW)
                ?.optionalList(KEY_SELECTORS)
                ?.map(ShadowSelector::fromJson)
                ?.firstOrNull { it.platform == null || it.platform == Platform.ANDROID }
                ?.shadow

            return ModalPlacement(
                size = ConstrainedSize.fromJson(content.require(KEY_SIZE)),
                margin = content[KEY_MARGIN]?.let(Margin::fromJson),
                position = content[KEY_POSITION]?.let(Position::fromJson),
                shadeColor = content[KEY_SHADE_COLOR]?.let(Color::fromJson),
                ignoreSafeArea = ignoreSafeAreaFromJson(content),
                border = content[KEY_BORDER]?.let(Border::fromJson),
                backgroundColor = content[KEY_BACKGROUND_COLOR]?.let(Color::fromJson),
                shadow = shadow,
                animation = content[KEY_ANIMATION]?.let(ModalAnimation::fromJson)
            )
        }
    }
}
