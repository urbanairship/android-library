package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.Size

internal data class ItemProperties(
    val size: Size?,
    /** Where the layout holding this item anchors it, or null where it states none. */
    val position: Position? = null
)
