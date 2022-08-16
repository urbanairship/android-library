/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.os.Bundle
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.WebViewEvent
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData

internal class WebViewModel(
    val url: String,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseModel(
    viewType = ViewType.WEB_VIEW,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {
    constructor(info: WebViewInfo, env: ModelEnvironment) : this(
        url = info.url,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    final var savedState: Bundle? = null
        private set

    fun onClose() {
        bubbleEvent(WebViewEvent.Close(), LayoutData.empty())
    }

    fun saveState(bundle: Bundle) {
        savedState = bundle
    }
}
