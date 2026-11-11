package com.urbanairship.android.layout.util

/**
 * A run of identical `*`, `_` or `~` markers outside any HTML tag.
 *
 * @property scope Which span's text the run sits in (a link, color, superscript, subscript or
 *   highlight, the innermost one), or 0 outside every span.
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

/**
 * Returns whether a code point counts as punctuation for flanking: Unicode punctuation and symbols,
 * and escape stand-ins.
 *
 * @receiver A code point, or `null` past either end of the text.
 * @return `true` for punctuation.
 */
private fun Int?.isFlankPunctuation(): Boolean {
    if (this == null) return false
    if (this in ESCAPED_CHAR_RANGE) return true
    return when (Character.getType(this).toByte()) {
        Character.CONNECTOR_PUNCTUATION,
        Character.DASH_PUNCTUATION,
        Character.START_PUNCTUATION,
        Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION,
        Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION,
        Character.MATH_SYMBOL,
        Character.CURRENCY_SYMBOL,
        Character.MODIFIER_SYMBOL,
        Character.OTHER_SYMBOL -> true
        else -> false
    }
}

/**
 * Returns whether a code point counts as whitespace for flanking: Unicode whitespace and space
 * separators, or either end of the text.
 *
 * @receiver A code point, or `null` past either end of the text.
 * @return `true` for whitespace.
 */
private fun Int?.isFlankWhitespace(): Boolean =
    this == null || Character.isWhitespace(this) || Character.isSpaceChar(this)

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

/** Tags whose text emphasis can't pair across: a marker inside only pairs with one inside. */
private val scopeTagNames = setOf("a", "font", "sup", "sub", HIGHLIGHT_TAG)

private fun String.findDelimiterRuns(): List<DelimiterRun> {
    val runs = mutableListOf<DelimiterRun>()
    val scopes = ArrayDeque<Int>()
    var nextScope = 1
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c == '<' -> {
                val close = indexOf('>', i)
                val tagEnd = if (close < 0) length else close + 1
                val tag = substring(i, tagEnd)
                val name = tag.removePrefix("<").removePrefix("/").takeWhile { it.isLetter() }
                if (name in scopeTagNames) {
                    if (tag.startsWith("</")) scopes.removeLastOrNull() else scopes.addLast(nextScope++)
                }
                i = tagEnd
            }
            c == '*' || c == '_' || c == '~' -> {
                var end = i
                while (end < length && this[end] == c) end++
                runs += delimiterRun(i, end, c, scopes.lastOrNull() ?: 0)
                i = end
            }
            else -> i++
        }
    }
    return runs
}

/**
 * Returns the code point a flanking check sees before [index]; a line break reads as whitespace.
 *
 * @param index Where a delimiter run starts.
 * @return The code point, or `null` at the start of the text.
 */
private fun String.flankCharBefore(index: Int): Int? = when {
    startsWith(newlineTag, index - newlineTag.length) -> ' '.code
    index <= 0 -> null
    else -> codePointBefore(index)
}

/**
 * Returns the code point a flanking check sees at [index]; a line break reads as whitespace.
 *
 * @param index Where a delimiter run ends.
 * @return The code point, or `null` at the end of the text.
 */
private fun String.flankCharAfter(index: Int): Int? = when {
    startsWith(newlineTag, index) -> ' '.code
    index >= length -> null
    else -> codePointAt(index)
}

private fun String.delimiterRun(start: Int, end: Int, char: Char, scope: Int): DelimiterRun {
    val before = flankCharBefore(start)
    val after = flankCharAfter(end)
    val leftFlanking = !after.isFlankWhitespace() &&
        (!after.isFlankPunctuation() || before.isFlankWhitespace() || before.isFlankPunctuation())
    val rightFlanking = !before.isFlankWhitespace() &&
        (!before.isFlankPunctuation() || after.isFlankWhitespace() || after.isFlankPunctuation())

    val (canOpen, canClose) = when (char) {
        '_' -> (leftFlanking && (!rightFlanking || before.isFlankPunctuation())) to
            (rightFlanking && (!leftFlanking || after.isFlankPunctuation()))
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
                opener.remaining >= 2 && closer.remaining >= 2 -> 2
                else -> 1
            }
            val (open, close) = when {
                closer.char == '~' -> "<s>" to "</s>"
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
