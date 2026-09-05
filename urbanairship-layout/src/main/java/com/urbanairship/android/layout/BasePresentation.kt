/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.property.PresentationType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * Base presentation info.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class BasePresentation public constructor(
    public val type: PresentationType
) {

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
