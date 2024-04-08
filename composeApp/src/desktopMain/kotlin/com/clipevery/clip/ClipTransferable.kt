package com.clipevery.clip

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

class ClipTransferable(private val map: LinkedHashMap<DataFlavor, Any>) : Transferable {

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
}
