package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.ColorConversion.toRGBString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.fail

/**
 * Checks that the hand-written HtmlColorUtils matches the former ph-css based
 * implementation ([PhCssHtmlColorReference]) on a corpus of clipboard HTML.
 *
 * Intentional differences (each covered in [HtmlColorUtilsTest]): #RGBA / #RRGGBBAA are
 * recognized where ph-css returns no color; an unparseable `bgcolor` no longer short-circuits
 * the search for an ancestor background; and CSS 2.1 system colors (`Canvas`, `ButtonFace`,
 * ...) count as unrecognized instead of resolving to transparent.
 */
class HtmlColorUtilsDifferentialTest {

    private val styles =
        listOf(
            // plain colors
            "background-color: #fff",
            "background-color: #FFFFFF",
            "background-color: white",
            "background-color: WhiteSmoke",
            "background-color: transparent",
            "background-color: rgb(1, 2, 3)",
            "background-color: rgb(10%, 50%, 100%)",
            "background-color: RGB(128, 128, 128)",
            "background-color: rgba(25, 25, 28, 0.05)",
            "background-color: rgba(25, 25, 28, 50%)",
            "background-color: hsl(120, 100%, 50%)",
            "background-color: hsla(240, 100%, 50%, 0.5)",
            "background-color: red !important",
            "background-color:red",
            " background-color : blue ; ",
            // background shorthand
            "background: #FFF",
            "background: transparent",
            "background: red url(a.png) no-repeat",
            "background: url(a.png) no-repeat center / cover #00ff00",
            "background: linear-gradient(to right, red, blue)",
            "background: linear-gradient(90deg, rgb(1, 2, 3) 0%, white 100%)",
            "background: radial-gradient(circle, #123456, #654321)",
            "background: none",
            // multiple layers: the top-most layer wins
            "background: linear-gradient(red, blue), #fff",
            "background: linear-gradient(red, blue), linear-gradient(white, black)",
            "background: #fff, linear-gradient(red, blue)",
            "background: url(a.png), #fff",
            // !important, with and without a separating space
            "background-color:red!important",
            "background:red!important",
            "background: url(a.png) red!important",
            "background: linear-gradient(red, blue)!important",
            "background-color: linear-gradient(red, blue)",
            // properties around and precedence
            "color: black; font-size: 14px",
            "color: rgb(31, 35, 40); font-size: 32px; background-color: rgb(240, 240, 240)",
            "background: red; background-color: blue",
            "BACKGROUND-COLOR: RGB(128, 128, 128)",
            "background-image: url(a.png)",
            // malformed
            "background-color: rgb(255, 255, 255",
            "background-color:",
            "background-color: notacolor",
            ";;;",
            // VS Code
            "color: #d4d4d4;background-color: #1e1e1e;font-family: Menlo, Monaco, 'Courier New', monospace;" +
                "font-weight: normal;font-size: 12px;line-height: 18px;white-space: pre;",
            // GitHub
            "box-sizing: border-box; font-family: ui-monospace, SFMono-Regular, monospace; font-size: 13.6px; " +
                "padding: 0.2em 0.4em; margin: 0px; white-space: break-spaces; " +
                "background-color: rgba(129, 139, 152, 0.12); border-radius: 6px;",
        )

    private val documents =
        styles.map { """<div style="$it">Test</div>""" } +
            listOf(
                // Chrome copy fragment
                """<meta charset='utf-8'><span style="color: rgb(0, 0, 0); background-color: rgb(255, 255, 255);">x</span>""",
                // Word
                """<html><body lang=ZH-CN style='tab-interval:21.0pt;word-wrap:break-word'><div class=WordSection1>""" +
                    """<p class=MsoNormal style='background:yellow'>x</p></div></body></html>""",
                // Google Docs
                """<meta charset="utf-8"><b style="font-weight:normal;" id="docs-internal-guid-1">""" +
                    """<span style="background-color:transparent;">x</span></b>""",
                // tables with bgcolor
                """<table bgcolor="#336699"><tr><td>x</td></tr></table>""",
                """<table bgcolor="red"><tr><td>x</td></tr></table>""",
                """<body bgcolor="#000000"><p>x</p></body>""",
                // nesting and main container selection
                """<div style="background-color: rgb(100, 100, 100);"><span style="background-color: rgb(200, 200, 200);">x</span></div>""",
                """<p>a</p><main style="background: #222">b</main>""",
                """<p>a</p><div id="app" style="background-color: hsl(0, 100%, 50%)">b</div>""",
                """<p>a</p><div style="width: 100%; background: navy">b</div>""",
                """<html style="background: #eee"><body><p>x</p><p>y</p></body></html>""",
                """<script>var a</script><div style="background: green">x</div>""",
                // no color at all
                """<p>plain</p>""",
                "",
            )

    @Test
    fun `matches the ph-css implementation on the corpus`() {
        val mismatches =
            documents.mapNotNull { html ->
                val expected = PhCssHtmlColorReference.getBackgroundColor(html)
                val actual = HtmlColorUtils.getBackgroundColor(html)
                if (sameColor(expected, actual)) {
                    null
                } else {
                    "$html\n    ph-css: ${expected?.toRGBString()}\n    actual: ${actual?.toRGBString()}"
                }
            }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} mismatches:\n" + mismatches.joinToString("\n"))
        }
    }

    // ph-css truncates channels while ColorParser rounds, so allow one step of difference
    private fun sameColor(
        a: Color?,
        b: Color?,
    ): Boolean {
        if (a == null || b == null) return a == b
        val tolerance = 1.5f / 255f
        return abs(a.red - b.red) <= tolerance &&
            abs(a.green - b.green) <= tolerance &&
            abs(a.blue - b.blue) <= tolerance &&
            abs(a.alpha - b.alpha) <= tolerance
    }
}
