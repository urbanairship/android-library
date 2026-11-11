package com.urbanairship.android.layout.util

import android.graphics.Color
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import org.xml.sax.XMLReader

/** The tag [markdownToHtml] wraps a `==highlight==` in. */
internal const val HIGHLIGHT_TAG = "uahighlight"

/**
 * Turns the [HIGHLIGHT_TAG] tag from [markdownToHtml] into a [BackgroundColorSpan].
 *
 * It is a tag of its own because the framework closes the enclosing strikethrough at the end of any
 * `<span style>`, so `~~==a== b~~` would strike only `a`. The HTML must start with `<body>`: the
 * parser loses the first end tag of a document that opens with an unknown element.
 */
internal object MarkdownTagHandler : Html.TagHandler {
    private class Mark

    override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
        if (tag != HIGHLIGHT_TAG) return

        if (opening) {
            output.setSpan(Mark(), output.length, output.length, Spanned.SPAN_MARK_MARK)
            return
        }

        val mark = output.getSpans(0, output.length, Mark::class.java).lastOrNull() ?: return
        val start = output.getSpanStart(mark)
        output.removeSpan(mark)
        if (start < output.length) {
            output.setSpan(BackgroundColorSpan(Color.TRANSPARENT), start, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
