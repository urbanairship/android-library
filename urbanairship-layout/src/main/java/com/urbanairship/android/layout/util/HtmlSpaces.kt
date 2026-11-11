package com.urbanairship.android.layout.util

private const val NO_BREAK_SPACE = ' '

private val tagRegex = "<[^>]*>".toRegex()

private enum class Neighbor { EDGE, BREAK, SPACE, TEXT }

/**
 * Returns the HTML with every space that [android.text.Html] would collapse or drop swapped for a
 * no-break space, so repeated, leading and trailing spaces draw as typed.
 *
 * The last space of a run stays a plain space when text follows it, so the run keeps one place for a
 * line to wrap. Tags other than `<br>` are skipped when looking for a space's neighbors.
 *
 * @receiver HTML whose line breaks are `<br>` tags.
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
            append(if (collapsible) NO_BREAK_SPACE else char)
        }
    }
}
