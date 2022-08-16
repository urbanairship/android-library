/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.widget.ImageView
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.ViewType

internal class MediaModel(
    val url: String,
    val mediaType: MediaType,
    val scaleType: ImageView.ScaleType,
    override val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseModel(ViewType.MEDIA, backgroundColor, border, environment), Accessible {
    constructor(info: MediaInfo, env: ModelEnvironment) : this(
        url = info.url,
        mediaType = info.mediaType,
        scaleType = info.scaleType,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )
}
