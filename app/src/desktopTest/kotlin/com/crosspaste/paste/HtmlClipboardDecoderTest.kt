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
    fun `returns empty string for empty input`() {
        assertEquals("", HtmlClipboardDecoder.decode(ByteArray(0)))
    }
}
