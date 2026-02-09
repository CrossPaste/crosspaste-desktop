package com.crosspaste.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.safety.Safelist

fun getHtmlUtils(): HtmlUtils = HtmlUtils

object HtmlUtils {

    private val codecsUtils = getCodecsUtils()

    fun dataUrl(html: String): String {
        val encodedContent = codecsUtils.base64Encode(html.encodeToByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }

    fun getHtmlText(html: String): String? =
        runCatching {
            val ksoupDoc: Document = Ksoup.parse(html)
            val outputSettings = Document.OutputSettings()
            outputSettings.prettyPrint(false)
            ksoupDoc.outputSettings(outputSettings)
            ksoupDoc.select("br").before("\\n")
            ksoupDoc.select("p").before("\\n")
            val str = ksoupDoc.html().replace("\\\\n".toRegex(), "\n")
            Ksoup.clean(str, Safelist.none(), "", outputSettings)
        }.getOrNull()

    fun ensureHtmlCharsetUtf8(html: String): String =
        runCatching {
            val doc = Ksoup.parse(html)
            val head = doc.head()

            head.select("meta[charset]").remove()
            head.select("meta[http-equiv=Content-Type]").remove()
            head.select("meta[name=viewport]").remove()

            val metaTags =
                listOf(
                    doc.createElement("meta").attr("charset", "UTF-8"),
                    doc
                        .createElement("meta")
                        .attr("http-equiv", "Content-Type")
                        .attr("content", "text/html; charset=UTF-8"),
                )

            metaTags.reversed().forEach { meta ->
                head.prependChild(meta)
            }

            doc.html()
        }.getOrElse { html }
}
