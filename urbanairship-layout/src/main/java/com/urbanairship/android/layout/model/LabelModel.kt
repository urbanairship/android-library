/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.property.ViewType

internal class LabelModel(
    val text: String,
    val textAppearance: TextAppearance,
    override val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseModel(ViewType.LABEL, backgroundColor, border, environment), Accessible {
    constructor(info: LabelInfo, env: ModelEnvironment) : this(
        text = info.text,
        textAppearance = info.textAppearance,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )
}
