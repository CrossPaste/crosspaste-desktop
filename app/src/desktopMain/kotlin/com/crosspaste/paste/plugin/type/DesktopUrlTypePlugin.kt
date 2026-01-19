package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors.URL_FLAVOR
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.Platform
import java.net.MalformedURLException
import java.net.URL

class DesktopUrlTypePlugin(
    private val platform: Platform,
) : UrlTypePlugin {

    companion object {
        const val URL = "application/x-java-url"
    }

    override fun getPasteType(): PasteType = PasteType.URL_TYPE

    override fun getIdentifiers(): List<String> = listOf(URL)

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        createUrlPasteItem(
            identifiers = listOf(identifier),
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
            val update: (PasteItem) -> PasteItem = { pasteItem ->
                createUrlPasteItem(
                    identifiers = pasteItem.identifiers,
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
        mixedCategory: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as UrlPasteItem
        @Suppress("DEPRECATION")
        map[URL_FLAVOR.toPasteDataFlavor()] = URL(pasteItem.url)
    }
}
