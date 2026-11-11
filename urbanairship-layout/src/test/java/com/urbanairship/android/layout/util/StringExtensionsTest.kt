package com.urbanairship.android.layout.util

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric is needed because markdownToHtml uses Android's TextUtils.htmlEncode
@RunWith(RobolectricTestRunner::class)
public class StringExtensionsTest {

    @Test
    public fun ifNotEmpty() {
        var called = false
        "Hello".ifNotEmpty {
            called = true
            assertEquals("Hello", it)
        }
        assertTrue(called)
    }

    @Test
    public fun ifNotEmptyNoOpsOnEmpty() {
        var called = false
        "".ifNotEmpty {
            called = true
        }
        assertFalse(called)
    }

    @Test
    public fun ifNotEmptyNoOpsOnNull() {
        var called = false
        val nullString: String? = null
        nullString.ifNotEmpty {
            called = true
        }
        assertFalse(called)
    }

    @Test
    public fun markdownToHtmlPlainText() {
        val input = "Hello, world!"
        val expected = "Hello, world!"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlLinks() {
        val input = "Check out [this link](https://example.com)"
        val expected = """Check out <a href="https://example.com">this link</a>"""
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlBold() {
        val inputStar = "This is **bold** text"
        val inputBar = "This is __bold__ text"
        val expected = "This is <b>bold</b> text"
        assertEquals(expected, inputStar.markdownToHtml())
        assertEquals(expected, inputBar.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlItalic() {
        val inputStar = "This is *italic* text"
        val inputBar = "This is _italic_ text"
        val expected = "This is <i>italic</i> text"
        assertEquals(expected, inputStar.markdownToHtml())
        assertEquals(expected, inputBar.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlBoldAndItalic() {
        val inputStar = "This is ***bold and italic*** text"
        val inputBar = "This is ___bold and italic___ text"
        val expected = "This is <b><i>bold and italic</i></b> text"
        assertEquals(expected, inputStar.markdownToHtml())
        assertEquals(expected, inputBar.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlStrikethrough() {
        val input = "This is ~~strikethrough~~ text"
        val expected = "This is <s>strikethrough</s> text"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlMultiple() {
        val inputStar = "**Bold**, *italic*, and [link](https://example.com)"
        val inputBar = "__Bold__, _italic_, and [link](https://example.com)"
        val expected = """<b>Bold</b>, <i>italic</i>, and <a href="https://example.com">link</a>"""
        assertEquals(expected, inputStar.markdownToHtml())
        assertEquals(expected, inputBar.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlNested() {
        val inputStar = "***Bold and italic***, **bold with *italic* inside**"
        val inputBar = "___Bold and italic___, __bold with _italic_ inside__"
        val expected = "<b><i>Bold and italic</i></b>, <b>bold with <i>italic</i> inside</b>"
        assertEquals(expected, inputStar.markdownToHtml())
        assertEquals(expected, inputBar.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlMixedSyntax() {
        val input = "**Bold** and _italic_, __bold__ and *italic*"
        val expected = "<b>Bold</b> and <i>italic</i>, <b>bold</b> and <i>italic</i>"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlPreservesWhitespace() {
        val input = "Line 1\nLine 2"
        val inputEscaped = "Line 1\\nLine 2"
        val expected = "Line 1<br>Line 2"
        assertEquals(expected, input.markdownToHtml())
        assertEquals(expected, inputEscaped.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlPreservesSymbols() {
        val input = "This & that < these > those"
        val expected = "This &amp; that &lt; these &gt; those"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlIgnoresUnsupportedSyntax() {
        val input = "# Heading\n- List item"
        val expected = "# Heading<br>- List item"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlEmpty() {
        val input = ""
        val expected = ""
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSnakeCase() {
        val input = "Airship_test_ko"
        val expected = "Airship_test_ko"
        assertEquals(expected, input.markdownToHtml())

        val input2 = "hello_world_test"
        val expected2 = "hello_world_test"
        assertEquals(expected2, input2.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlUnderscoreBoundaries() {
        val input = "_italic_"
        val expected = "<i>italic</i>"
        assertEquals(expected, input.markdownToHtml())

        val input2 = " _italic_ "
        val expected2 = " <i>italic</i> "
        assertEquals(expected2, input2.markdownToHtml())

        val input3 = "(_italic_)"
        val expectedWeb = "(<i>italic</i>)"
        assertEquals(expectedWeb, input3.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlAsteriskIntraword() {
        val input = "un*frigging*believable"
        val expected = "un<i>frigging</i>believable"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlStrikethroughIntraword() {
        val input = "del~~ete~~d"
        val expected = "del<s>ete</s>d"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlHighlightIntraword() {
        val input = "high==light==ed"
        val expected = """high<span style="background-color: #4DFFFF00;">light</span>ed"""
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSuperscript() {
        val input = "E = mc^^2^^"
        val expected = "E = mc<sup>2</sup>"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSubscript() {
        val input = "H,{2},O"
        val expected = "H<sub>2</sub>O"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSuperscriptAndSubscript() {
        val input = "H,{2},O  —  E = mc^^2^^"
        val expected = "H<sub>2</sub>O  —  E = mc<sup>2</sup>"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSuperscriptWithHighlightInside() {
        val input = "mc^^==2==^^"
        val expected = """mc<sup><span style="background-color: #4DFFFF00;">2</span></sup>"""
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSubscriptInsideHighlight() {
        val input = "==H,{2},O=="
        val expected = """<span style="background-color: #4DFFFF00;">H<sub>2</sub>O</span>"""
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSubscriptInsideStrikethrough() {
        val input = "~~H,{2},O~~"
        val expected = "<s>H<sub>2</sub>O</s>"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSuperscriptInsideStrikethrough() {
        val input = "~~mc^^2^^~~"
        val expected = "<s>mc<sup>2</sup></s>"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlHighlightedSubscriptAndSuperscript() {
        val input = "==H,{2},O is highlighted!==  and  ==E = mc^^2^^ too=="
        val expected = """<span style="background-color: #4DFFFF00;">H<sub>2</sub>O is highlighted!</span>  and  <span style="background-color: #4DFFFF00;">E = mc<sup>2</sup> too</span>"""
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlIgnoresUnpairedSuperscriptAndSubscript() {
        val input = "^^open only, ,{open only"
        val expected = "^^open only, ,{open only"
        assertEquals(expected, input.markdownToHtml())
    }

    @Test
    public fun markdownToHtmlLeavesUnmatchedMarkersLiteral() {
        listOf(
            "**unclosed bold",
            "unopened bold**",
            "***unclosed bold italic",
            "*unclosed italic",
            "__unclosed bold",
            "_unclosed italic",
            "~~unclosed strike",
            "==unclosed highlight",
            "^^unclosed sup",
            ",{unclosed sub",
            "unopened sub},",
        ).forEach { input ->
            assertEquals(input, input.markdownToHtml())
        }
    }

    @Test
    public fun markdownToHtmlLeavesEmptyPairsLiteral() {
        listOf("****", "____", "~~~~", "^^^^", ",{},").forEach { input ->
            assertEquals(input, input.markdownToHtml())
        }
    }

    @Test
    public fun markdownToHtmlUnderscoreItalicStillMatchesInsideWords() {
        assertEquals("<i>italic</i>", "_italic_".markdownToHtml())
        assertEquals("<i>a_b</i>", "_a_b_".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSpaceFlankedMarkersStayLiteral() {
        listOf("2 * 3 * 4", "** spaced **", "_ spaced _", "~ spaced ~").forEach { input ->
            assertEquals(input, input.markdownToHtml())
        }
    }

    @Test
    public fun markdownToHtmlIntrawordMarkerNextToLinkStaysLiteral() {
        assertEquals(
            """foo**<a href="u">x</a>**bar""",
            "foo**[x](u)**bar".markdownToHtml()
        )
    }

    @Test
    public fun markdownToHtmlBoldAroundLinkFormatsTheLink() {
        assertEquals(
            """<b><a href="u">x</a></b> after""",
            "**[x](u)** after".markdownToHtml()
        )
    }

    @Test
    public fun markdownToHtmlEscapedMarkerCountsAsPunctuation() {
        assertEquals("<b>*x</b>", "**\\*x**".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlSingleTildeStrikethrough() {
        assertEquals("<s>x</s>", "~x~".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlMarkerRunFormatsOnlyWhatPairs() {
        assertEquals("*<i>a</i>", "**a*".markdownToHtml())
        assertEquals("~~a~", "~~a~".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlMixedMarkersNestWithoutBrokenTags() {
        assertEquals("<i>a <b>b</b></i>", "_a **b**_".markdownToHtml())
        assertEquals("<i>__</i>__", "*__*__".markdownToHtml())
        assertEquals("<b>__</b>__", "**__**__".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlMarkerSplitByRuleOfThreeStaysLiteral() {
        assertEquals("*a**a", "*a**a".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlIgnoresMarkersInsideTags() {
        assertEquals(
            """<a href="https://example.com/a_b_c">x</a>""",
            "[x](https://example.com/a_b_c)".markdownToHtml()
        )
    }

    @Test
    public fun markdownToHtmlEmphasisDoesNotCrossALinksEdge() {
        assertEquals(
            """<a href="u">a **b</a>** c""",
            "[a **b](u)** c".markdownToHtml()
        )
        assertEquals(
            """**a <a href="u">b** c</a>""",
            "**a [b** c](u)".markdownToHtml()
        )
    }

    @Test
    public fun markdownToHtmlEmphasisInsideALinkStaysInsideIt() {
        assertEquals(
            """<b>x</b> <a href="u"><i>a</i> b</a>""",
            "**x** [*a* b](u)".markdownToHtml()
        )
    }

    @Test
    public fun markdownToHtmlUnmatchedMarkerBesideAPair() {
        assertEquals("<b>bold</b> and **open", "**bold** and **open".markdownToHtml())
        assertEquals("<i>italic</i> and **open", "*italic* and **open".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlColorSpans() {
        val input = "[Sale]{color:brand} ends [today]{color:urgent}, so don't miss it"
        val expected = """<span style="color: #000001;">Sale</span> ends <span style="color: #000002;">today</span>, so don&#39;t miss it"""
        assertEquals(expected, input.markdownToHtml(listOf("brand", "urgent")))
    }

    @Test
    public fun markdownToHtmlColorSpanWithUnknownNameKeepsText() {
        assertEquals("Sale ends", "[Sale]{color:missing} ends".markdownToHtml(listOf("brand")))
        assertEquals("Sale ends", "[Sale]{color:brand} ends".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlNestedColorSpans() {
        val input = "[one [two]{color:inner} one]{color:outer}"
        val expected = """<span style="color: #000001;">one <span style="color: #000002;">two</span> one</span>"""
        assertEquals(expected, input.markdownToHtml(listOf("outer", "inner")))
    }

    @Test
    public fun markdownToHtmlColorSpanAroundStylesAndLink() {
        val input = "[**Sale** at the [store](https://example.com) ,{now},]{color:brand}"
        val expected = """<span style="color: #000001;"><b>Sale</b> at the <a href="https://example.com">store</a> <sub>now</sub></span>"""
        assertEquals(expected, input.markdownToHtml(listOf("brand")))
    }

    @Test
    public fun markdownToHtmlColorSpanBeforeLink() {
        val input = "[Sale]{color:brand} at the [store](https://example.com)"
        val expected = """<span style="color: #000001;">Sale</span> at the <a href="https://example.com">store</a>"""
        assertEquals(expected, input.markdownToHtml(listOf("brand")))
    }

    @Test
    public fun markdownToHtmlColorSpanTextWithPercent() {
        val expected = """<span style="color: #000001;">20% off</span>"""
        assertEquals(expected, "[20% off]{color:brand}".markdownToHtml(listOf("brand")))
    }

    @Test
    public fun markdownToHtmlLeavesNonColorSpansLiteral() {
        listOf(
            "[space] {color:brand}",
            "[x]{color:}",
            "[y]{color:two words}",
            "[]{color:brand}",
            "[open]{color:brand",
            "[x]{colour:brand}",
            "plain {color:brand}",
        ).forEach { input ->
            assertEquals(input, input.markdownToHtml(listOf("brand")))
        }
    }

    @Test
    public fun markdownToHtmlBackslashEscapesDrawTheCharacter() {
        assertEquals("*not italic*", """\*not italic\*""".markdownToHtml())
        assertEquals("**not bold**", """\*\*not bold\*\*""".markdownToHtml())
        assertEquals("[docs](https://example.com)", """\[docs](https://example.com)""".markdownToHtml())
        assertEquals("&lt;b&gt; and a \\", """\<b\> and a \\""".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlBackslashBeforeAnythingElseIsLiteral() {
        assertEquals("""C:\Users and \a""", """C:\Users and \a""".markdownToHtml())
        assertEquals("Line 1<br>Line 2", "Line 1\\nLine 2".markdownToHtml())
    }

    @Test
    public fun markdownToHtmlEscapedBracketStillOpensAColorSpan() {
        val input = """[parens](color:brand) and \[escaped]{color:brand}"""
        val expected = """<a href="color:brand">parens</a> and <span style="color: #000001;">escaped</span>"""
        assertEquals(expected, input.markdownToHtml(listOf("brand")))
    }

    @Test
    public fun markdownToHtmlEscapedBracketOutsideASpanIsLiteral() {
        assertEquals("[not a span] here", """\[not a span\] here""".markdownToHtml(listOf("brand")))
    }

    @Test
    public fun colorSpanIndexDecodesPlaceholders() {
        assertEquals(0, colorSpanIndex(0xFF000001.toInt()))
        assertEquals(41, colorSpanIndex(0xFF00002A.toInt()))
        assertEquals(null, colorSpanIndex(0xFF000000.toInt()))
    }

}
