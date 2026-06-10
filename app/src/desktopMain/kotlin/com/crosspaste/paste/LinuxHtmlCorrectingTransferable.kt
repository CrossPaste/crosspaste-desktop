package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.StringReader

/**
 * Wraps an AWT clipboard [Transferable], overriding only its `text/html`
 * flavors to return [correctedHtml] (decoded from the raw X11 selection bytes
 * via [HtmlClipboardDecoder]) instead of AWT's mis-decoded value. Every other
 * flavor is delegated untouched, so the existing paste pipeline is unaffected.
 */
class LinuxHtmlCorrectingTransferable(
    private val delegate: Transferable,
    private val correctedHtml: String,
) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> = delegate.transferDataFlavors

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = delegate.isDataFlavorSupported(flavor)

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isHtmlFlavor(flavor)) {
            return delegate.getTransferData(flavor)
        }
        val representationClass = flavor.representationClass
        return when {
            String::class.java.isAssignableFrom(representationClass) -> correctedHtml
            java.io.Reader::class.java.isAssignableFrom(representationClass) -> StringReader(correctedHtml)
            java.io.InputStream::class.java.isAssignableFrom(representationClass) ->
                ByteArrayInputStream(correctedHtml.toByteArray(flavorCharset(flavor)))
            else -> delegate.getTransferData(flavor)
        }
    }

    private fun isHtmlFlavor(flavor: DataFlavor): Boolean = flavor.primaryType == "text" && flavor.subType == "html"

    private fun flavorCharset(flavor: DataFlavor): java.nio.charset.Charset =
        flavor.getParameter("charset")?.let {
            runCatching {
                java.nio.charset.Charset
                    .forName(it)
            }.getOrNull()
        } ?: Charsets.UTF_8

    companion object {

        fun supportsHtml(transferable: Transferable): Boolean =
            transferable.transferDataFlavors.any { it.primaryType == "text" && it.subType == "html" }
    }
}
