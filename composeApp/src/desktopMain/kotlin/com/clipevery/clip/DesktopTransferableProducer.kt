package com.clipevery.clip

import com.clipevery.dao.clip.ClipData
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class DesktopTransferableProducer: TransferableProducer {
    override fun produce(clipData: ClipData): Transferable {
        val dataFlavors = buildDataFlavorArray(clipData)
        return object: Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> {
                return dataFlavors
            }

            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
                return dataFlavors.contains(flavor)
            }

            override fun getTransferData(flavor: DataFlavor?): Any {
                return flavor?.let {
                    (it as? ClipDataFlavor)?.getData()
                } ?: ""
            }
        }
    }

    private fun buildDataFlavorArray(clipData: ClipData): Array<DataFlavor> {
        // todo implement build data flavor array from clipData
        return Array(0) { DataFlavor() }
    }
}