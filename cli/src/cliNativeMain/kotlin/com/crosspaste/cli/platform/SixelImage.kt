package com.crosspaste.cli.platform

/**
 * Sixel encoding (design: ai/design/cli-sixel-support.md, issue #4845).
 *
 * Unlike the iTerm/kitty writers in TerminalImage.kt — which pass the stored
 * image file through for the terminal to decode — sixel requires the sender
 * to hold decoded pixels. The CLI receives exact-display-size straight-alpha
 * RGBA8888 from the app's transcode endpoint and only quantizes + encodes
 * here: pure math over raw pixels, still no image-format decoding in the CLI.
 */

internal const val SIXEL_MAX_COLORS = 256

/** Pixels below this alpha are left unpainted (DCS P2=1 keeps background). */
internal const val SIXEL_OPAQUE_ALPHA_THRESHOLD = 128

internal class QuantizedSixelImage(
    val width: Int,
    val height: Int,
    /** Palette index per pixel in row-major order, or -1 for transparent. */
    val indices: IntArray,
    /** Packed 0xRRGGBB palette colors, at most [SIXEL_MAX_COLORS] entries. */
    val palette: IntArray,
)

/**
 * Quantizes straight-alpha RGBA8888 to a sixel-ready indexed image. Images
 * with at most [SIXEL_MAX_COLORS] distinct opaque colors keep them exactly,
 * in first-seen order (deterministic output for tests); larger color counts
 * go through median-cut.
 */
internal fun quantizeForSixel(
    rgba: ByteArray,
    width: Int,
    height: Int,
): QuantizedSixelImage {
    val pixelCount = width * height
    require(rgba.size >= pixelCount * 4) {
        "rgba buffer holds ${rgba.size} bytes, need ${pixelCount * 4} for ${width}x$height"
    }
    val packed = IntArray(pixelCount)
    val colorToIndex = HashMap<Int, Int>()
    val distinct = ArrayList<Int>()
    for (i in 0 until pixelCount) {
        val o = i * 4
        val alpha = rgba[o + 3].toInt() and 0xFF
        if (alpha < SIXEL_OPAQUE_ALPHA_THRESHOLD) {
            packed[i] = -1
            continue
        }
        val color =
            ((rgba[o].toInt() and 0xFF) shl 16) or
                ((rgba[o + 1].toInt() and 0xFF) shl 8) or
                (rgba[o + 2].toInt() and 0xFF)
        packed[i] = color
        if (colorToIndex[color] == null) {
            colorToIndex[color] = distinct.size
            distinct.add(color)
        }
    }
    val palette: IntArray
    val mapping: HashMap<Int, Int>
    if (distinct.size <= SIXEL_MAX_COLORS) {
        palette = distinct.toIntArray()
        mapping = colorToIndex
    } else {
        val counts = IntArray(distinct.size)
        for (i in 0 until pixelCount) {
            val color = packed[i]
            if (color >= 0) counts[colorToIndex.getValue(color)]++
        }
        val cut = medianCutPalette(distinct.toIntArray(), counts)
        palette = cut.palette
        mapping = cut.colorToIndex
    }
    val indices = IntArray(pixelCount)
    for (i in 0 until pixelCount) {
        val color = packed[i]
        indices[i] = if (color < 0) -1 else mapping.getValue(color)
    }
    return QuantizedSixelImage(width, height, indices, palette)
}

/**
 * Encodes RGBA8888 pixels as a sixel escape sequence. The pixels must already
 * be at final display size — the CLI never scales (the app endpoint returns
 * exact dimensions). Output is streamed through [write] one band at a time so
 * the full escape string is never held in memory.
 */
internal fun writeSixelInlineImage(
    rgba: ByteArray,
    width: Int,
    height: Int,
    write: (String) -> Unit,
) {
    if (width <= 0 || height <= 0) return
    writeSixel(quantizeForSixel(rgba, width, height), write)
}

