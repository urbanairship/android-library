/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.os.Bundle
import com.urbanairship.android.layout.event.WebViewEvent
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class WebViewModel(
    val url: String,
    backgroundColor: Color?,
    border: Border?
) : BaseModel(ViewType.WEB_VIEW, backgroundColor, border) {

    final var savedState: Bundle? = null
        private set

    fun onClose() {
        bubbleEvent(WebViewEvent.Close(), LayoutData.empty())
    }

    fun saveState(bundle: Bundle) {
        savedState = bundle
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): WebViewModel =
            WebViewModel(
                url = json.opt("url").optString(),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
    }
}
