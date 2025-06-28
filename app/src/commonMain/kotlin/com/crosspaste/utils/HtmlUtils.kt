package com.crosspaste.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.safety.Safelist

fun getHtmlUtils(): HtmlUtils {
    return HtmlUtils
}

object HtmlUtils {

    private val codecsUtils = getCodecsUtils()

    fun dataUrl(html: String): String {
        val encodedContent = codecsUtils.base64Encode(html.encodeToByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }

    fun getHtmlText(html: String): String {
        val jsoupDoc: Document = Ksoup.parse(html)
        val outputSettings = Document.OutputSettings()
        outputSettings.prettyPrint(false)
        jsoupDoc.outputSettings(outputSettings)
        jsoupDoc.select("br").before("\\n")
        jsoupDoc.select("p").before("\\n")
        val str = jsoupDoc.html().replace("\\\\n".toRegex(), "\n")
        return Ksoup.clean(str, Safelist.none(), "", outputSettings)
    }

    fun ensureHtmlCharsetUtf8(html: String): String {
        return runCatching {
            val doc = Ksoup.parse(html)
            val head = doc.head()

            head.select("meta[charset]").remove()
            head.select("meta[http-equiv=Content-Type]").remove()
            head.select("meta[name=viewport]").remove()

            val metaTags =
                listOf(
                    doc.createElement("meta").attr("charset", "UTF-8"),
                    doc.createElement("meta")
                        .attr("http-equiv", "Content-Type")
                        .attr("content", "text/html; charset=UTF-8"),
                )

            metaTags.reversed().forEach { meta ->
                head.prependChild(meta)
            }

            doc.html()
        }.getOrElse { html }
    }
}
