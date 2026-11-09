/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import java.util.concurrent.ConcurrentHashMap

/**
 * The colors a layout names under `references.colors`, which a color reference looks up when
 * it's resolved.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NamedColors private constructor(private val colors: Map<String, Color>) {

    /** Returns the color named [name], or null where these colors don't define it. */
    public operator fun get(name: String): Color? = colors[name]

    /** Names already logged as missing; a missing color is resolved on every draw. */
    private val loggedMissingNames = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns the color named [name], or opaque black where these colors don't define it.
     *
     * A missing name is logged once per [NamedColors], not on each lookup.
     *
     * @param name the name a color reference gives.
     * @return the named color, or [Color.MISSING].
     */
    internal fun colorNamed(name: String): Color = colors[name] ?: Color.MISSING.also {
        if (loggedMissingNames.add(name)) UALog.e { "Unknown color reference $name" }
    }

    /** The names these colors define, in the order [resolveAll] returns them. */
    internal val names: List<String> get() = colors.keys.toList()

    /** Resolves every named color for [context], in [names] order. */
    internal fun resolveAll(context: Context): List<Int> = colors.values.map { it.resolve(context) }

    /**
     * Returns these colors with [content]'s own `references.colors` laid over them, so a name
     * both define takes [content]'s color.
     *
     * @param content a scene, or an async view's wrapper, whose root may carry `references`.
     * @return the colors in scope within [content].
     * @throws JsonException if a named color isn't a color, a reference among them.
     */
    @Throws(JsonException::class)
    public fun scopedTo(content: JsonMap): NamedColors {
        val own = content.opt("references").optMap().opt("colors").optMap().map
        if (own.isEmpty()) return this
        return NamedColors(colors + own.mapValues { (_, json) -> Color.valueFromJson(json) })
    }

    public companion object {
        public val EMPTY: NamedColors = NamedColors(emptyMap())
    }
}
