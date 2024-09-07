package com.crosspaste.platform.windows.html

import java.awt.datatransfer.DataFlavor
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.util.Arrays
import java.util.Locale

internal enum class EHTMLReadMode {
    HTML_READ_ALL,
    HTML_READ_FRAGMENT,
    HTML_READ_SELECTION,
    ;

    companion object {
        fun getEHTMLReadMode(df: DataFlavor): EHTMLReadMode {
            var mode = HTML_READ_SELECTION

            val parameter = df.getParameter("document")

            if ("all" == parameter) {
                mode = HTML_READ_ALL
            } else if ("fragment" == parameter) {
                mode = HTML_READ_FRAGMENT
            }

            return mode
        }
    }
}

internal class HTMLCodec(
    _bytestream: InputStream?,
    _readMode: EHTMLReadMode,
) : InputStream() {
    // //////////////////////////////////
    // decoder instance data and methods:
    private val bufferedStream: BufferedInputStream
    private var descriptionParsed = false
    private var closed = false

    // HTML header mapping:
    private var iHTMLStart: Long = 0 // StartHTML -- shift in array to the first byte after the header
    private var iHTMLEnd: Long = 0 // EndHTML -- shift in array of last byte for HTML syntax analysis
    private var iFragStart: Long = 0 // StartFragment -- shift in array jast after <!--StartFragment-->
    private var iFragEnd: Long = 0 // EndFragment -- shift in array before start <!--EndFragment-->
    private var iSelStart: Long = 0 // StartSelection -- shift in array of the first char in copied selection
    private var iSelEnd: Long = 0 // EndSelection -- shift in array of the last char in copied selection
    private var stBaseURL: String? = null // SourceURL -- base URL for related referenses
    private var stVersion: String? = null // Version -- current supported version

    // Stream reader markers:
    private var iStartOffset: Long = 0
    private var iEndOffset: Long = 0
    private var iReadCount: Long = 0

    private val readMode: EHTMLReadMode

    init {
        bufferedStream = BufferedInputStream(_bytestream, BYTE_BUFFER_LEN)
        readMode = _readMode
    }

    @get:Throws(IOException::class)
    @get:Synchronized
    val baseURL: String?
        get() {
            if (!descriptionParsed) {
                parseDescription()
            }
            return stBaseURL
        }

    @get:Throws(IOException::class)
    @get:Synchronized
    val version: String?
        get() {
            if (!descriptionParsed) {
                parseDescription()
            }
            return stVersion
        }

    /**
     * parseDescription parsing HTML clipboard header as it described in
     * comment to convertToHTMLFormat
     */
    @Throws(IOException::class)
    private fun parseDescription() {
        stBaseURL = null
        stVersion = null

        // initialization of array offset pointers
        // to the same "uninitialized" state.
        iSelStart = -1
        iSelEnd = iSelStart
        iFragStart = iSelEnd
        iFragEnd = iFragStart
        iHTMLStart = iFragEnd
        iHTMLEnd = iHTMLStart

        bufferedStream.mark(BYTE_BUFFER_LEN)
        val astEntries =
            arrayOf(
                // common
                VERSION,
                START_HTML,
                END_HTML,
                START_FRAGMENT,
                END_FRAGMENT,
                // ver 1.0
                START_SELECTION,
                END_SELECTION,
                SOURCE_URL,
            )
        val bufferedReader =
            BufferedReader(
                InputStreamReader(
                    bufferedStream,
                    ENCODING,
                ),
                CHAR_BUFFER_LEN,
            )
        var iHeadSize: Long = 0
        val iCRSize = EOLN.length.toLong()
        val iEntCount = astEntries.size

        var iEntry = 0
        while (iEntry < iEntCount) {
            val stLine = bufferedReader.readLine() ?: break
            // some header entries are optional, but the order is fixed.
            while (iEntry < iEntCount) {
                if (!stLine.startsWith(astEntries[iEntry])) {
                    ++iEntry
                    continue
                }
                iHeadSize += stLine.length + iCRSize
                val stValue: String = stLine.substring(astEntries[iEntry].length).trim { it <= ' ' }
                try {
                    when (iEntry) {
                        0 -> stVersion = stValue
                        1 -> iHTMLStart = stValue.toInt().toLong()
                        2 -> iHTMLEnd = stValue.toInt().toLong()
                        3 -> iFragStart = stValue.toInt().toLong()
                        4 -> iFragEnd = stValue.toInt().toLong()
                        5 -> iSelStart = stValue.toInt().toLong()
                        6 -> iSelEnd = stValue.toInt().toLong()
                        7 -> stBaseURL = stValue
                    }
                } catch (e: NumberFormatException) {
                    throw IOException(FAILURE_MSG + astEntries[iEntry] + " value " + e + INVALID_MSG)
                }
                break
            }
            ++iEntry
        }
        // some entries could absent in HTML header,
        // so we have find they by another way.
        if (-1L == iHTMLStart) iHTMLStart = iHeadSize
        if (-1L == iFragStart) iFragStart = iHTMLStart
        if (-1L == iFragEnd) iFragEnd = iHTMLEnd
        if (-1L == iSelStart) iSelStart = iFragStart
        if (-1L == iSelEnd) iSelEnd = iFragEnd

        when (readMode) {
            EHTMLReadMode.HTML_READ_ALL -> {
                iStartOffset = iHTMLStart
                iEndOffset = iHTMLEnd
            }

            EHTMLReadMode.HTML_READ_FRAGMENT -> {
                iStartOffset = iFragStart
                iEndOffset = iFragEnd
            }

            EHTMLReadMode.HTML_READ_SELECTION -> {
                iStartOffset = iSelStart
                iEndOffset = iSelEnd
            }
        }
        bufferedStream.reset()
        if (-1L == iStartOffset) {
            throw IOException(FAILURE_MSG + "invalid HTML format.")
        }

        var curOffset = 0
        while (curOffset < iStartOffset) {
            curOffset += bufferedStream.skip(iStartOffset - curOffset).toInt()
        }

        iReadCount = curOffset.toLong()

        if (iStartOffset != iReadCount) {
            throw IOException(FAILURE_MSG + "Byte stream ends in description.")
        }
        descriptionParsed = true
    }

    @Synchronized
    @Throws(IOException::class)
    override fun read(): Int {
        if (closed) {
            throw IOException("Stream closed")
        }

        if (!descriptionParsed) {
            parseDescription()
        }
        if (-1L != iEndOffset && iReadCount >= iEndOffset) {
            return -1
        }

        val retval = bufferedStream.read()
        if (retval == -1) {
            return -1
        }
        ++iReadCount
        return retval
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        if (!closed) {
            closed = true
            bufferedStream.close()
        }
    }

    companion object {
        // static section
        val ENCODING: String = "UTF-8"

        val VERSION: String = "Version:"
        val START_HTML: String = "StartHTML:"
        val END_HTML: String = "EndHTML:"
        val START_FRAGMENT: String = "StartFragment:"
        val END_FRAGMENT: String = "EndFragment:"
        val START_SELECTION: String = "StartSelection:" // optional
        val END_SELECTION: String = "EndSelection:" // optional

        val START_FRAGMENT_CMT: String = "<!--StartFragment-->"
        val END_FRAGMENT_CMT: String = "<!--EndFragment-->"
        val SOURCE_URL: String = "SourceURL:"
        val DEF_SOURCE_URL: String = "about:blank"

        val EOLN: String = "\r\n"

        private val VERSION_NUM = "1.0"
        private val PADDED_WIDTH = 10

        fun kmpSearch(
            textBytes: ByteArray,
            patternBytes: ByteArray,
        ): Int {
            val lps = computeLPSArray(patternBytes)

            var i = 0 // index for textBytes
            var j = 0 // index for patternBytes
            while (i < textBytes.size) {
                if (patternBytes[j] == textBytes[i]) {
                    i++
                    j++
                }
                if (j == patternBytes.size) {
                    return i - j // Found pattern at index (i - j)
                } else if (i < textBytes.size && patternBytes[j] != textBytes[i]) {
                    if (j != 0) {
                        j = lps[j - 1]
                    } else {
                        i++
                    }
                }
            }
            return -1 // Pattern not found
        }

        // Function to compute the LPS (Longest Prefix which is also Suffix) array
        fun computeLPSArray(patternBytes: ByteArray): IntArray {
            val lps = IntArray(patternBytes.size)
            var length = 0
            var i = 1
            lps[0] = 0 // lps[0] is always 0

            while (i < patternBytes.size) {
                if (patternBytes[i] == patternBytes[length]) {
                    length++
                    lps[i] = length
                    i++
                } else {
                    if (length != 0) {
                        length = lps[length - 1]
                    } else {
                        lps[i] = 0
                        i++
                    }
                }
            }
            return lps
        }

        private fun toPaddedString(
            n: Int,
            width: Int,
        ): String {
            var string = "" + n
            val len = string.length
            if (n >= 0 && len < width) {
                val array = CharArray(width - len)
                Arrays.fill(array, '0')
                string = String(array) + string
            }
            return string
        }

        fun replaceFromIndex(
            input: String,
            index: Int,
            oldValue: String,
            newValue: String,
        ): String {
            // Check if the index is within the string's range
            if (index !in 0..input.length) {
                throw IllegalArgumentException("Index out of bounds")
            }

            // Extract the substring from the given index to the end of the string
            val substring = input.substring(index)

            // Perform the replacement in the extracted substring
            val replacedSubstring = substring.replace(oldValue, newValue)

            // Concatenate the original part of the string (before the index) with the replaced substring
            return input.substring(0, index) + replacedSubstring
        }

        /**
         * convertToHTMLFormat adds the MS HTML clipboard header to byte array that
         * contains the parameters pairs.
         *
         * The consequence of parameters is fixed, but some or all of them could be
         * omitted. One parameter per one text line.
         * It looks like that:
         *
         * Version:1.0\r\n                -- current supported version
         * StartHTML:000000192\r\n        -- shift in array to the first byte after the header
         * EndHTML:000000757\r\n          -- shift in array of last byte for HTML syntax analysis
         * StartFragment:000000396\r\n    -- shift in array jast after
         * EndFragment:000000694\r\n      -- shift in array before start
         * StartSelection:000000398\r\n   -- shift in array of the first char in copied selection
         * EndSelection:000000692\r\n     -- shift in array of the last char in copied selection
         * SourceURL:http://sun.com/\r\n  -- base URL for related referenses
         * <HTML>...<BODY>...........................</BODY><HTML>
         * ^                                     ^ ^                ^^                                 ^
         * \ StartHTML                           | \-StartSelection | \EndFragment              EndHTML/
         * \-StartFragment    \EndSelection
         *
         * Combinations with tags sequence
         * <HTML>...<BODY>...</BODY><HTML>
         * or
         * <HTML>......<BODY>...</BODY><HTML>
         * are vailid too.
         </HTML></HTML></HTML></HTML></HTML></HTML> */
        fun convertToHTMLFormat(html: String): ByteArray {
            // Calculate section offsets
            var htmlPrefix = ""
            var htmlSuffix = ""

            // we have extend the fragment to full HTML document correctly
            // to avoid HTML and BODY tags doubling
            var stContext: String = html
            val stUpContext: String = stContext.uppercase(Locale.getDefault())

            val htmlIndex = stUpContext.indexOf("<HTML")
            if (-1 == htmlIndex) {
                htmlPrefix = "<HTML>"
                htmlSuffix = "</HTML>"
                if (-1 == stUpContext.indexOf("<BODY")) {
                    htmlPrefix = htmlPrefix + "<BODY>"
                    htmlSuffix = "</BODY>" + htmlSuffix
                }
            } else {
                // Eliminate line breaks in html to avoid lag in word copy
                stContext = replaceFromIndex(stContext, htmlIndex, "\n", "")
            }

            val bytes = stContext.toByteArray()

            val searchStartFragment = kmpSearch(bytes, START_FRAGMENT_CMT.toByteArray())

            val searchEndFragment = kmpSearch(bytes, END_FRAGMENT_CMT.toByteArray())

            val stBaseUrl = DEF_SOURCE_URL
            val nStartHTML =
                (
                    VERSION.length + VERSION_NUM.length + EOLN.length +
                        START_HTML.length + PADDED_WIDTH + EOLN.length +
                        END_HTML.length + PADDED_WIDTH + EOLN.length +
                        START_FRAGMENT.length + PADDED_WIDTH + EOLN.length +
                        END_FRAGMENT.length + PADDED_WIDTH + EOLN.length +
                        SOURCE_URL.length + stBaseUrl.length + EOLN.length
                )

            val startFragment =
                if (searchStartFragment > 0) {
                    searchStartFragment + START_FRAGMENT_CMT.length
                } else {
                    0
                }

            val nStartFragment = nStartHTML + htmlPrefix.length + startFragment
            val nEndFragment =
                if (searchEndFragment > 0) {
                    nStartHTML + htmlPrefix.length + searchEndFragment
                } else {
                    nStartFragment + bytes.size - 1
                }

            val nEndHTML = nEndFragment + htmlSuffix.length

            val header =
                StringBuilder(
                    nStartFragment +
                        START_FRAGMENT_CMT.length,
                )
            // header
            header.append(VERSION)
            header.append(VERSION_NUM)
            header.append(EOLN)

            header.append(START_HTML)
            header.append(toPaddedString(nStartHTML, PADDED_WIDTH))
            header.append(EOLN)

            header.append(END_HTML)
            header.append(toPaddedString(nEndHTML, PADDED_WIDTH))
            header.append(EOLN)

            header.append(START_FRAGMENT)
            header.append(toPaddedString(nStartFragment, PADDED_WIDTH))
            header.append(EOLN)

            header.append(END_FRAGMENT)
            header.append(toPaddedString(nEndFragment, PADDED_WIDTH))
            header.append(EOLN)

            header.append(SOURCE_URL)
            header.append(stBaseUrl)
            header.append(EOLN)

            // HTML
            header.append(htmlPrefix)

            var headerBytes: ByteArray? = null
            var trailerBytes: ByteArray? = null

            try {
                headerBytes = header.toString().toByteArray(charset(ENCODING))
                trailerBytes = htmlSuffix.toByteArray(charset(ENCODING))
            } catch (cannotHappen: UnsupportedEncodingException) {
            }

            val retval =
                ByteArray(
                    (
                        headerBytes!!.size + bytes.size +
                            trailerBytes!!.size
                    ),
                )

            System.arraycopy(headerBytes, 0, retval, 0, headerBytes.size)
            System.arraycopy(
                bytes,
                0,
                retval,
                headerBytes.size,
                bytes.size - 1,
            )
            System.arraycopy(
                trailerBytes,
                0,
                retval,
                headerBytes.size + bytes.size - 1,
                trailerBytes.size,
            )
            retval[retval.size - 1] = 0

            return retval
        }

        // InputStreamReader uses an 8K buffer. The size is not customizable.
        val BYTE_BUFFER_LEN: Int = 8192

        // CharToByteUTF8.getMaxBytesPerChar returns 3, so we should not buffer
        // more chars than 3 times the number of bytes we can buffer.
        val CHAR_BUFFER_LEN: Int = BYTE_BUFFER_LEN / 3

        private val FAILURE_MSG = "Unable to parse HTML description: "
        private val INVALID_MSG = " invalid"
    }
}
