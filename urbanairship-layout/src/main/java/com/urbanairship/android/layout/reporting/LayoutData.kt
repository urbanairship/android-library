/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap.Companion.newBuilder
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/**
 * Layout state of an event.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class LayoutData public constructor(
    public val formInfo: FormInfo?,
    public val pagerData: PagerData?,
    public val buttonIdentifier: String?
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_FORM_INFO to formInfo,
        KEY_PAGER_DATA to pagerData,
        KEY_BUTTON_IDENTIFIER to buttonIdentifier
    ).toJsonValue()

    public companion object {
        public val EMPTY: LayoutData = LayoutData(null, null, null)

        public fun form(formInfo: FormInfo?): LayoutData {
            return LayoutData(formInfo, null, null)
        }

        public fun pager(pagerData: PagerData?): LayoutData {
            return LayoutData(null, pagerData, null)
        }

        public fun button(buttonIdentifier: String?): LayoutData {
            return LayoutData(null, null, buttonIdentifier)
        }

        private const val KEY_FORM_INFO = "formInfo"
        private const val KEY_PAGER_DATA = "pagerData"
        private const val KEY_BUTTON_IDENTIFIER = "buttonIdentifier"
    }
}
