/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.LocalizedContentDescription
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.LabelView

internal class LabelModel(
    val text: String,
    val textAppearance: TextAppearance,
    val markdownOptions: MarkdownOptions?,
    val contentDescription: String? = null,
    val localizedContentDescription: LocalizedContentDescription? = null,
    val accessibilityHidden: Boolean = false,
    val accessibilityRole: LabelInfo.AccessibilityRole?,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<LabelView, BaseModel.Listener>(
    viewType = ViewType.LABEL,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) {
    constructor(info: LabelInfo, env: ModelEnvironment, props: ModelProperties) : this(
        text = info.text,
        textAppearance = info.textAppearance,
        accessibilityRole = info.accessibilityRole,
        markdownOptions = info.markdownOptions,
        contentDescription = info.contentDescription,
        localizedContentDescription = info.localizedContentDescription,
        accessibilityHidden = info.accessibilityHidden ?: false,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env,
        properties = props
    )


    override fun onCreateView(context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?) =
        LabelView(context, this).apply {
            id = viewId
        }
}
