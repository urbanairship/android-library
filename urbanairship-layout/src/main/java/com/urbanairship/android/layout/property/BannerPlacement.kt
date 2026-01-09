/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.model.SafeAreaAware
import com.urbanairship.android.layout.model.SafeAreaAware.Companion.ignoreSafeAreaFromJson
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalMap

public class BannerPlacement public constructor(
    public val size: ConstrainedSize,
    public val margin: Margin?,
    public val position: Position?,
    public val ignoreSafeArea: Boolean,
    public val border: Border?,
    public val backgroundColor: Color?
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

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BannerPlacement {
            val content = json.requireMap()

            return BannerPlacement(
                size = ConstrainedSize.fromJson(content.require(KEY_SIZE)),
                margin = content[KEY_MARGIN]?.let(Margin::fromJson),
                position = Position(HorizontalPosition.CENTER, VerticalPosition.from(content.require(KEY_POSITION))),
                ignoreSafeArea = ignoreSafeAreaFromJson(content),
                border = content[KEY_BORDER]?.let(Border::fromJson),
                backgroundColor = content[KEY_BACKGROUND]?.let(Color::fromJson)
            )
        }
    }
}