internal fun writeSixel(
    image: QuantizedSixelImage,
    write: (String) -> Unit,
) {
    val width = image.width
    val sb = StringBuilder()
    // DCS q with P2=1: unpainted pixels keep the terminal background, which
    // is how alpha-transparent regions stay transparent.
    sb.append(TERM_ESC).append("P0;1;0q")
    // Raster attributes: 1:1 pixel aspect plus the image size hint
    sb
        .append('"')
        .append("1;1;")
        .append(width)
        .append(';')
        .append(image.height)
    for ((i, color) in image.palette.withIndex()) {
        sb
            .append('#')
            .append(i)
            .append(";2;")
            .append(sixelChannel(color shr 16))
            .append(';')
            .append(sixelChannel(color shr 8))
            .append(';')
            .append(sixelChannel(color))
    }
    write(sb.toString())
    sb.setLength(0)

    if (image.palette.isNotEmpty()) {
        // Per-band column bitmasks, one row of [width] slots per palette
        // color; only slots of colors seen in the band are written and reset.
        val masks = IntArray(image.palette.size * width)
        val inBand = BooleanArray(image.palette.size)
        val bandColors = ArrayList<Int>()
        var bandTop = 0
        while (bandTop < image.height) {
            val bandRows = minOf(6, image.height - bandTop)
            for (r in 0 until bandRows) {
                val bit = 1 shl r
                val rowBase = (bandTop + r) * width
                for (x in 0 until width) {
                    val ci = image.indices[rowBase + x]
                    if (ci < 0) continue
                    val slot = ci * width + x
                    masks[slot] = masks[slot] or bit
                    if (!inBand[ci]) {
                        inBand[ci] = true
                        bandColors.add(ci)
                    }
                }
            }
            bandColors.sort()
            for ((k, ci) in bandColors.withIndex()) {
                // '$' returns to the band's first column for the next plane
                if (k > 0) sb.append('$')
                sb.append('#').append(ci)
                appendSixelRuns(sb, masks, ci * width, width)
            }
            bandTop += 6
            if (bandTop < image.height) sb.append('-')
            if (sb.isNotEmpty()) {
                write(sb.toString())
                sb.setLength(0)
            }
            for (ci in bandColors) {
                masks.fill(0, ci * width, ci * width + width)
                inBand[ci] = false
            }
            bandColors.clear()
        }
    }
    write("$TERM_ESC\\")
}

/** Sixel palette channels use a 0–100 scale. */
private fun sixelChannel(value: Int): Int = ((value and 0xFF) * 100 + 127) / 255

/**
 * Emits one color plane: 6-bit column masks as chars 63..126, run-length
 * encoded with `!n` for runs of 4+ (shorter runs are cheaper spelled out).
 * Trailing empty columns are trimmed — the terminal treats them as unpainted.
 */
private fun appendSixelRuns(
    sb: StringBuilder,
    masks: IntArray,
    offset: Int,
    width: Int,
) {
    var last = width - 1
    while (last >= 0 && masks[offset + last] == 0) last--
    var x = 0
    while (x <= last) {
        val mask = masks[offset + x]
        var run = 1
        while (x + run <= last && masks[offset + x + run] == mask) run++
        val ch = (63 + mask).toChar()
        if (run >= 4) {
            sb.append('!').append(run).append(ch)
        } else {
            repeat(run) { sb.append(ch) }
        }
        x += run
    }
}

internal class MedianCutResult(
    val palette: IntArray,
    val colorToIndex: HashMap<Int, Int>,
)

/**
 * Entry layout (Long): bits 0..23 packed RGB color, bits 24..47 saturated
 * pixel count, bits 48..55 the current split channel value. Sorting a segment
 * after stamping bits 48..55 orders it by that channel; ties resolve by count
 * then color, so the result is deterministic without a stable sort.
 */
private const val ENTRY_COLOR_MASK = 0xFFFFFFL
private const val ENTRY_COUNT_SHIFT = 24
private const val ENTRY_COUNT_MAX = 0xFFFFFF
private const val ENTRY_KEY_SHIFT = 48
private const val ENTRY_LOW_MASK = 0xFFFFFFFFFFFFL

