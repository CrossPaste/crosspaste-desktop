package com.crosspaste.ui.model

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteCollection
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteState
import com.crosspaste.db.paste.PasteType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItemProperties.MARKETING_PATH
import com.crosspaste.paste.item.PasteItemProperties.TITLE
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform.Companion.MACOS
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.ColorUtils.tryCovertToColor
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

open class MarketingPasteData(
    copywriter: GlobalCopywriter,
    private val userDataPathProvider: UserDataPathProvider,
) {

    private val codecsUtils = getCodecsUtils()

    private val fileUtils = getFileUtils()

    private val color =
        run {
            val colorHex = "#FFA6D6D6"
            val colorBytes = colorHex.encodeToByteArray()

            PasteData(
                id = 1L,
                appInstanceId = "$MACOS-id",
                favorite = false,
                pasteAppearItem =
                    ColorPasteItem(
                        identifiers = listOf(),
                        color = tryCovertToColor(colorHex)!!,
                        size = colorBytes.size.toLong(),
                        hash = codecsUtils.hash(colorBytes),
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.COLOR_TYPE.type,
                source = "Figma",
                size = colorBytes.size.toLong(),
                hash = codecsUtils.hash(colorBytes),
                pasteState = PasteState.LOADED,
            )
        }

    private val url =
        run {
            val imagePath =
                userDataPathProvider
                    .resolve(appFileType = AppFileType.MARKETING)
                    .resolve("openGraphImage.png")
                    .toString()

            val url = "https://github.com"
            val urlBytes = url.encodeToByteArray()

            PasteData(
                id = 2L,
                appInstanceId = "$MACOS-id",
                favorite = false,
                pasteAppearItem =
                    UrlPasteItem(
                        identifiers = listOf(),
                        url = url,
                        size = urlBytes.size.toLong(),
                        hash = codecsUtils.hash(urlBytes),
                        extraInfo =
                            buildJsonObject {
                                put(MARKETING_PATH, imagePath)
                                put(TITLE, "Github - Build software better, together")
                            },
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = "Google Chrome",
                size = urlBytes.size.toLong(),
                hash = codecsUtils.hash(urlBytes),
                pasteState = PasteState.LOADED,
            )
        }

    private val zipData =
        run {
            val dataZipPath = userDataPathProvider.resolve("data.zip", AppFileType.MARKETING)
            val fileInfoTree = fileUtils.getFileInfoTree(dataZipPath)
            val fileInfoTrees = mapOf("data.zip" to fileInfoTree)
            val size = fileInfoTree.size
            val hash = fileInfoTree.hash
            PasteData(
                id = 3L,
                appInstanceId = "$MACOS-id",
                favorite = false,
                pasteAppearItem =
                    FilesPasteItem(
                        identifiers = listOf(),
                        count = 1,
                        size = size,
                        hash = hash,
                        basePath = dataZipPath.noOptionParent.toString(),
                        fileInfoTreeMap = fileInfoTrees,
                        relativePathList = listOf("data.zip"),
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.FILE_TYPE.type,
                source = "Finder",
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    private val imageFile =
        run {
            val imageName = "sunflower.png"
            val imagePath =
                userDataPathProvider.resolve(
                    imageName,
                    AppFileType.MARKETING,
                )
            val fileInfoTree = fileUtils.getFileInfoTree(imagePath)
            val fileInfoTrees: Map<String, FileInfoTree> = mapOf(imageName to fileInfoTree)
            val size = fileUtils.getFileSize(imagePath)
            val hash = fileUtils.getFileHash(imagePath)

            PasteData(
                id = 4L,
                appInstanceId = "$MACOS-id",
                favorite = false,
                pasteAppearItem =
                    ImagesPasteItem(
                        identifiers = listOf(),
                        count = 1,
                        basePath = imagePath.noOptionParent.toString(),
                        size = size,
                        hash = hash,
                        fileInfoTreeMap = fileInfoTrees,
                        relativePathList = listOf(imageName),
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.IMAGE_TYPE.type,
                source = "Photo Album",
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    private val language = if (copywriter.language() == "zh") "zh" else "en"

    private val text =
        run {
            val textPath =
                userDataPathProvider.resolve(
                    "$language.txt",
                    AppFileType.MARKETING,
                )

            val byteArray = fileUtils.fileSystem.read(textPath) { readByteArray() }

            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)

            PasteData(
                id = 5L,
                appInstanceId = "$MACOS-id",
                favorite = false,
                pasteAppearItem =
                    TextPasteItem(
                        identifiers = listOf(),
                        text = byteArray.decodeToString(),
                        size = size,
                        hash = hash,
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                source = "Notes Archive",
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    private val html =
        run {
            val imagePath =
                userDataPathProvider
                    .resolve(appFileType = AppFileType.MARKETING)
                    .resolve("$language-html2Image.png")
                    .toString()

            val htmlPath =
                userDataPathProvider.resolve(
                    "$language.html",
                    AppFileType.MARKETING,
                )

            val byteArray = fileUtils.fileSystem.read(htmlPath) { readByteArray() }
            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)

            PasteData(
                id = 6L,
                appInstanceId = "$MACOS-id",
                favorite = false,
                pasteAppearItem =
                    HtmlPasteItem(
                        identifiers = listOf(),
                        hash = hash,
                        size = size,
                        html = byteArray.decodeToString(),
                        extraInfo =
                            buildJsonObject {
                                put(MARKETING_PATH, imagePath)
                            },
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.HTML_TYPE.type,
                source = "Email",
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    open fun getPasteDataList(): List<PasteData> =
        listOf(
            html,
            text,
            imageFile,
            zipData,
            url,
            color,
        )
}
