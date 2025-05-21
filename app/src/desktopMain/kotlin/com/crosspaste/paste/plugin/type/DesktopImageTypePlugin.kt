package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteType
import com.crosspaste.image.ImageWriter
import com.crosspaste.paste.DesktopPasteDataFlavor
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors
import com.crosspaste.paste.PasteDataFlavors.URL_FLAVOR
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.DesktopFilesTypePlugin.Companion.FILE_LIST_ID
import com.crosspaste.paste.plugin.type.DesktopHtmlTypePlugin.Companion.HTML_ID
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO

class DesktopImageTypePlugin(
    private val appInfo: AppInfo,
    private val imageWriter: ImageWriter<BufferedImage>,
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

    override fun getPasteType(): PasteType {
        return PasteType.IMAGE_TYPE
    }

    override fun getIdentifiers(): List<String> {
        return listOf(X_JAVA_IMAGE, IMAGE_PNG, IMAGE_JPEG, IMAGE)
    }

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        ImagesPasteItem(
            identifiers = listOf(identifier),
            count = 0,
            hash = "",
            size = 0,
            fileInfoTreeMap = mapOf(),
            relativePathList = listOf(),
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
            image = ImageIO.read(transferData)
        }

        image?.let {
            var name =
                tryGetImageName(dataFlavorMap, pasteTransferable)
                    ?: fileUtils.createRandomFileName(ext = "png")
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
            if (imageWriter.writeImage(image, ext, imagePath)) {
                val fileTree = fileUtils.getFileInfoTree(imagePath)
                val count = fileTree.getCount()
                val size = fileTree.size
                val hash = fileTree.hash

                val update: (PasteItem) -> PasteItem = { pasteItem ->
                    ImagesPasteItem(
                        identifiers = pasteItem.identifiers,
                        count = count,
                        hash = hash,
                        size = size,
                        fileInfoTreeMap = mapOf(name to fileTree),
                        relativePathList = listOf(relativePath),
                        extraInfo = pasteItem.extraInfo,
                    )
                }
                pasteCollector.updateCollectItem(itemIndex, this::class, update)
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
        val doc = Jsoup.parse(imageHtml)

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
        singleType: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as ImagesPasteItem
        val filePaths = pasteItem.getFilePaths(userDataPathProvider)
        val fileList: List<File> = filePaths.map { it.toFile() }
        map[DataFlavor.javaFileListFlavor.toPasteDataFlavor()] = fileList

        if (!singleType) {
            map[PasteDataFlavors.URI_LIST_FLAVOR.toPasteDataFlavor()] =
                ByteArrayInputStream(
                    fileList.joinToString(separator = "\n") {
                        it.absolutePath
                    }.toByteArray(),
                )
            map[DataFlavor.stringFlavor.toPasteDataFlavor()] =
                fileList.joinToString(separator = "\n") {
                    it.name
                }

            if (fileList.size == 1) {
                map[URL_FLAVOR.toPasteDataFlavor()] = fileList[0].toURI().toURL()
                runCatching {
                    val start = System.currentTimeMillis()
                    val image: BufferedImage? = ImageIO.read(fileList[0])
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
            val inputStream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
            map[PasteDataFlavors.GNOME_COPIED_FILES_FLAVOR.toPasteDataFlavor()] = inputStream
        }
    }
}
