package com.crosspaste.paste

import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/**
 * A transfer value produced only when a consumer asks for its flavor, for values that
 * are costly to build and often never read (e.g. a full-size decoded image offered
 * next to its file). Failures are thrown from [Transferable.getTransferData].
 */
class LazyTransferData(
    load: () -> Any,
) {
    val value: Any by lazy(load)
}

class DesktopWriteTransferableBuilder {

    private val map: MutableMap<PasteDataFlavor, Any> = LinkedHashMap()

    fun isEmpty(): Boolean = map.isEmpty()

    suspend fun add(
        pasteTypePlugin: PasteTypePlugin,
        pasteItem: PasteItem,
        scope: PasteWriteScope,
    ): DesktopWriteTransferableBuilder {
        pasteTypePlugin.buildTransferable(pasteItem, scope, map)
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

    override fun getTransferData(flavor: DataFlavor?): Any =
        when (val value = map[flavor]) {
            null -> NoneTransferData
            is LazyTransferData -> value.value
            else -> value
        }

    override fun getTransferData(pasteDataFlavor: PasteDataFlavor): Any =
        getTransferData((pasteDataFlavor as DesktopPasteDataFlavor).dataFlavor)
}
