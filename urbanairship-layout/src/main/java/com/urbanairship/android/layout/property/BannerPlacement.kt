/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.model.SafeAreaAware
import com.urbanairship.android.layout.model.SafeAreaAware.Companion.ignoreSafeAreaFromJson
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap

public class BannerPlacement public constructor(
    public val size: ConstrainedSize,
    public val margin: Margin?,
    public val position: Position,
    public val ignoreSafeArea: Boolean,
    public val border: Border?,
    public val backgroundColor: Color?,
    public val animation: BannerAnimation = BannerAnimation.DEFAULT,
    public val swipeToDismiss: Boolean = true,
    public val shadow: Shadow? = null
) : SafeAreaAware {

    override fun shouldIgnoreSafeArea(): Boolean {
        return ignoreSafeArea
    }

    public companion object {

        private const val KEY_SIZE = "size"
        private const val KEY_MARGIN = "margin"
        private const val KEY_POSITION = "position"
        private const val KEY_BORDER = "border"
        private const val KEY_BACKGROUND = "background_color"
        private const val KEY_ANIMATION = "animation"
        private const val KEY_SWIPE_TO_DISMISS = "swipe_to_dismiss"
        private const val KEY_SHADOW = "shadow"
        private const val KEY_SELECTORS = "selectors"

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BannerPlacement {
            val content = json.requireMap()

            val positionJson = content.require(KEY_POSITION)
            val position = if (positionJson.isString) {
                // Legacy payloads specify only a vertical edge for the position.
                Position(HorizontalPosition.CENTER, VerticalPosition.from(positionJson))
            } else {
                Position.fromJson(positionJson)
            }

            if (position.horizontal == HorizontalPosition.CENTER &&
                position.vertical == VerticalPosition.CENTER) {
                throw JsonException("Banner position must include at least one non-center edge!")
            }

            val shadow = content
                .optionalMap(KEY_SHADOW)
                ?.optionalList(KEY_SELECTORS)
                ?.map(ShadowSelector::fromJson)
                ?.firstOrNull { it.platform == null || it.platform == Platform.ANDROID }
                ?.shadow

            return BannerPlacement(
                size = ConstrainedSize.fromJson(content.require(KEY_SIZE)),
                margin = content[KEY_MARGIN]?.let(Margin::fromJson),
                position = position,
                ignoreSafeArea = ignoreSafeAreaFromJson(content),
                border = content[KEY_BORDER]?.let(Border::fromJson),
                backgroundColor = content[KEY_BACKGROUND]?.let(Color::fromJson),
                animation = content[KEY_ANIMATION]?.let(BannerAnimation::fromJson)
                    ?: BannerAnimation.DEFAULT,
                swipeToDismiss = content.opt(KEY_SWIPE_TO_DISMISS).getBoolean(true),
                shadow = shadow
            )
        }
    }
}
