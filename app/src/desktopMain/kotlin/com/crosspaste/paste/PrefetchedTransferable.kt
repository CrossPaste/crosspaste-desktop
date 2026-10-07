package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * An in-memory copy of a dropped [Transferable].
 *
 * Drop data is only readable until the drop callback returns, but collecting it (file
 * copies, image encoding, the database write) must not run on the UI thread. So the
 * drop callback reads the flavors a paste type plugin will ask for into this copy, and
 * the collection runs on it later. Every original flavor is still listed, so the
 * consumer sees the same flavor set (and item order) as from the source. A flavor that
 * was not read returns [NoneTransferData], which the plugins skip just as they skip a
 * representation they cannot use; a read that failed rethrows its original error.
 */
class PrefetchedTransferable private constructor(
    private val flavors: Array<DataFlavor>,
    private val data: Map<DataFlavor, Result<Any?>>,
) : Transferable {

    companion object {
        fun of(
            source: Transferable,
            shouldRead: (DataFlavor) -> Boolean,
        ): PrefetchedTransferable {
            val flavors = source.transferDataFlavors ?: emptyArray()
            val data =
                flavors
                    .filter(shouldRead)
                    .associateWith { flavor -> runCatching { detach(source.getTransferData(flavor)) } }
            return PrefetchedTransferable(flavors, data)
        }

        // A stream may still read from the drag source (e.g. a native IStream on Windows),
        // which is gone once the drop completes; buffer its bytes now so each subsequent read
        // receives a fresh stream from position zero.
        private fun detach(value: Any?): Any? =
            if (value is InputStream) {
                BufferedStream(value.use { it.readBytes() })
            } else {
                value
            }
    }

    /** Marks bytes that came from a stream, so a byte[] flavor is still returned as byte[]. */
    private class BufferedStream(
        val bytes: ByteArray,
    )

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.clone()

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavors.contains(flavor)

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor !in flavors) throw UnsupportedFlavorException(flavor)
        val value = data[flavor]?.getOrThrow() ?: return NoneTransferData
        return if (value is BufferedStream) ByteArrayInputStream(value.bytes) else value
    }
}