private fun entryColor(entry: Long): Int = (entry and ENTRY_COLOR_MASK).toInt()

private fun entryCount(entry: Long): Long = (entry ushr ENTRY_COUNT_SHIFT) and ENTRY_COLOR_MASK

private class SixelColorBox(
    val start: Int,
    val end: Int,
    val total: Long,
    val widestChannelShift: Int,
    val splittable: Boolean,
)

private fun makeBox(
    entries: LongArray,
    start: Int,
    end: Int,
): SixelColorBox {
    var total = 0L
    var rMin = 255
    var rMax = 0
    var gMin = 255
    var gMax = 0
    var bMin = 255
    var bMax = 0
    for (i in start until end) {
        val color = entryColor(entries[i])
        total += entryCount(entries[i])
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        if (r < rMin) rMin = r
        if (r > rMax) rMax = r
        if (g < gMin) gMin = g
        if (g > gMax) gMax = g
        if (b < bMin) bMin = b
        if (b > bMax) bMax = b
    }
    val rRange = rMax - rMin
    val gRange = gMax - gMin
    val bRange = bMax - bMin
    val shift =
        when {
            rRange >= gRange && rRange >= bRange -> 16
            gRange >= bRange -> 8
            else -> 0
        }
    return SixelColorBox(start, end, total, shift, end - start > 1)
}

/**
 * Median-cut quantization: repeatedly splits the most populous splittable box
 * along its widest RGB axis at the count-weighted median, until [maxColors]
 * boxes exist. Each box's representative is its count-weighted average color.
 * [maxColors] is a parameter only for tests; production always passes 256.
 */
internal fun medianCutPalette(
    distinctColors: IntArray,
    counts: IntArray,
    maxColors: Int = SIXEL_MAX_COLORS,
): MedianCutResult {
    val n = distinctColors.size
    val entries = LongArray(n)
    for (i in 0 until n) {
        val count = minOf(counts[i], ENTRY_COUNT_MAX)
        entries[i] = (count.toLong() shl ENTRY_COUNT_SHIFT) or distinctColors[i].toLong()
    }
    val boxes = ArrayList<SixelColorBox>()
    boxes.add(makeBox(entries, 0, n))
    while (boxes.size < maxColors) {
        var pick = -1
        var pickTotal = -1L
        for ((i, box) in boxes.withIndex()) {
            if (box.splittable && box.total > pickTotal) {
                pick = i
                pickTotal = box.total
            }
        }
        if (pick < 0) break
        val box = boxes[pick]
        for (i in box.start until box.end) {
            val channel = ((entryColor(entries[i]) shr box.widestChannelShift) and 0xFF).toLong()
            entries[i] = (entries[i] and ENTRY_LOW_MASK) or (channel shl ENTRY_KEY_SHIFT)
        }
        entries.sort(box.start, box.end)
        val half = box.total / 2
        var acc = 0L
        var cut = box.start
        while (cut < box.end - 1) {
            acc += entryCount(entries[cut])
            cut++
            if (acc >= half) break
        }
        boxes[pick] = makeBox(entries, box.start, cut)
        boxes.add(makeBox(entries, cut, box.end))
    }
    val palette = IntArray(boxes.size)
    val colorToIndex = HashMap<Int, Int>(n * 2)
    for ((boxIndex, box) in boxes.withIndex()) {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        for (i in box.start until box.end) {
            val entry = entries[i]
            val color = entryColor(entry)
            val weight = entryCount(entry)
            rSum += weight * ((color shr 16) and 0xFF)
            gSum += weight * ((color shr 8) and 0xFF)
            bSum += weight * (color and 0xFF)
            count += weight
            colorToIndex[color] = boxIndex
        }
        val r = ((rSum + count / 2) / count).toInt()
        val g = ((gSum + count / 2) / count).toInt()
        val b = ((bSum + count / 2) / count).toInt()
        palette[boxIndex] = (r shl 16) or (g shl 8) or b
    }
    return MedianCutResult(palette, colorToIndex)
}
