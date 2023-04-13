package com.urbanairship.android.layout.info

import com.urbanairship.android.layout.BasePresentation
import com.urbanairship.android.layout.info.ViewInfo.Companion.viewInfoFromJson
import com.urbanairship.json.JsonMap
import com.urbanairship.json.requireField

public data class LayoutInfo(
    public val version: Int,
    public val presentation: BasePresentation,
    public val view: ViewInfo
) {
    public constructor(json: JsonMap) : this(
        version = json.requireField("version"),
        presentation = BasePresentation.fromJson(json.requireField("presentation")),
        view = viewInfoFromJson(json.requireField("view"))
    )
}
