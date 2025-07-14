package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors.URL_FLAVOR
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getCodecsUtils
import java.net.MalformedURLException
import java.net.URL

class DesktopUrlTypePlugin(
    private val platform: Platform,
) : UrlTypePlugin {

    companion object {
        const val URL = "application/x-java-url"

        private val codecsUtils = getCodecsUtils()
    }

    override fun getPasteType(): PasteType = PasteType.URL_TYPE

    override fun getIdentifiers(): List<String> = listOf(URL)

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        UrlPasteItem(
            identifiers = listOf(identifier),
            hash = "",
            size = 0,
            url = "",
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
        if (transferData is String) {
            val urlBytes = transferData.encodeToByteArray()
            val hash = codecsUtils.hash(urlBytes)
            val update: (PasteItem) -> PasteItem = { pasteItem ->
                UrlPasteItem(
                    identifiers = pasteItem.identifiers,
                    hash = hash,
                    size = urlBytes.size.toLong(),
                    url = transferData,
                    extraInfo = pasteItem.extraInfo,
                )
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun collectError(
        error: Throwable,
        pasteId: Long,
        itemIndex: Int,
        pasteCollector: PasteCollector,
    ) {
        if (!platform.isWindows() || error !is MalformedURLException) {
            super.collectError(error, pasteId, itemIndex, pasteCollector)
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        singleType: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as UrlPasteItem
        @Suppress("DEPRECATION")
        map[URL_FLAVOR.toPasteDataFlavor()] = URL(pasteItem.url)
    }
}
