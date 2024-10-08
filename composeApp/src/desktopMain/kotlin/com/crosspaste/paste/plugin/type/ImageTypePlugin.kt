package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.image.ImageWriter
import com.crosspaste.paste.DesktopPasteDataFlavor
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.plugin.type.FilesTypePlugin.Companion.FILE_LIST_ID
import com.crosspaste.paste.plugin.type.HtmlTypePlugin.Companion.HTML_ID
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import kotlinx.serialization.encodeToString
import org.jsoup.Jsoup
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO

class ImageTypePlugin(
    private val appInfo: AppInfo,
    private val imageWriter: ImageWriter<BufferedImage>,
    private val userDataPathProvider: UserDataPathProvider,
) : PasteTypePlugin {

    companion object {
        const val IMAGE_ID = "image/x-java-image"
    }

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    private val jsonUtils = getJsonUtils()

    override fun getPasteType(): Int {
        return PasteType.IMAGE
    }

    override fun getIdentifiers(): List<String> {
        return listOf(IMAGE_ID)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        ImagesPasteItem().apply {
            this.identifiers = realmListOf(identifier)
        }.let {
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
        if (transferData is Image) {
            val image: BufferedImage = toBufferedImage(transferData)
            var name =
                tryGetImageName(dataFlavorMap, pasteTransferable)
                    ?: fileUtils.createRandomFileName(ext = "png")
            val ext =
                fileUtils.getExtFromFileName(name) ?: run {
                    name += ".png"
                    "png"
                }
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate =
                        PasteCoordinate(
                            appInstanceId = appInfo.appInstanceId,
                            pasteId = pasteId,
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

                val fileInfoTreeJsonString = jsonUtils.JSON.encodeToString(mapOf(name to fileTree))
                val count = fileTree.getCount()
                val size = fileTree.size
                val hash = fileTree.hash

                val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                    realm.query(ImagesPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                        this.relativePathList = realmListOf(relativePath)
                        this.fileInfoTree = fileInfoTreeJsonString
                        this.count = count
                        this.size = size
                        this.hash = hash
                    }
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
        try {
            val url = URL(urlString)
            var path: String = url.path
            // Remove trailing slash from the path (if it exists)
            path = if (path.endsWith("/")) path.substring(0, path.length - 1) else path
            // Get the last path segment
            val lastSegment = path.substring(path.lastIndexOf('/') + 1)
            return lastSegment
        } catch (e: MalformedURLException) {
            return null
        }
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
                try {
                    val start = System.currentTimeMillis()
                    val image: BufferedImage? = ImageIO.read(fileList[0])
                    image?.let { map[DataFlavor.imageFlavor.toPasteDataFlavor()] = it }
                    val end = System.currentTimeMillis()
                    logger.debug { "read image ${fileList[0].absolutePath} use time: ${end - start} ms" }
                } catch (e: Exception) {
                    logger.error(e) { "read image fail" }
                }
            }
        }

        if (getPlatform().isLinux()) {
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
