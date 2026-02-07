package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.image.ImageHandler
import com.crosspaste.paste.DesktopPasteDataFlavor
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors
import com.crosspaste.paste.PasteDataFlavors.URL_FLAVOR
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.DesktopFilesTypePlugin.Companion.FILE_LIST_ID
import com.crosspaste.paste.plugin.type.DesktopHtmlTypePlugin.Companion.HTML_ID
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.FileNameNormalizer
import com.crosspaste.utils.getFileUtils
import com.fleeksoft.ksoup.Ksoup
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.asSource
import kotlinx.io.buffered
import okio.Path.Companion.toOkioPath
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI

class DesktopImageTypePlugin(
    private val appInfo: AppInfo,
    private val imageHandler: ImageHandler<BufferedImage>,
    private val platform: Platform,
    private val userDataPathProvider: UserDataPathProvider,
) : ImageTypePlugin {

    companion object {
        const val X_JAVA_IMAGE = "image/x-java-image"
        const val IMAGE_PNG = "image/png"
        const val IMAGE_JPEG = "image/jpeg"
        const val IMAGE = "image"
    }

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override fun getPasteType(): PasteType = PasteType.IMAGE_TYPE

    override fun getIdentifiers(): List<String> = listOf(X_JAVA_IMAGE, IMAGE_PNG, IMAGE_JPEG, IMAGE)

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        createImagesPasteItem(
            identifiers = listOf(identifier),
            relativePathList = listOf(),
            fileInfoTreeMap = mapOf(),
        ).let {
            pasteCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: PasteDataFlavor,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        // FILE_LIST_IDIf FILE_LIST_ID exists
        // then the image is the icon corresponding to the file type
        // we do not need to save
        if (dataFlavorMap.keys.contains(FILE_LIST_ID)) {
            return
        }

        var image: BufferedImage? = null

        if (transferData is Image) {
            image = toBufferedImage(img = transferData)
        } else if (transferData is InputStream) {
            image = imageHandler.readImage(transferData.asSource().buffered())
        }

        image?.let {
            var name =
                FileNameNormalizer.normalize(
                    tryGetImageName(dataFlavorMap, pasteTransferable)
                        ?: fileUtils.createRandomFileName(ext = "png"),
                )
            val ext =
                fileUtils.getImageExtFromFileName(name) ?: run {
                    name += ".png"
                    "png"
                }
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate =
                        PasteCoordinate(
                            id = pasteId,
                            appInstanceId = appInfo.appInstanceId,
                        ),
                    fileName = name,
                )
            val imagePath =
                fileUtils.createPastePath(
                    relativePath,
                    isFile = true,
                    AppFileType.IMAGE,
                    userDataPathProvider,
                )
            if (imageHandler.writeImage(image, ext, imagePath)) {
                val fileTree = fileUtils.getFileInfoTree(imagePath)

                val update: (PasteItem) -> PasteItem = { pasteItem ->
                    createImagesPasteItem(
                        identifiers = pasteItem.identifiers,
                        relativePathList = listOf(relativePath),
                        fileInfoTreeMap = mapOf(name to fileTree),
                        extraInfo = pasteItem.extraInfo,
                    )
                }
                pasteCollector.updateCollectItem(itemIndex, this::class, update)
            } else {
                logger.warn { "Failed to write image for pasteId=$pasteId itemIndex=$itemIndex path=$imagePath" }
            }
        }
    }

    private fun tryGetImageName(
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
    ): String? {
        dataFlavorMap[HTML_ID]?.let {
            for (pasteDataFlavor in it) {
                val dataFlavor = (pasteDataFlavor as DesktopPasteDataFlavor).dataFlavor
                if (dataFlavor.representationClass == String::class.java) {
                    (pasteTransferable.getTransferData(pasteDataFlavor) as? String)?.let { imageHtml ->
                        return getImageNameFromHtml(imageHtml)
                    }
                }
            }
        }
        return null
    }

    private fun getImageNameFromHtml(imageHtml: String): String? {
        val doc = Ksoup.parse(imageHtml)

        val imgElement = doc.select("img").first() ?: return null

        val src = imgElement.attr("src")

        return getLastPathSegment(src)
    }

    private fun getLastPathSegment(urlString: String): String? {
        return runCatching {
            val uri = URI(urlString)
            val path = uri.path ?: return@runCatching null

            // Remove trailing slash from the path (if it exists)
            val normalizedPath = path.removeSuffix("/")

            // Handle empty path or root path
            if (normalizedPath.isEmpty() || normalizedPath == "/") {
                return@runCatching null
            }

            // Get the last path segment
            normalizedPath.substringAfterLast('/')
        }.getOrNull()
    }

    private fun toBufferedImage(img: Image): BufferedImage {
        if (img is BufferedImage) {
            return img
        }

        // Create a buffered image with transparency
        val bufferedImage =
            BufferedImage(
                img.getWidth(null),
                img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB,
            )

        // Draw the image on to the buffered image
        val bGr = bufferedImage.createGraphics()
        bGr.drawImage(img, 0, 0, null)
        bGr.dispose()

        // Return the buffered image
        return bufferedImage
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        mixedCategory: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as ImagesPasteItem
        val filePaths = pasteItem.getFilePaths(userDataPathProvider)
        val fileList: List<File> = filePaths.map { it.toFile() }
        map[DataFlavor.javaFileListFlavor.toPasteDataFlavor()] = fileList

        if (mixedCategory) {
            map[PasteDataFlavors.URI_LIST_FLAVOR.toPasteDataFlavor()] =
                ByteArrayInputStream(
                    fileList
                        .joinToString(separator = "\n") {
                            it.absolutePath
                        }.encodeToByteArray(),
                )
            map[DataFlavor.stringFlavor.toPasteDataFlavor()] =
                fileList.joinToString(separator = "\n") {
                    it.name
                }

            if (fileList.size == 1) {
                map[URL_FLAVOR.toPasteDataFlavor()] = fileList[0].toURI().toURL()
                runCatching {
                    val start = System.currentTimeMillis()
                    val image: BufferedImage? = imageHandler.readImage(fileList[0].toOkioPath())
                    image?.let { map[DataFlavor.imageFlavor.toPasteDataFlavor()] = it }
                    val end = System.currentTimeMillis()
                    logger.debug { "read image ${fileList[0].absolutePath} use time: ${end - start} ms" }
                }.onFailure { e ->
                    logger.error(e) { "read image fail" }
                }
            }
        }

        if (platform.isLinux()) {
            val content =
                fileList.joinToString(
                    separator = "\n",
                    prefix = "copy\n",
                ) { it.toURI().toString() }
            val inputStream = ByteArrayInputStream(content.encodeToByteArray())
            map[PasteDataFlavors.GNOME_COPIED_FILES_FLAVOR.toPasteDataFlavor()] = inputStream
        }
    }
}
