package com.urbanairship.android.layout.util

/**
 * A run of identical `*`, `_` or `~` markers outside any HTML tag.
 *
 * @property scope Which link's text the run sits in, or 0 outside every link.
 */
private class DelimiterRun(
    val start: Int,
    val char: Char,
    val length: Int,
    val canOpen: Boolean,
    val canClose: Boolean,
    val scope: Int
) {
    var remaining = length
    var opensAtAll = canOpen
    val openTags = StringBuilder()
    val closeTags = StringBuilder()
}

/** Whether a character counts as punctuation for flanking: Unicode punctuation and symbols, and escape stand-ins. */
private fun Char.isFlankPunctuation(): Boolean = when (Character.getType(this)) {
    Character.CONNECTOR_PUNCTUATION.toInt(),
    Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(),
    Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
    Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(),
    Character.MATH_SYMBOL.toInt(),
    Character.CURRENCY_SYMBOL.toInt(),
    Character.MODIFIER_SYMBOL.toInt(),
    Character.OTHER_SYMBOL.toInt() -> true
    else -> this in ''..''
}

private fun Char?.isFlankWhitespace(): Boolean = this == null || Character.isWhitespace(this) || this == ' '

private fun Char?.isFlankPunct(): Boolean = this != null && isFlankPunctuation()

/**
 * Wraps `*`, `_` and `~` emphasis in `<b>`, `<i>` and `<s>` using the CommonMark delimiter-run rules, so
 * the result matches iOS and web: markers open and close by flanking, `_` never works inside a word,
 * and a marker with no partner stays literal.
 *
 * HTML tags in the text are skipped, and read as punctuation beside a marker. A link binds tighter
 * than emphasis, so markers inside a link's text only pair with each other, and markers outside it
 * never pair across its edge.
 *
 * @receiver Text that is already HTML-encoded, with escaped characters swapped for stand-ins.
 * @return The text with matched markers replaced by tags.
 */
internal fun String.applyEmphasis(): String {
    val runs = findDelimiterRuns()
    if (runs.isEmpty()) return this

    matchDelimiterRuns(runs)

    return buildString {
        var cursor = 0
        for (run in runs) {
            append(this@applyEmphasis.substring(cursor, run.start))
            append(run.closeTags)
            repeat(run.remaining) { append(run.char) }
            append(run.openTags)
            cursor = run.start + run.length
        }
        append(this@applyEmphasis.substring(cursor))
    }
}

private fun String.findDelimiterRuns(): List<DelimiterRun> {
    val runs = mutableListOf<DelimiterRun>()
    var scope = 0
    var nextScope = 1
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c == '<' -> {
                val close = indexOf('>', i)
                val tagEnd = if (close < 0) length else close + 1
                val tag = substring(i, tagEnd)
                when {
                    tag.startsWith("<a ") || tag == "<a>" -> scope = nextScope++
                    tag == "</a>" -> scope = 0
                }
                i = tagEnd
            }
            c == '*' || c == '_' || c == '~' -> {
                var end = i
                while (end < length && this[end] == c) end++
                runs += delimiterRun(i, end, c, scope)
                i = end
            }
            else -> i++
        }
    }
    return runs
}

/** Returns the character a flanking check sees before [index]; a line break reads as whitespace. */
private fun String.flankCharBefore(index: Int): Char? =
    if (startsWith(newlineTag, index - newlineTag.length)) ' ' else getOrNull(index - 1)

/** Returns the character a flanking check sees at [index]; a line break reads as whitespace. */
private fun String.flankCharAfter(index: Int): Char? =
    if (startsWith(newlineTag, index)) ' ' else getOrNull(index)

private fun String.delimiterRun(start: Int, end: Int, char: Char, scope: Int): DelimiterRun {
    val before = flankCharBefore(start)
    val after = flankCharAfter(end)
    val leftFlanking = !after.isFlankWhitespace() &&
        (!after.isFlankPunct() || before.isFlankWhitespace() || before.isFlankPunct())
    val rightFlanking = !before.isFlankWhitespace() &&
        (!before.isFlankPunct() || after.isFlankWhitespace() || after.isFlankPunct())

    val (canOpen, canClose) = when (char) {
        '_' -> (leftFlanking && (!rightFlanking || before.isFlankPunct())) to
            (rightFlanking && (!leftFlanking || after.isFlankPunct()))
        else -> leftFlanking to rightFlanking
    }
    return DelimiterRun(start, char, end - start, canOpen, canClose, scope)
}

/** Pairs closers with the nearest compatible opener, recording the tags each run gains. */
private fun matchDelimiterRuns(runs: List<DelimiterRun>) {
    for (closerIndex in runs.indices) {
        val closer = runs[closerIndex]
        if (!closer.canClose) continue

        while (closer.remaining > 0) {
            val openerIndex = (closerIndex - 1 downTo 0).firstOrNull { canPair(runs[it], closer) } ?: break
            val opener = runs[openerIndex]

            val count = when {
                closer.char == '~' -> closer.remaining
                opener.remaining >= 3 && closer.remaining >= 3 -> 3
                opener.remaining >= 2 && closer.remaining >= 2 -> 2
                else -> 1
            }
            val (open, close) = when {
                closer.char == '~' -> "<s>" to "</s>"
                count == 3 -> "<b><i>" to "</i></b>"
                count == 2 -> "<b>" to "</b>"
                else -> "<i>" to "</i>"
            }

            // Later matches wrap earlier ones, so they sit further from the text between the markers.
            opener.openTags.insert(0, open)
            closer.closeTags.append(close)
            opener.remaining -= count
            closer.remaining -= count

            // Markers between a pair can no longer open anything that closes outside it.
            for (between in openerIndex + 1 until closerIndex) {
                runs[between].opensAtAll = false
            }
        }
    }
}

private fun canPair(opener: DelimiterRun, closer: DelimiterRun): Boolean {
    if (opener.char != closer.char || opener.scope != closer.scope) return false
    if (!opener.canOpen || !opener.opensAtAll || opener.remaining == 0) return false

    if (closer.char == '~') {
        return opener.remaining == closer.remaining && closer.remaining <= 2
    }

    // CommonMark's rule of 3: a run that can both open and close doesn't pair when the lengths sum to a multiple of 3.
    val bothSides = opener.canClose || closer.canOpen
    val multipleOfThree = (opener.length + closer.length) % 3 == 0
    val bothMultiples = opener.length % 3 == 0 && closer.length % 3 == 0
    return !(bothSides && multipleOfThree && !bothMultiples)
}
