package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlUtilsTest {

    private val htmlUtils = getHtmlUtils()

    @Test
    fun `getHtmlText drops pretty-print whitespace before the body content`() {
        val html =
            """
            <html>
             <head>
              <meta charset="UTF-8">
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
             </head>
             <body>
              <strong>免费核心功能</strong>：安装后即可使用。
             </body>
            </html>
            """.trimIndent()

        assertEquals("免费核心功能：安装后即可使用。", htmlUtils.getHtmlText(html))
    }

    @Test
    fun `getHtmlText ignores the document title`() {
        val html = "<html><head><title>Page title</title></head><body>body text</body></html>"

        assertEquals("body text", htmlUtils.getHtmlText(html))
    }

    @Test
    fun `getHtmlText keeps br and p as line breaks`() {
        assertEquals("a\nb", htmlUtils.getHtmlText("<p>a</p><p>b</p>"))
        assertEquals("a\nb", htmlUtils.getHtmlText("a<br>b"))
    }

    @Test
    fun `getHtmlText round-trips a document produced by ensureHtmlCharsetUtf8`() {
        val stored = htmlUtils.ensureHtmlCharsetUtf8("<p>Pro 高级功能免费试用。</p>")

        assertEquals("Pro 高级功能免费试用。", htmlUtils.getHtmlText(stored))
    }

    @Test
    fun `getHtmlText returns plain text without HTML entities`() {
        assertEquals("a < b & c > d", htmlUtils.getHtmlText("a &lt; b &amp; c &gt; <b>d</b>"))
        assertEquals("if (a < b) {}", htmlUtils.getHtmlText("<pre>if (a < b) {}</pre>"))
    }
}
