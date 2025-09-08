package com.crosspaste.paste.plugin.type

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItem.Companion.updateExtraInfo
import com.crosspaste.paste.item.PasteItemProperties.BACKGROUND
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.utils.HtmlColorUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getRtfUtils
import kotlinx.serialization.json.put
import java.awt.datatransfer.DataFlavor
import java.io.InputStream

class DesktopRtfTypePlugin : RtfTypePlugin {

    companion object {
        const val RTF_ID = "text/rtf"

        val RTF_DATA_FLAVOR =
            DataFlavor(
                "text/rtf;  class=java.io.InputStream",
                "Rich Text Format",
            )

        private val codecsUtils = getCodecsUtils()
        private val rtfUtils = getRtfUtils()
    }

    override fun getPasteType(): PasteType = PasteType.RTF_TYPE

    override fun getIdentifiers(): List<String> = listOf(RTF_ID)

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        RtfPasteItem(
            identifiers = listOf(identifier),
            hash = "",
            size = 0,
            rtf = "",
        ).let {
            pasteCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: PasteDataFlavor,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is InputStream) {
            val rtfBytes = transferData.readBytes()
            val hash = codecsUtils.hash(rtfBytes)
            val size = rtfBytes.size.toLong()
            val rtf = rtfBytes.toString(Charsets.UTF_8)
            val html = rtfUtils.rtfToHtml(rtf) ?: return
            val background = HtmlColorUtils.getBackgroundColor(html) ?: Color.Transparent
            val update: (PasteItem) -> PasteItem = { pasteItem ->
                RtfPasteItem(
                    identifiers = pasteItem.identifiers,
                    hash = hash,
                    size = size,
                    rtf = rtf,
                    extraInfo =
                        updateExtraInfo(
                            pasteItem.extraInfo,
                            update = {
                                put(BACKGROUND, background.toArgb())
                            },
                        ),
                )
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        mixedCategory: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as RtfPasteItem
        val currentRtf = pasteItem.rtf
        map[RTF_DATA_FLAVOR.toPasteDataFlavor()] = currentRtf.byteInputStream()
    }
}
