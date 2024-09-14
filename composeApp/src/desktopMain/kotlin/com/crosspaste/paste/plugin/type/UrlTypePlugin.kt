package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors.URL_FLAVOR
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.getPlatform
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import java.net.MalformedURLException
import java.net.URL

class UrlTypePlugin : PasteTypePlugin {

    companion object {
        const val URL = "application/x-java-url"

        private val codecsUtils = getCodecsUtils()
    }

    private val platform = getPlatform()

    override fun getPasteType(): Int {
        return PasteType.URL
    }

    override fun getIdentifiers(): List<String> {
        return listOf(URL)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        UrlPasteItem().apply {
            this.identifier = identifier
        }.let {
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
            val urlBytes = transferData.toByteArray()
            val hash = codecsUtils.hash(urlBytes)
            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                realm.query(UrlPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                    this.url = transferData
                    this.size = urlBytes.size.toLong()
                    this.hash = hash
                }
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun collectError(
        error: Exception,
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
        map[URL_FLAVOR.toPasteDataFlavor()] = URL(pasteItem.url)
    }
}
