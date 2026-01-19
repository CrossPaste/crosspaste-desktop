package com.crosspaste.paste.item

import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.getCodecsUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object CreatePasteItemHelper {

    private val codecsUtils = getCodecsUtils()

    fun createColorPasteItem(
        identifiers: List<String> = listOf(),
        color: Int,
        extraInfo: JsonObject? = null,
    ): ColorPasteItem {
        val hash = color.toString()
        val size = 8L
        return ColorPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            color = color,
            extraInfo = extraInfo,
        )
    }

    fun ColorPasteItem.copy(color: Int): ColorPasteItem =
        createColorPasteItem(
            identifiers = identifiers,
            color = color,
            extraInfo = extraInfo,
        )

    fun createFilesPasteItem(
        identifiers: List<String> = listOf(),
        basePath: String? = null,
        relativePathList: List<String>,
        fileInfoTreeMap: Map<String, FileInfoTree>,
        extraInfo: JsonObject? = null,
    ): FilesPasteItem {
        val names = fileInfoTreeMap.keys.sorted()
        val hash = codecsUtils.hashByArray(names.mapNotNull { fileInfoTreeMap[it]?.hash }.toTypedArray())
        val count = fileInfoTreeMap.map { it.value.getCount() }.sum()
        val size = fileInfoTreeMap.map { it.value.size }.sum()
        return FilesPasteItem(
            identifiers = identifiers,
            count = count,
            hash = hash,
            size = size,
            basePath = basePath,
            relativePathList = relativePathList,
            fileInfoTreeMap = fileInfoTreeMap,
            extraInfo = extraInfo,
        )
    }

    fun FilesPasteItem.copy(
        basePath: String? = null,
        relativePathList: List<String>,
        fileInfoTreeMap: Map<String, FileInfoTree>,
    ): FilesPasteItem =
        createFilesPasteItem(
            identifiers = identifiers,
            basePath = basePath,
            relativePathList = relativePathList,
            fileInfoTreeMap = fileInfoTreeMap,
            extraInfo = extraInfo,
        )

    fun createHtmlPasteItem(
        identifiers: List<String> = listOf(),
        html: String,
        extraInfo: JsonObject? = null,
    ): HtmlPasteItem {
        val htmlBytes = html.encodeToByteArray()
        val hash = codecsUtils.hash(htmlBytes)
        val size = htmlBytes.size.toLong()
        return HtmlPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            html = html,
            extraInfo = extraInfo,
        )
    }

    fun HtmlPasteItem.copy(html: String): HtmlPasteItem =
        createHtmlPasteItem(
            identifiers = identifiers,
            html = html,
            extraInfo = extraInfo,
        )

    fun createImagesPasteItem(
        identifiers: List<String> = listOf(),
        basePath: String? = null,
        relativePathList: List<String>,
        fileInfoTreeMap: Map<String, FileInfoTree>,
        extraInfo: JsonObject? = null,
    ): ImagesPasteItem {
        val names = fileInfoTreeMap.keys.sorted()
        val hash = codecsUtils.hashByArray(names.mapNotNull { fileInfoTreeMap[it]?.hash }.toTypedArray())
        val count = fileInfoTreeMap.map { it.value.getCount() }.sum()
        val size = fileInfoTreeMap.map { it.value.size }.sum()
        return ImagesPasteItem(
            identifiers = identifiers,
            count = count,
            hash = hash,
            size = size,
            basePath = basePath,
            relativePathList = relativePathList,
            fileInfoTreeMap = fileInfoTreeMap,
            extraInfo = extraInfo,
        )
    }

    fun createRtfPasteItem(
        identifiers: List<String> = listOf(),
        rtf: String,
        extraInfo: JsonObject? = null,
    ): RtfPasteItem {
        val rtfBytes = rtf.encodeToByteArray()
        val hash = codecsUtils.hash(rtfBytes)
        val size = rtfBytes.size.toLong()
        return RtfPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            rtf = rtf,
            extraInfo = extraInfo,
        )
    }

    fun RtfPasteItem.copy(rtf: String): RtfPasteItem =
        createRtfPasteItem(
            identifiers = identifiers,
            rtf = rtf,
            extraInfo = extraInfo,
        )

    fun createTextPasteItem(
        identifiers: List<String> = listOf(),
        text: String,
        extraInfo: JsonObject? = null,
    ): TextPasteItem {
        val textBytes = text.encodeToByteArray()
        val hash = codecsUtils.hash(textBytes)
        val size = textBytes.size.toLong()
        return TextPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            text = text,
            extraInfo = extraInfo,
        )
    }

    fun TextPasteItem.copy(text: String): TextPasteItem =
        createTextPasteItem(
            identifiers = identifiers,
            text = text,
            extraInfo = extraInfo,
        )

    fun createUrlPasteItem(
        identifiers: List<String> = listOf(),
        url: String,
        extraInfo: JsonObject? = null,
    ): UrlPasteItem {
        val urlBytes = url.encodeToByteArray()
        val hash = codecsUtils.hash(urlBytes)
        var size = urlBytes.size.toLong()
        extraInfo?.let {
            it[PasteItemProperties.TITLE]?.jsonPrimitive?.contentOrNull?.let { title ->
                size += title.length.toLong()
            }
        }
        return UrlPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            url = url,
            extraInfo = extraInfo,
        )
    }

    fun UrlPasteItem.copy(url: String): PasteItem =
        createUrlPasteItem(
            identifiers = identifiers,
            url = url,
            extraInfo = extraInfo,
        )
}
