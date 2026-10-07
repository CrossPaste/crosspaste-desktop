package com.crosspaste.platform.windows.html

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlCodecTest {

    // What AWT does to the String we hand it for CF_HTML ("HTML Format" in
    // flavormap.properties: charset=utf-8, eoln="\r\n", terminators=1)
    private fun awtClipboardBytes(cfHtml: String): ByteArray {
        val sb = StringBuilder()
        var i = 0
        while (i < cfHtml.length) {
            if (cfHtml.startsWith("\r\n", i)) {
                sb.append("\r\n")
                i += 2
                continue
            }
            val c = cfHtml[i]
            sb.append(if (c == '\n') "\r\n" else c.toString())
            i++
        }
        return sb.toString().encodeToByteArray() + 0
    }

    private fun offset(
        bytes: ByteArray,
        key: String,
    ): Int {
        val header = bytes.decodeToString()
        val start = header.indexOf(key) + key.length
        return header.substring(start, header.indexOf("\r\n", start)).toInt()
    }

    private class Sections(
        val html: String,
        val fragment: String,
    )

    private fun writeAndParse(html: String): Sections {
        val bytes = awtClipboardBytes(HTMLCodec.convertToHTMLFormat(html).decodeToString())
        return Sections(
            html = bytes.copyOfRange(offset(bytes, "StartHTML:"), offset(bytes, "EndHTML:")).decodeToString(),
            fragment =
                bytes.copyOfRange(offset(bytes, "StartFragment:"), offset(bytes, "EndFragment:")).decodeToString(),
        )
    }

    @Test
    fun `fragment offsets survive line breaks in a snippet`() {
        val html = "<meta charset='utf-8'><span>first\nsecond\nthird</span>\n<p>last</p>"

        val sections = writeAndParse(html)

        assertEquals(html.replace("\n", "\r\n"), sections.fragment)
        assertEquals("<HTML><BODY>${html.replace("\n", "\r\n")}</BODY></HTML>", sections.html)
    }

    @Test
    fun `fragment keeps a trailing multi-byte character`() {
        val html = "<b>bold</b> 中文"

        assertEquals(html, writeAndParse(html).fragment)
    }

    @Test
    fun `fragment comments bound the fragment of a full document`() {
        val html =
            "<html>\n<body>\n<!--StartFragment--><p>one</p>\n<p>two</p><!--EndFragment-->\n</body>\n</html>"

        val sections = writeAndParse(html)

        assertEquals("<p>one</p><p>two</p>", sections.fragment)
        assertEquals(
            "<html><body><!--StartFragment--><p>one</p><p>two</p><!--EndFragment--></body></html>",
            sections.html,
        )
    }
}
