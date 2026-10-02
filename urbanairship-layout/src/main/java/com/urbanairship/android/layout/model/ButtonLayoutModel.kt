/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ButtonLayoutInfo
import com.urbanairship.android.layout.info.GridLayoutInfo
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.ViewGroupInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.util.ShuffleKey
import com.urbanairship.android.layout.util.resolveContentDescription
import com.urbanairship.android.layout.util.shuffledIf
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

    /** See [videoBackgroundModels] -- a playable layer is a real child view, not painted. */
    val videoBackgroundModels = videoBackgroundModels(
        layers = viewInfo.backgrounds,
        pagerState = environment.layoutState.pager,
        environment = environment,
        properties = properties
    )

    /** Wraps a view without resizing it, so what it wraps answers for it. */
    override fun establishesLength(direction: Direction): Boolean =
        view.establishesLength(direction)

    override var isShrinkable: Boolean = view.isShrinkable

    override fun contentDescription(context: Context): String? {
        return viewInfo.contentDescriptionResolver(environment.shuffleSeed)(context)
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

/**
 * Returns a resolver for each label and media inside this view, in the order they're shown.
 *
 * @param seed the layout's shuffle seed, so a `randomize_children` layout is read as it's shuffled.
 */
private fun ViewInfo.childContentDescriptionResolvers(seed: String): List<(Context) -> String?> {
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

        is LinearLayoutInfo -> items
            .shuffledIf(randomizeChildren, ShuffleKey(seed, identifier))
            .sortedBy { it.config.order }
            .flatMap { it.info.childContentDescriptionResolvers(seed) }

        is GridLayoutInfo -> (
            listOfNotNull(header) +
                items.shuffledIf(randomizeChildren, ShuffleKey(seed, identifier)).sortedBy { it.config.order } +
                listOfNotNull(footer)
            ).flatMap { it.info.childContentDescriptionResolvers(seed) }

        is ViewGroupInfo<*> -> this.children.toList()
            .flatMap { it.info.childContentDescriptionResolvers(seed) }

        else -> emptyList()
    }
}

private fun ButtonLayoutInfo.contentDescriptionResolver(seed: String): (Context) -> String? {
    if (contentDescription != null || localizedContentDescription != null) {
        return { context ->
            context.resolveContentDescription(
                contentDescription, localizedContentDescription
            )
        }
    }

    val childResolvers = this.view.childContentDescriptionResolvers(seed)
    return { context -> childResolvers.mapNotNull { it.invoke(context) }.joinToString(", ") }
}

private val ButtonLayoutInfo.reportingDescriptionResolver: (Context) -> String
    get() = { context ->
        context.resolveContentDescription(
            contentDescription, localizedContentDescription
        ) ?: identifier
    }
