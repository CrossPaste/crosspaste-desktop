package com.crosspaste.utils

/**
 * Normalizes file names so they are valid on all major desktop OSes (Windows, macOS, Linux).
 *
 * See: https://learn.microsoft.com/windows/win32/fileio/naming-a-file
 */
object FileNameNormalizer {

    /** Windows-reserved device names (case-insensitive). */
    private val WINDOWS_RESERVED_NAMES =
        setOf(
            "CON",
            "PRN",
            "AUX",
            "NUL",
            "COM1",
            "COM2",
            "COM3",
            "COM4",
            "COM5",
            "COM6",
            "COM7",
            "COM8",
            "COM9",
            "LPT1",
            "LPT2",
            "LPT3",
            "LPT4",
            "LPT5",
            "LPT6",
            "LPT7",
            "LPT8",
            "LPT9",
        )

    /** Characters that are illegal on at least one mainstream OS. */
    private val ILLEGAL_CHARS =
        setOf(
            '<',
            '>',
            ':',
            '"',
            '|',
            '?',
            '*',
            '\\',
            '/',
        )

    /** ASCII control characters 0x00-0x1F + 0x7F (DEL). */
    private val CONTROL_CHARS = (0..31).map { it.toChar() }.toSet() + 0x7F.toChar()

    /**
     * Normalize a file name.
     *
     * @param fileName          The raw file name.
     * @param replacement       Replacement character for illegal chars. **Must be a single code point.**
     * @param preserveExtension Whether to keep the file extension (.txt, .png …).
     */
    fun normalize(
        fileName: String,
        replacement: Char = '_',
        preserveExtension: Boolean = true,
    ): String {
        if (fileName.isBlank()) return "unnamed"

        // Split base name / extension (keep the dot in [extension]).
        val lastDotIdx = if (preserveExtension) fileName.lastIndexOf('.') else -1
        val baseName = if (lastDotIdx > 0) fileName.substring(0, lastDotIdx) else fileName
        val extension = if (lastDotIdx > 0) fileName.substring(lastDotIdx) else ""

        // 1) Replace illegal chars in the base name.
        var normalized =
            buildString {
                for (ch in baseName) {
                    append(
                        when {
                            ch in ILLEGAL_CHARS || ch in CONTROL_CHARS || ch.code == 0 -> replacement
                            else -> ch
                        },
                    )
                }
            }

        // 2) Handle Windows-reserved device names.
        if (WINDOWS_RESERVED_NAMES.contains(normalized.uppercase())) {
            normalized += replacement
        }

        // 3) Trim leading / trailing spaces & dots (Windows restriction).
        normalized = normalized.trim(' ', '.')

        // 4) Ensure not empty.
        if (normalized.isEmpty()) normalized = "file"

        // 5) Enforce length limit (UTF-8 bytes). Reserve space for the extension.
        val extBytes = extension.toByteArray(Charsets.UTF_8).size
        val maxBaseNameBytes = maxOf(50, 255 - extBytes)
        normalized = limitLength(normalized, maxBaseNameBytes)

        // 6) Re-attach and normalize extension if requested.
        if (preserveExtension && extension.isNotEmpty()) {
            val normalizedExt =
                buildString {
                    // keep the leading dot untouched
                    append('.')
                    for (ch in extension.drop(1)) {
                        append(
                            when {
                                ch in ILLEGAL_CHARS || ch in CONTROL_CHARS -> replacement
                                else -> ch
                            },
                        )
                    }
                }.trim(' ', '.') // just in case

            return normalized + normalizedExt
        }

        return normalized
    }

    /**
     * Limit string to [maxBytes] in UTF-8, without breaking code points.
     */
    private fun limitLength(
        text: String,
        maxBytes: Int,
    ): String {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        if (utf8.size <= maxBytes) return text

        val result = StringBuilder()
        var byteCount = 0
        for (ch in text) {
            val chBytes = ch.toString().toByteArray(Charsets.UTF_8).size
            if (byteCount + chBytes > maxBytes) break
            result.append(ch)
            byteCount += chBytes
        }
        return result.toString()
    }

    /**
     * Validate whether a file name is portable across major OSes.
     */
    fun isValid(fileName: String): Boolean {
        if (fileName.isBlank()) return false

        // Illegal characters
        if (fileName.any { it in ILLEGAL_CHARS || it in CONTROL_CHARS || it.code == 0 }) return false

        // Windows-reserved device names
        val nameWithoutExt = fileName.substringBefore('.')
        if (WINDOWS_RESERVED_NAMES.contains(nameWithoutExt.uppercase())) return false

        // Leading / trailing space or dot
        if (fileName.startsWith(' ') ||
            fileName.startsWith('.') ||
            fileName.endsWith(' ') ||
            fileName.endsWith('.')
        ) {
            return false
        }

        // Length ≤ 255 bytes (UTF-8)
        if (fileName.toByteArray(Charsets.UTF_8).size > 255) return false

        return true
    }
}
