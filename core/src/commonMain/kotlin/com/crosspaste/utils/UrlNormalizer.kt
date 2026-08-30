package com.crosspaste.utils

/**
 * Normalizes a URL for storage and opening. URL validators (JVM `java.net.URL`)
 * tolerate surrounding whitespace, but URL openers do not: `URI` throws
 * `URISyntaxException` on desktop and Android resolves no Activity for the
 * VIEW intent. Every path that stores or opens a URL must go through this.
 */
fun normalizeUrl(url: String): String = url.trim()
