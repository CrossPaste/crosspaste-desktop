package com.crosspaste.ui.model

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteCollection
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteState
import com.crosspaste.db.paste.PasteType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingPasteDataViewModel(
    copywriter: GlobalCopywriter,
    private val userDataPathProvider: UserDataPathProvider,
) : PasteDataViewModel() {

    private val codecsUtils = getCodecsUtils()

    private val fileUtils = getFileUtils()

    private val crossPasteDownloadUrl =
        run {
            val url = "https://crosspaste.com/download"
            val urlBytes = url.encodeToByteArray()

            PasteData(
                appInstanceId = "1",
                favorite = false,
                pasteId = 1,
                pasteAppearItem =
                    UrlPasteItem(
                        identifiers = listOf(),
                        url = url,
                        size = urlBytes.size.toLong(),
                        hash = codecsUtils.hash(urlBytes),
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = null,
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
                appInstanceId = "1",
                favorite = false,
                pasteId = 2,
                pasteAppearItem =
                    FilesPasteItem(
                        identifiers = listOf(),
                        count = 1,
                        size = size,
                        hash = hash,
                        fileInfoTreeMap = fileInfoTrees,
                        relativePathList = listOf("data.zip"),
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.FILE_TYPE.type,
                source = null,
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    private val imageFile =
        run {
            val imageName = "eberhard-grossgasteiger-MaKGtATeNDY-unsplash.jpg"
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
                appInstanceId = "1",
                favorite = false,
                pasteId = 3,
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
                source = null,
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
                appInstanceId = "1",
                favorite = false,
                pasteId = 4,
                pasteAppearItem =
                    TextPasteItem(
                        identifiers = listOf(),
                        text = byteArray.contentToString(),
                        size = size,
                        hash = hash,
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                source = null,
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    private val html =
        run {
            val htmlPath =
                userDataPathProvider.resolve(
                    "$language.html",
                    AppFileType.MARKETING,
                )

            val byteArray = fileUtils.fileSystem.read(htmlPath) { readByteArray() }
            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)

            PasteData(
                appInstanceId = "1",
                favorite = false,
                pasteId = if (language == "en") 5 else 6,
                pasteAppearItem =
                    HtmlPasteItem(
                        identifiers = listOf(),
                        html = byteArray.contentToString(),
                        size = size,
                        hash = hash,
                        relativePath =
                            fileUtils.createPasteRelativePath(
                                pasteCoordinate =
                                    PasteCoordinate(
                                        appInstanceId = "1",
                                        pasteId = if (language == "en") 5 else 6,
                                    ),
                                fileName = "html2Image.png",
                            ),
                    ),
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.HTML_TYPE.type,
                source = null,
                size = size,
                hash = hash,
                pasteState = PasteState.LOADED,
            )
        }

    override val pasteDataList: StateFlow<List<PasteData>> =
        MutableStateFlow(
            listOf(
                html,
                text,
                imageFile,
                zipData,
                crossPasteDownloadUrl,
            ),
        )

    override fun loadMore() {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun cleanup() {
    }
}
