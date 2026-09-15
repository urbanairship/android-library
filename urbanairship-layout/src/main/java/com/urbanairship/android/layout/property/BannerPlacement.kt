/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.UALog
import com.urbanairship.android.layout.model.SafeAreaAware
import com.urbanairship.android.layout.model.SafeAreaAware.Companion.ignoreSafeAreaFromJson
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalList

public class BannerPlacement public constructor(
    public val size: ConstrainedSize,
    public val margin: Margin?,
    public val position: EdgePosition,
    public val ignoreSafeArea: Boolean,
    public val border: Border?,
    public val backgroundColor: Color?,
    public val transition: BannerTransition?,
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
        private const val KEY_TRANSITION = "transition"
        private const val KEY_SWIPE_TO_DISMISS = "swipe_to_dismiss"
        private const val KEY_SHADOW = "shadow"
        private const val KEY_SELECTORS = "selectors"

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BannerPlacement {
            val content = json.requireMap()

            val shadow = content[KEY_SHADOW]?.let { shadowJson ->
                val resolved = shadowJson.optMap()
                    .optionalList(KEY_SELECTORS)
                    ?.map(ShadowSelector::fromJson)
                    ?.firstOrNull { it.platform == null || it.platform == Platform.ANDROID }
                    ?.shadow
                if (resolved == null) {
                    UALog.w { "Ignoring banner '$KEY_SHADOW'! No usable Android shadow in: $shadowJson" }
                }
                resolved
            }

            val swipeToDismiss = content[KEY_SWIPE_TO_DISMISS]?.let { value ->
                if (value.isBoolean) {
                    value.getBoolean(true)
                } else {
                    UALog.w { "Ignoring banner '$KEY_SWIPE_TO_DISMISS'! Expected a boolean, got: $value" }
                    null
                }
            } ?: true

            return BannerPlacement(
                size = ConstrainedSize.fromJson(content.require(KEY_SIZE)),
                margin = content[KEY_MARGIN]?.let(Margin::fromJson),
                position = EdgePosition.fromJson(content.require(KEY_POSITION)),
                ignoreSafeArea = ignoreSafeAreaFromJson(content),
                border = content[KEY_BORDER]?.let(Border::fromJson),
                backgroundColor = content[KEY_BACKGROUND]?.let(Color::fromJson),
                transition = content[KEY_TRANSITION]?.let(BannerTransition::fromJson),
                swipeToDismiss = swipeToDismiss,
                shadow = shadow
            )
        }
    }
}
