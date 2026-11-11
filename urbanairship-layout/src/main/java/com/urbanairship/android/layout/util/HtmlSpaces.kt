package com.urbanairship.android.layout.util

import android.text.SpannableStringBuilder
import android.text.Spanned

/**
 * Stands in for a space [android.text.Html] would collapse or drop, until [restoreSpaces] puts it
 * back. A noncharacter, which no real text holds.
 */
internal const val KEPT_SPACE: Char = '\uFDD0'

private val tagRegex = "<[^>]*>".toRegex()

private enum class Neighbor { EDGE, BREAK, SPACE, TEXT }

/**
 * Returns the HTML with every space that [android.text.Html] would collapse or drop swapped for
 * [KEPT_SPACE], so [restoreSpaces] can draw repeated, leading and trailing spaces as typed.
 *
 * A space stays plain when text follows it and text or a space precedes it, since `Html` keeps that
 * one. Tags other than `<br>` are skipped when looking for a space's neighbors.
 *
 * @receiver HTML whose line breaks are `<br>` tags, and whose text has `<`, `>` and `&` encoded.
 * @return The HTML with those spaces replaced.
 */
internal fun String.preserveSpaces(): String {
    if (' ' !in this) return this

    val tagRanges = tagRegex.findAll(this).map { it.range }.toList()
    val isInTag = BooleanArray(length)
    for (range in tagRanges) {
        for (i in range) isInTag[i] = true
    }
    val isBreak = BooleanArray(length)
    for (range in tagRanges) {
        if (substring(range).startsWith("<br", ignoreCase = true)) isBreak[range.first] = true
    }

    // What sits just before and just after each character, skipping non-break tags.
    val before = Array(length) { Neighbor.EDGE }
    var previous = Neighbor.EDGE
    for (i in indices) {
        before[i] = previous
        previous = when {
            isInTag[i] && isBreak[i] -> Neighbor.BREAK
            isInTag[i] -> previous
            this[i] == ' ' -> Neighbor.SPACE
            else -> Neighbor.TEXT
        }
    }
    val after = Array(length) { Neighbor.EDGE }
    var next = Neighbor.EDGE
    for (i in indices.reversed()) {
        after[i] = next
        next = when {
            isInTag[i] && isBreak[i] -> Neighbor.BREAK
            isInTag[i] -> next
            this[i] == ' ' -> Neighbor.SPACE
            else -> Neighbor.TEXT
        }
    }

    return buildString(length) {
        for (i in this@preserveSpaces.indices) {
            val char = this@preserveSpaces[i]
            // The spaces before it in a run are already no-break, so only the last can stay plain.
            val staysPlain = after[i] == Neighbor.TEXT && (before[i] == Neighbor.TEXT || before[i] == Neighbor.SPACE)
            val collapsible = char == ' ' && !isInTag[i] && !staysPlain
            append(if (collapsible) KEPT_SPACE else char)
        }
    }
}

/**
 * Returns the parsed text with each [KEPT_SPACE] back to a plain space, keeping every span.
 *
 * Plain spaces wrap and hang at a line's end the way web's `pre-wrap` does, where a no-break space
 * would count toward a line's width.
 *
 * @receiver Text parsed from [preserveSpaces]' HTML.
 * @return The text with real spaces.
 */
internal fun Spanned.restoreSpaces(): Spanned {
    if (KEPT_SPACE !in this) return this
    return SpannableStringBuilder(this).apply {
        for (i in indices) {
            if (this[i] == KEPT_SPACE) replace(i, i + 1, " ")
        }
    }
}
