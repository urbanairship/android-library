/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.widget.ImageView
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class MediaModel(
    val url: String,
    val mediaType: MediaType,
    val scaleType: ImageView.ScaleType,
    override val contentDescription: String?,
    backgroundColor: Color?,
    border: Border?
) : BaseModel(ViewType.MEDIA, backgroundColor, border), Accessible {

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): MediaModel {
            val url = json.opt("url").optString()
            val mediaTypeString = json.opt("media_type").optString()
            val mediaFitString = json.opt("media_fit").optString()
            return MediaModel(
                url = url,
                mediaType = MediaType.from(mediaTypeString),
                scaleType = MediaFit.asScaleType(mediaFitString),
                contentDescription = contentDescriptionFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
