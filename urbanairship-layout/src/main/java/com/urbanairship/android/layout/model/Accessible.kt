/*
 Copyright Airship and Contributors
 */
package com.urbanairship.android.layout.model

import com.urbanairship.json.JsonMap

internal interface Accessible {

    val contentDescription: String?

    companion object {
        @JvmStatic
        fun contentDescriptionFromJson(json: JsonMap): String? =
            json.opt("content_description").string
    }
}
