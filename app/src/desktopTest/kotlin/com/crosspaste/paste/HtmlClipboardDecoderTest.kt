package com.crosspaste.paste

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlClipboardDecoderTest {

    @Test
    fun `decodes utf-8 without declaration`() {
        val html = "<html><body><p>你好，世界 — IDEA</p></body></html>"
        assertEquals(html, HtmlClipboardDecoder.decode(html.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `honors utf-8 bom and strips it`() {
        val html = "<html><body>héllo</body></html>"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + html.toByteArray(Charsets.UTF_8)

        val decoded = HtmlClipboardDecoder.decode(bytes)

        assertEquals(html, decoded)
        assertTrue(decoded.first() == '<', "BOM should be stripped, got U+%04X".format(decoded.first().code))
    }

    @Test
    fun `honors utf-16le bom`() {
        val html = "<html><body><p>Привет, мир</p></body></html>"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + html.toByteArray(Charsets.UTF_16LE)

        assertEquals(html, HtmlClipboardDecoder.decode(bytes))
    }

    @Test
    fun `honors declared meta charset over content detection`() {
        // Real bytes are GBK; the document declares it. Decoding as anything
        // else would corrupt the Chinese text.
        val gbk = Charset.forName("GBK")
        val html = """<html><head><meta charset="gbk"></head><body><p>简体中文内容测试</p></body></html>"""

        val decoded = HtmlClipboardDecoder.decode(html.toByteArray(gbk))

        assertEquals(html, decoded)
    }

    @Test
    fun `honors http-equiv content type charset`() {
        val gbk = Charset.forName("GBK")
        val html =
            "<html><head>" +
                """<meta http-equiv="Content-Type" content="text/html; charset=GBK">""" +
                "</head><body><p>中文乱码测试用例</p></body></html>"

        assertEquals(html, HtmlClipboardDecoder.decode(html.toByteArray(gbk)))
    }

    @Test
    fun `ignores charset literal in body text without a meta tag`() {
        // No declaration at all; the body merely *talks about* charsets. The
        // literal "charset=gbk" must not be mistaken for a declaration, or
        // these UTF-8 bytes would be decoded as GBK and mojibaked.
        val html = "<html><body><p>设置 charset=gbk 会导致乱码，正确做法是使用 UTF-8 编码保存文件。</p></body></html>"

        assertEquals(html, HtmlClipboardDecoder.decode(html.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `honors xml encoding prolog`() {
        val html = """<?xml version="1.0" encoding="ISO-8859-1"?><html><body>café résumé</body></html>"""
        val bytes = html.toByteArray(Charsets.ISO_8859_1)

        assertEquals(html, HtmlClipboardDecoder.decode(bytes))
    }

    @Test
    fun `detects undeclared big5 via content`() {
        val big5 = Charset.forName("Big5")
        // No declaration; enough CJK text for ICU to detect Big5 confidently.
        val html = "<html><body><p>繁體中文內容偵測測試，這是一段較長的文字以便編碼偵測器判斷。</p></body></html>"

        val decoded = HtmlClipboardDecoder.decode(html.toByteArray(big5))

        assertEquals(html, decoded)
    }

    @Test
    fun `decodes known utf-16 charset target (IntelliJ-style)`() {
        // IntelliJ / JBR offers html only as text/html;charset=UTF-16, encoded
        // with a BOM. We know the charset from the target name and pass it in.
        val utf16 = Charset.forName("UTF-16")
        val html = "<html><body><p>你好，世界 — IntelliJ 复制的中文</p></body></html>"

        val decoded = HtmlClipboardDecoder.decode(html.toByteArray(utf16), knownCharset = utf16)

        assertEquals(html, decoded)
    }

    @Test
    fun `in-document meta overrides a lying utf-16 target label`() {
        // IntelliJ / JBR serves UTF-8 bytes for every text/html;charset=* target,
        // including text/html;charset=UTF-16. Trusting that label would re-mojibake
        // the text; the document's own <meta charset=UTF-8> must win.
        val html =
            "<html><head>" +
                """<meta http-equiv="content-type" content="text/html; charset=UTF-8">""" +
                "</head><body><p>你好，世界 — IntelliJ 复制的中文</p></body></html>"
        val utf8Bytes = html.toByteArray(Charsets.UTF_8)

        val decoded = HtmlClipboardDecoder.decode(utf8Bytes, knownCharset = Charset.forName("UTF-16"))

        assertEquals(html, decoded)
    }

    @Test
    fun `decodes known utf-16le charset target without bom`() {
        val utf16le = Charset.forName("UTF-16LE")
        val html = "<html><body><p>简体中文，无 BOM</p></body></html>"

        val decoded = HtmlClipboardDecoder.decode(html.toByteArray(utf16le), knownCharset = utf16le)

        assertEquals(html, decoded)
    }

    @Test
    fun `bom overrides a wrong known charset`() {
        // Bytes are UTF-8 with a BOM; even if a bogus known charset is supplied,
        // the BOM must win so the document is not corrupted.
        val html = "<html><body>café 世界</body></html>"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + html.toByteArray(Charsets.UTF_8)

        val decoded = HtmlClipboardDecoder.decode(bytes, knownCharset = Charset.forName("ISO-8859-1"))

        assertEquals(html, decoded)
    }

    @Test
    fun `returns empty string for empty input`() {
        assertEquals("", HtmlClipboardDecoder.decode(ByteArray(0)))
    }
}
