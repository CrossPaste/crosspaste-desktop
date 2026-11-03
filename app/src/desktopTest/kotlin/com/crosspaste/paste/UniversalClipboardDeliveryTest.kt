package com.crosspaste.paste

import com.crosspaste.paste.MacosPasteboardService.Companion.isDeliveredByUniversalClipboard
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniversalClipboardDeliveryTest {

    private val incoming = pasteData(hash = "same")

    private fun pasteData(
        hash: String,
        pasteType: PasteType = PasteType.TEXT_TYPE,
    ): PasteData =
        PasteData(
            appInstanceId = "peer",
            pasteCollection = PasteCollection(emptyList()),
            pasteType = pasteType.type,
            size = 4,
            hash = hash,
        )

    private fun delivered(
        universalClipboardChangeCount: Int? = 7,
        currentChangeCount: Int = 7,
        currentPaste: PasteData? = pasteData(hash = "same"),
        incoming: PasteData = this.incoming,
    ): Boolean =
        isDeliveredByUniversalClipboard(
            universalClipboardChangeCount = universalClipboardChangeCount,
            currentChangeCount = currentChangeCount,
            currentPaste = { currentPaste },
            incoming = incoming,
        )

    @Test
    fun sameContentStillOnPasteboardFromUniversalClipboardIsDelivered() {
        assertTrue(delivered())
    }

    @Test
    fun noUniversalClipboardContentRecordedIsNotDelivered() {
        assertFalse(delivered(universalClipboardChangeCount = null))
    }

    @Test
    fun pasteboardChangedSinceUniversalClipboardIsNotDelivered() {
        assertFalse(delivered(currentChangeCount = 8))
    }

    @Test
    fun differentContentIsNotDelivered() {
        assertFalse(delivered(currentPaste = pasteData(hash = "other")))
    }

    @Test
    fun sameHashOfAnotherTypeIsNotDelivered() {
        assertFalse(delivered(currentPaste = pasteData(hash = "same", pasteType = PasteType.HTML_TYPE)))
    }

    @Test
    fun missingCurrentPasteIsNotDelivered() {
        assertFalse(delivered(currentPaste = null))
    }

    @Test
    fun emptyHashIsNeverDelivered() {
        assertFalse(delivered(currentPaste = pasteData(hash = ""), incoming = pasteData(hash = "")))
    }
}
