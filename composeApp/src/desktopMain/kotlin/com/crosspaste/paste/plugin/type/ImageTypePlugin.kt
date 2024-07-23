package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.image.ImageService.writeImage
import com.crosspaste.paste.DesktopPasteDataFlavor
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.plugin.type.FilesTypePlugin.FilesTypePlugin.FILE_LIST_ID
import com.crosspaste.paste.plugin.type.HtmlTypePlugin.HtmlTypePlugin.HTML_ID
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.DesktopFileUtils.createPastePath
import com.crosspaste.utils.DesktopFileUtils.createPasteRelativePath
import com.crosspaste.utils.DesktopFileUtils.createRandomFileName
import com.crosspaste.utils.DesktopFileUtils.getExtFromFileName
import com.crosspaste.utils.DesktopJsonUtils
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

class ImageTypePlugin(private val appInfo: AppInfo) : PasteTypePlugin {

    companion object ImageItemService {
        const val IMAGE_ID = "image/x-java-image"
    }

    private val logger = KotlinLogging.logger {}

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
            var name = tryGetImageName(dataFlavorMap, pasteTransferable) ?: createRandomFileName(ext = "png")
            val ext =
                getExtFromFileName(name) ?: run {
                    name += ".png"
                    "png"
                }
            val relativePath =
                createPasteRelativePath(
                    appInstanceId = appInfo.appInstanceId,
                    pasteId = pasteId,
                    fileName = name,
                )
            val imagePath = createPastePath(relativePath, isFile = true, AppFileType.IMAGE)
            if (writeImage(image, ext, imagePath.toNioPath())) {
                val fileTree = DesktopFileUtils.getFileInfoTree(imagePath)

                val fileInfoTreeJsonString = DesktopJsonUtils.JSON.encodeToString(mapOf(name to fileTree))
                val count = fileTree.getCount()
                val size = fileTree.size
                val md5 = fileTree.md5

                val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                    realm.query(ImagesPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                        this.relativePathList = realmListOf(relativePath)
                        this.fileInfoTree = fileInfoTreeJsonString
                        this.count = count
                        this.size = size
                        this.md5 = md5
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
            var path: String = url.getPath()
            // 删除路径末尾的斜杠（如果存在）
            path = if (path.endsWith("/")) path.substring(0, path.length - 1) else path
            // 获取最后一个路径部分
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
        val bimage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)

        // Draw the image on to the buffered image
        val bGr = bimage.createGraphics()
        bGr.drawImage(img, 0, 0, null)
        bGr.dispose()

        // Return the buffered image
        return bimage
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as ImagesPasteItem
        val filePaths = pasteItem.getFilePaths()
        val fileList: List<File> = filePaths.map { it.toFile() }
        map[DataFlavor.javaFileListFlavor.toPasteDataFlavor()] = fileList
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
                val image: BufferedImage? = ImageIO.read(fileList[0])
                image?.let { map[DataFlavor.imageFlavor.toPasteDataFlavor()] = it }
            } catch (e: Exception) {
                logger.error(e) { "read image fail" }
            }
        }

        if (currentPlatform().isLinux()) {
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
