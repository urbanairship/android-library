package com.urbanairship.android.layout.property

import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import com.urbanairship.UALog
import com.urbanairship.android.layout.util.LayoutUtils.dpToPx
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap

internal class MarkdownOptions(
    json: JsonMap
) {
    val disabled: Boolean? = json.optionalField("disabled")
    val appearance: MarkdownAppearance? = json.optionalMap("appearance")?.let { MarkdownAppearance(it) }
}

internal class MarkdownAppearance(json: JsonMap) {
    val links: LinkAppearance? = json.optionalMap("anchor")?.let { LinkAppearance(it) }
    val highlight: HighlightAppearance? = json.optionalMap("highlight")?.let { HighlightAppearance(it) }

    internal class LinkAppearance(json: JsonMap) {

        val color: Color? = Color.fromJsonField(json, "color")

        val styles: List<TextStyle>? = json.optionalList("styles")
            ?.mapNotNull {
                try {
                    TextStyle.from(it)
                } catch (e: JsonException) {
                    UALog.w("Failed to parse anchor styles: ${e.message}")
                    null
                }
            }
            // We only support underline for anchors, so we'll ignore any other styles for now.
            ?.filter { it == TextStyle.UNDERLINE }

    }

    internal class HighlightAppearance(json: JsonMap) {
        val color: Color? = Color.fromJsonField(json, "color")
        val cornerRadius: Float? = json.optionalField<Float>("corner_radius")
    }
}

// Helper extensions

/**
 * Whether limited markdown rendering should be enabled.
 *
 * If `disabled` is present in the payload, it will override the default value of `true`.
 */
internal val MarkdownOptions?.isEnabled: Boolean
    get() = this?.disabled?.not() ?: true

/**
 * Optional color override for links.
 *
 * If a link color is present in the payload, it will be resolved to a `ColorInt` and returned.
 * Otherwise, `null`.
 */
@ColorInt
internal fun MarkdownOptions?.resolvedLinkColor(context: Context): Int? =
    this?.appearance?.links?.color?.resolve(context)

@ColorInt
internal fun MarkdownOptions?.resolveHighlightCornerRadius(context: Context): Float =
    this?.appearance?.highlight?.cornerRadius?.let {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            it,
            context.resources.displayMetrics
        )
    } ?: 0F

@ColorInt
internal fun MarkdownOptions?.resolveHighlightColor(context: Context): Int =
    (this?.appearance?.highlight?.color?.resolve(context) ?: 0x4DFFD60A)

/**
 * Whether links should be underlined.
 *
 * If the link styles contain [TextStyle.UNDERLINE], returns `true`, otherwise `false`.
 */
internal val MarkdownOptions?.underlineLinks: Boolean
    get() = this?.appearance?.links?.styles?.contains(TextStyle.UNDERLINE) ?: false
