package com.crosspaste.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

actual fun getHtmlUtils(): HtmlUtils {
    return DesktopHtmlUtils
}

object DesktopHtmlUtils : HtmlUtils {
    private val codecsUtils = getCodecsUtils()

    override fun dataUrl(html: String): String {
        val encodedContent = codecsUtils.base64Encode(html.toByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }

    override fun getHtmlText(html: String): String {
        val jsoupDoc: Document = Jsoup.parse(html)
        val outputSettings = Document.OutputSettings()
        outputSettings.prettyPrint(false)
        jsoupDoc.outputSettings(outputSettings)
        jsoupDoc.select("br").before("\\n")
        jsoupDoc.select("p").before("\\n")
        val str = jsoupDoc.html().replace("\\\\n".toRegex(), "\n")
        return Jsoup.clean(str, "", Safelist.none(), outputSettings)
    }
}
