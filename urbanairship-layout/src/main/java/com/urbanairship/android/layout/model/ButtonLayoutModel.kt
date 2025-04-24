/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ButtonLayoutInfo
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.ViewGroupInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.util.resolveContentDescription
import com.urbanairship.android.layout.view.ButtonLayoutView

internal class ButtonLayoutModel(
    viewInfo: ButtonLayoutInfo,
    val view: AnyModel,
    formState: ThomasForm?,
    pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties,
) : ButtonModel<ButtonLayoutView, ButtonLayoutInfo>(
    viewInfo = viewInfo,
    formState = formState,
    pagerState = pagerState,
    environment = environment,
    properties = properties,
) {

    override fun contentDescription(context: Context): String? {
        return viewInfo.contentDescriptionResolver(context)
    }

    override fun reportingDescription(context: Context): String {
        return viewInfo.reportingDescriptionResolver(context)
    }

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = ButtonLayoutView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }
}

private val ViewInfo.childContentDescriptionResolvers: List<(Context) -> String?>
    get() {
        return when (this) {
            is LabelInfo -> listOf { context: Context ->
                context.resolveContentDescription(
                    this.contentDescription, this.localizedContentDescription
                ) ?: text
            }

            is MediaInfo -> listOf { context: Context ->
                context.resolveContentDescription(
                    this.contentDescription, this.localizedContentDescription
                )
            }

            is ButtonLayoutInfo -> emptyList()

            is ViewGroupInfo<*> -> this.children.toList()
                .flatMap { it.info.childContentDescriptionResolvers }

            else -> emptyList()
        }
    }

private val ButtonLayoutInfo.contentDescriptionResolver: (Context) -> String?
    get() {
        if (contentDescription != null || localizedContentDescription != null) {
            return { context ->
                context.resolveContentDescription(
                    contentDescription, localizedContentDescription
                )
            }
        }

        val childResolvers = this.view.childContentDescriptionResolvers
        return { context -> childResolvers.mapNotNull { it.invoke(context) }.joinToString(", ") }
    }

private val ButtonLayoutInfo.reportingDescriptionResolver: (Context) -> String
    get() = { context ->
        context.resolveContentDescription(
            contentDescription, localizedContentDescription
        ) ?: identifier
    }
