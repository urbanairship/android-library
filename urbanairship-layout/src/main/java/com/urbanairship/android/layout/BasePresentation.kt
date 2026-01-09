/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public abstract class BasePresentation public constructor(
    public val type: PresentationType
) {

    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): BasePresentation {
            val content = json.requireMap()
            val typeJson = content.require("type")

            return when (PresentationType.from(typeJson)) {
                PresentationType.BANNER -> BannerPresentation.fromJson(json)
                PresentationType.MODAL -> ModalPresentation.fromJson(json)
                PresentationType.EMBEDDED -> EmbeddedPresentation.Companion.fromJson(json)
            }
        }
    }
}
