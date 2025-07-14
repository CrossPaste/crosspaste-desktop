package com.crosspaste.plugin.office

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.TextNode

class OfficeHtmlPlugin {

    companion object {
        const val WORD = "Microsoft Word"

        const val EXCEL = "Microsoft Excel"

        const val POWERPOINT = "Microsoft PowerPoint"

        fun isOfficeApp(source: String?): Boolean =
            source?.let {
                it == WORD || it == EXCEL || it == POWERPOINT
            } ?: false
    }

    fun match(source: String): Boolean = isOfficeApp(source)

    fun officeNormalizationHTML(html: String): String {
        val doc: Document = Ksoup.parse(html)

        // Remove unnecessary styles and attributes
        doc.select("[style]").forEach { it.removeAttr("style") }

        // Remove empty spans and divs
        doc.select("span:empty, div:empty").remove()

        // Remove class attributes
        doc.select("[class]").forEach { it.removeAttr("class") }

        // Wrap text nodes in p tags if they're direct children of the body
        doc.body().children().forEach { element ->
            if (element.childNodes().any { it is TextNode && it.text().trim().isNotEmpty() }) {
                val newP = doc.createElement("p")
                element.childNodes().filterIsInstance<TextNode>().forEach { node ->
                    newP.appendText(node.text())
                    node.remove()
                }
                element.before(newP)
            }
        }

        // Set base styles
        doc.head().appendElement("style").text(
            """
            body {
                font-family: "Microsoft YaHei", "SimHei", Arial, sans-serif;
                line-height: 1.6;
            }
            p {
                margin-bottom: 1em;
            }
            """.trimIndent(),
        )

        // Ensure proper encoding
        doc.outputSettings().charset("UTF-8")

        return doc.outerHtml()
    }
}
