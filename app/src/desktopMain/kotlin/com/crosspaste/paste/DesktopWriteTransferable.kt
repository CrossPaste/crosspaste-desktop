package com.crosspaste.paste

import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class DesktopWriteTransferableBuilder {

    private val map: MutableMap<PasteDataFlavor, Any> = LinkedHashMap()

    fun isEmpty(): Boolean = map.isEmpty()

    fun add(
        pasteTypePlugin: PasteTypePlugin,
        pasteItem: PasteItem,
        mixedCategory: Boolean,
    ): DesktopWriteTransferableBuilder {
        pasteTypePlugin.buildTransferable(pasteItem, mixedCategory, map)
        return this
    }

    fun add(
        pasteDataFlavor: PasteDataFlavor,
        value: Any,
    ): DesktopWriteTransferableBuilder {
        map[pasteDataFlavor] = value
        return this
    }

    fun build(): DesktopWriteTransferable =
        DesktopWriteTransferable(
            map
                .mapKeys {
                    (it.key as DesktopPasteDataFlavor).dataFlavor
                }.toMap(LinkedHashMap()),
        )
}

class DesktopWriteTransferable(
    private val map: LinkedHashMap<DataFlavor, Any>,
) : PasteTransferable,
    Transferable {

    private val dataFlavors = map.keys.toTypedArray()

    override fun getTransferDataFlavors(): Array<DataFlavor> = dataFlavors

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = map.containsKey(flavor)

    override fun getTransferData(flavor: DataFlavor?): Any = map[flavor] ?: NoneTransferData

    override fun getTransferData(pasteDataFlavor: PasteDataFlavor): Any {
        pasteDataFlavor as DesktopPasteDataFlavor
        return map[pasteDataFlavor.dataFlavor] ?: NoneTransferData
    }
}
