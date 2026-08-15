package com.crosspaste.paste.item

/**
 * Platform-specific reader for extracting parsed content from PasteItems.
 *
 * PasteItem stores raw data (HTML markup, RTF markup, etc.).
 * PasteItemReader provides platform-dependent parsing to extract
 * human-readable text, search content, summaries, and preview HTML.
 *
 * Each platform implements this interface with its own parsing tools:
 * - Desktop: ksoup for HTML, RtfUtils for RTF
 * - JS/Web: browser DOM API
 * - Mobile: shared with Desktop or custom implementation
 */
interface PasteItemReader {

    /**
     * Extract plain text from a PasteItem.
     * For HTML/RTF items, parses markup to return readable text.
     * For Text/URL/Color items, returns the raw value directly.
     */
    fun getText(pasteItem: PasteItem): String

    /**
     * Get search-friendly content from a PasteItem.
     * For HTML/RTF items, extracts and lowercases plain text.
     * For other types, returns the natural search representation.
     */
    fun getSearchContent(pasteItem: PasteItem): String?

    /**
     * Get a human-readable summary of a PasteItem.
     */
    fun getSummary(pasteItem: PasteItem): String

    /**
     * Get the content exactly as stored, without any parsing.
     * For HTML/RTF items, returns the source markup; every other type has no
     * richer representation than its summary, so it falls back to [getSummary].
     * Reading the stored fields needs no platform parsing, hence the default
     * implementation.
     */
    fun getRaw(pasteItem: PasteItem): String =
        when (pasteItem) {
            is HtmlPasteItem -> pasteItem.html
            is RtfPasteItem -> pasteItem.rtf
            else -> getSummary(pasteItem)
        }

    /**
     * Get truncated HTML suitable for rich-text preview rendering.
     * Returns null for non-HTML/RTF types.
     */
    fun getPreviewHtml(pasteItem: PasteItem): String?
}
