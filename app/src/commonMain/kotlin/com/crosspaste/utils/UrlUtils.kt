package com.crosspaste.utils

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints

expect fun getUrlUtils(): UrlUtils

interface UrlUtils {

    fun isValidUrl(string: String): Boolean

    fun createMiddleEllipsisText(
        url: String,
        maxLines: Int,
        textMeasurer: TextMeasurer,
        constraints: Constraints,
        style: androidx.compose.ui.text.TextStyle,
        ellipsisPosition: Float = 0.6f,
    ): String {
        val text = removeUrlScheme(url)
        // First, measure the full text
        val fullTextLayout =
            textMeasurer.measure(
                text = text,
                style = style,
                constraints = constraints,
                maxLines = maxLines,
            )

        // If the text does not overflow, return it as-is
        if (!fullTextLayout.hasVisualOverflow) {
            return text
        }

        // Get the index of the last visible character
        val lastVisibleCharIndex =
            fullTextLayout.getLineEnd(
                lineIndex = minOf(fullTextLayout.lineCount - 1, maxLines - 1),
                visibleEnd = true,
            )

        // If all text is visible, return it as-is
        if (lastVisibleCharIndex >= text.length - 1) {
            return text
        }

        // Handle single-line case (simpler logic)
        if (maxLines == 1) {
            return handleSingleLineEllipsis(
                text = text,
                textMeasurer = textMeasurer,
                constraints = constraints,
                style = style,
                ellipsisPosition = ellipsisPosition,
            )
        }

        // Handle multi-line case, ellipsis applied only on the last line
        return handleMultiLineEllipsis(
            text = text,
            maxLines = maxLines,
            textMeasurer = textMeasurer,
            constraints = constraints,
            style = style,
            ellipsisPosition = ellipsisPosition,
            lastVisibleCharIndex = lastVisibleCharIndex,
        )
    }

    private fun handleSingleLineEllipsis(
        text: String,
        textMeasurer: TextMeasurer,
        constraints: Constraints,
        style: androidx.compose.ui.text.TextStyle,
        ellipsisPosition: Float,
    ): String {
        val ellipsis = "…"

        // Use binary search to find the best-fitting total length
        var left = ellipsis.length
        var right = text.length
        var bestFit = ellipsis

        while (left <= right) {
            val totalLength = (left + right) / 2
            val availableForText = totalLength - ellipsis.length

            if (availableForText <= 0) {
                left = totalLength + 1
                continue
            }

            // Allocate prefix and suffix length based on the ratio
            val prefixLength = (availableForText * ellipsisPosition).toInt().coerceAtLeast(1)
            val suffixLength = (availableForText - prefixLength).coerceAtLeast(0)

            val truncated =
                buildString {
                    append(text.take(prefixLength))
                    append(ellipsis)
                    if (suffixLength > 0 && prefixLength + suffixLength <= text.length) {
                        append(text.takeLast(suffixLength))
                    }
                }

            val layout =
                textMeasurer.measure(
                    text = truncated,
                    style = style,
                    constraints = constraints,
                    maxLines = 1,
                )

            if (!layout.hasVisualOverflow) {
                bestFit = truncated
                left = totalLength + 1
            } else {
                right = totalLength - 1
            }
        }

        return bestFit
    }

    private fun handleMultiLineEllipsis(
        text: String,
        maxLines: Int,
        textMeasurer: TextMeasurer,
        constraints: Constraints,
        style: androidx.compose.ui.text.TextStyle,
        ellipsisPosition: Float,
        lastVisibleCharIndex: Int,
    ): String {
        val ellipsis = "…"

        // For multi-line case, keep the previous lines intact and only add ellipsis on the last line
        // First, find the end position of the second-to-last line
        val secondLastLineLayout =
            textMeasurer.measure(
                text = text,
                style = style,
                constraints = constraints,
                maxLines = maxLines - 1,
            )

        val secondLastLineEndIndex =
            if (secondLastLineLayout.lineCount >= maxLines - 1) {
                secondLastLineLayout.getLineEnd(maxLines - 2, visibleEnd = true)
            } else {
                secondLastLineLayout.getLineEnd(secondLastLineLayout.lineCount - 1, visibleEnd = true)
            }

        // Preserve all previous lines
        val preservedText = text.substring(0, secondLastLineEndIndex)

        // Extract the remaining text for the last line
        val remainingText = text.substring(secondLastLineEndIndex).trim()

        if (remainingText.isEmpty()) {
            return text
        }

        // Use binary search to find the best ellipsis placement in the last line
        var left = ellipsis.length
        var right = remainingText.length + text.length - secondLastLineEndIndex
        var bestFit = preservedText + ellipsis

        while (left <= right) {
            val totalLastLineLength = (left + right) / 2
            val availableForText = totalLastLineLength - ellipsis.length

            if (availableForText <= 0) {
                left = totalLastLineLength + 1
                continue
            }

            // Apply ellipsis in the last line
            val lastLinePrefixLength = (availableForText * ellipsisPosition).toInt().coerceAtLeast(1)
            val lastLineSuffixLength = (availableForText - lastLinePrefixLength).coerceAtLeast(0)

            val truncated =
                buildString {
                    append(preservedText)
                    append(remainingText.take(lastLinePrefixLength))
                    append(ellipsis)
                    if (lastLineSuffixLength > 0) {
                        // Take suffix from the original text end
                        append(text.takeLast(lastLineSuffixLength))
                    }
                }

            val layout =
                textMeasurer.measure(
                    text = truncated,
                    style = style,
                    constraints = constraints,
                    maxLines = maxLines,
                )

            if (!layout.hasVisualOverflow && layout.lineCount <= maxLines) {
                bestFit = truncated
                left = totalLastLineLength + 1
            } else {
                right = totalLastLineLength - 1
            }
        }

        return bestFit
    }

    fun removeUrlScheme(url: String): String {
        val delimiter = "://"
        val index = url.indexOf(delimiter)
        return if (index != -1) {
            url.substring(index + delimiter.length)
        } else {
            url
        }
    }
}
