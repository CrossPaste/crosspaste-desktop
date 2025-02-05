package com.crosspaste.ui.model

import com.crosspaste.app.AppFileType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.noOptionParent
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingPasteDataViewModel(
    private val copywriter: GlobalCopywriter,
    private val userDataPathProvider: UserDataPathProvider,
) : PasteDataViewModel() {

    private val codecsUtils = getCodecsUtils()

    private val fileUtils = getFileUtils()

    private val jsonUtils = getJsonUtils()

    private val crossPasteDownloadUrl =
        PasteData().apply {
            val url = "https://crosspaste.com/download"
            appInstanceId = "1"
            pasteId = 1
            pasteAppearItem =
                RealmAny.create(
                    UrlPasteItem().apply {
                        this.url = url
                        size = url.length.toLong()
                        hash = codecsUtils.hash(url.encodeToByteArray())
                    },
                )
            pasteType = PasteType.URL_TYPE.type
            size = "https://crosspaste.com/download".length.toLong()
            hash = codecsUtils.hash("https://crosspaste.com/download".encodeToByteArray())
            pasteState = PasteState.LOADED
        }

    private val zipData =
        PasteData().apply {
            val dataZipPath = userDataPathProvider.resolve("data.zip", AppFileType.MARKETING)
            val fileInfoTree = fileUtils.getFileInfoTree(dataZipPath)
            val fileInfoTrees = mapOf("data.zip" to fileInfoTree)
            val fileInfoTreeJsonString = jsonUtils.JSON.encodeToString(fileInfoTrees)
            val size = fileUtils.getFileSize(dataZipPath)
            val hash = fileUtils.getFileHash(dataZipPath)

            appInstanceId = "1"
            pasteId = 2
            pasteAppearItem =
                RealmAny.create(
                    FilesPasteItem().apply {
                        this.relativePathList = listOf<String>("data.zip").toRealmList()
                        this.fileInfoTree = fileInfoTreeJsonString
                        this.count = 1
                        this.basePath = dataZipPath.noOptionParent.toString()
                        this.size = size
                        this.hash = hash
                    },
                )
            pasteType = PasteType.FILE_TYPE.type
            this.size = size
            this.hash = hash
            pasteState = PasteState.LOADED
        }

    private val imageFile =
        PasteData().apply {
            val imageName = "eberhard-grossgasteiger-MaKGtATeNDY-unsplash.jpg"
            val imagePath =
                userDataPathProvider.resolve(
                    imageName,
                    AppFileType.MARKETING,
                )
            val fileInfoTree = fileUtils.getFileInfoTree(imagePath)
            val fileInfoTrees = mapOf(imageName to fileInfoTree)
            val fileInfoTreeJsonString = jsonUtils.JSON.encodeToString(fileInfoTrees)
            val size = fileUtils.getFileSize(imagePath)
            val hash = fileUtils.getFileHash(imagePath)

            appInstanceId = "1"
            pasteId = 3
            pasteAppearItem =
                RealmAny.create(
                    ImagesPasteItem().apply {
                        this.relativePathList = listOf<String>(imageName).toRealmList()
                        this.fileInfoTree = fileInfoTreeJsonString
                        this.count = 1
                        this.basePath = imagePath.noOptionParent.toString()
                        this.size = size
                        this.hash = hash
                    },
                )
            pasteType = PasteType.IMAGE_TYPE.type
            this.size = size
            this.hash = hash
            pasteState = PasteState.LOADED
        }

    private val language = if (copywriter.language() == "zh") "zh" else "en"

    private val text =
        PasteData().apply {
            val textPath =
                userDataPathProvider.resolve(
                    "$language.txt",
                    AppFileType.MARKETING,
                )

            val byteArray = fileUtils.fileSystem.read(textPath) { readByteArray() }

            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)
            appInstanceId = "1"
            pasteId = 4
            pasteAppearItem =
                RealmAny.create(
                    TextPasteItem().apply {
                        this.text = String(byteArray)
                        this.size = size
                        this.hash = hash
                    },
                )
            pasteType = PasteType.TEXT_TYPE.type
            this.size = size
            this.hash = hash
            pasteState = PasteState.LOADED
        }

    private val html =
        PasteData().apply {
            val htmlPath =
                userDataPathProvider.resolve(
                    "$language.html",
                    AppFileType.MARKETING,
                )

            val byteArray = fileUtils.fileSystem.read(htmlPath) { readByteArray() }
            val size = byteArray.size.toLong()
            val hash = codecsUtils.hash(byteArray)
            appInstanceId = "1"
            pasteId = if (language == "en") 5 else 6
            pasteAppearItem =
                RealmAny.create(
                    HtmlPasteItem().apply {
                        this.relativePath =
                            fileUtils.createPasteRelativePath(
                                pasteCoordinate =
                                    PasteCoordinate(
                                        appInstanceId = "1",
                                        pasteId = pasteId,
                                    ),
                                fileName = "html2Image.png",
                            )
                        this.html = String(byteArray)
                        this.size = size
                        this.hash = hash
                    },
                )
            pasteType = PasteType.HTML_TYPE.type
            this.size = size
            this.hash = hash
            pasteState = PasteState.LOADED
            this.createTime = RealmInstant.now()
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
