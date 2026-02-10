package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlUtilsTest {

    private val urlUtils = getUrlUtils()

    @Test
    fun `isValidUrl accepts standard HTTP URL`() {
        assertTrue(urlUtils.isValidUrl("http://example.com"))
    }

    @Test
    fun `isValidUrl accepts HTTPS URL`() {
        assertTrue(urlUtils.isValidUrl("https://example.com/path?query=1"))
    }

    @Test
    fun `isValidUrl rejects plain text`() {
        assertFalse(urlUtils.isValidUrl("not a url"))
    }

    @Test
    fun `isValidUrl rejects empty string`() {
        assertFalse(urlUtils.isValidUrl(""))
    }

    @Test
    fun `isValidUrl rejects string with only scheme`() {
        // "http://" alone is not a valid URL
        assertFalse(urlUtils.isValidUrl("http://"))
    }

    @Test
    fun `isValidUrl accepts FTP URL`() {
        assertTrue(urlUtils.isValidUrl("ftp://files.example.com/readme.txt"))
    }

    @Test
    fun `isValidUrl rejects relative path`() {
        assertFalse(urlUtils.isValidUrl("/relative/path"))
    }

    @Test
    fun `removeUrlScheme strips http scheme`() {
        assertEquals("example.com/path", urlUtils.removeUrlScheme("http://example.com/path"))
    }

    @Test
    fun `removeUrlScheme strips https scheme`() {
        assertEquals("example.com", urlUtils.removeUrlScheme("https://example.com"))
    }

    @Test
    fun `removeUrlScheme returns unchanged string without scheme`() {
        assertEquals("example.com", urlUtils.removeUrlScheme("example.com"))
    }

    @Test
    fun `removeUrlScheme handles ftp scheme`() {
        assertEquals("files.example.com/f.txt", urlUtils.removeUrlScheme("ftp://files.example.com/f.txt"))
    }

    @Test
    fun `removeUrlScheme handles string with only separator`() {
        assertEquals("", urlUtils.removeUrlScheme("://"))
    }

    @Test
    fun `removeUrlScheme picks first occurrence of separator`() {
        // "http://host://weird" -> "host://weird"
        assertEquals("host://weird", urlUtils.removeUrlScheme("http://host://weird"))
    }
}
