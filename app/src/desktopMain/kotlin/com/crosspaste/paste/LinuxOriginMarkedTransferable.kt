package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream

/**
 * A clipboard write of ours with [PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR]
 * added, so that once the compositor mirrors the X11 selection into the
 * Wayland clipboard the native monitor recognises it as our own and does not
 * record it a second time. Every other flavor is delegated untouched.
 */
class LinuxOriginMarkedTransferable(
    private val delegate: Transferable,
) : Transferable {

    private val flavors: Array<DataFlavor> = delegate.transferDataFlavors + PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.clone()

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
        flavor == PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR || delegate.isDataFlavorSupported(flavor)

    override fun getTransferData(flavor: DataFlavor?): Any =
        if (flavor == PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR) {
            // A fresh stream per read: AWT may convert the target more than once.
            ByteArrayInputStream(MARKER_BYTES)
        } else {
            delegate.getTransferData(flavor)
        }

    companion object {
        private val MARKER_BYTES = "crosspaste".encodeToByteArray()
    }
}
