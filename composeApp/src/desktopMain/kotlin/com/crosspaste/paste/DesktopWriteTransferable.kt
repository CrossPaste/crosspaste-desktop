package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

class DesktopWriteTransferableBuilder {

    private val map: MutableMap<PasteDataFlavor, Any> = LinkedHashMap()

    fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    fun add(
        pasteTypePlugin: PasteTypePlugin,
        pasteItem: PasteItem,
        singleType: Boolean = false,
    ): DesktopWriteTransferableBuilder {
        pasteTypePlugin.buildTransferable(pasteItem, singleType, map)
        return this
    }

    fun add(
        pasteDataFlavor: PasteDataFlavor,
        value: Any,
    ): DesktopWriteTransferableBuilder {
        map[pasteDataFlavor] = value
        return this
    }

    fun build(): DesktopWriteTransferable {
        return DesktopWriteTransferable(
            map.mapKeys {
                (it.key as DesktopPasteDataFlavor).dataFlavor
            }.toMap(LinkedHashMap()),
        )
    }
}

class DesktopWriteTransferable(
    private val map: LinkedHashMap<DataFlavor, Any>,
) : PasteTransferable, Transferable {

    private val dataFlavors = map.keys.toTypedArray()

    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return dataFlavors
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
        return map.containsKey(flavor)
    }

    override fun getTransferData(flavor: DataFlavor?): Any {
        return map[flavor] ?: throw UnsupportedFlavorException(flavor)
    }

    override fun getTransferData(pasteDataFlavor: PasteDataFlavor): Any {
        pasteDataFlavor as DesktopPasteDataFlavor
        return map[pasteDataFlavor.dataFlavor] ?: throw UnsupportedFlavorException(pasteDataFlavor.dataFlavor)
    }
}
