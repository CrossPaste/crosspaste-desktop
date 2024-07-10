package com.crosspaste.paste.service

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.image.ImageService.writeImage
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteItemService
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.service.HtmlItemService.HtmlItemService.HTML_ID
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.DesktopFileUtils.createPastePath
import com.crosspaste.utils.DesktopFileUtils.createPasteRelativePath
import com.crosspaste.utils.DesktopFileUtils.createRandomFileName
import com.crosspaste.utils.DesktopFileUtils.getExtFromFileName
import com.crosspaste.utils.DesktopJsonUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import kotlinx.serialization.encodeToString
import org.jsoup.Jsoup
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.net.MalformedURLException
import java.net.URL

class ImageItemService(appInfo: AppInfo) : PasteItemService(appInfo) {

    companion object ImageItemService {
        const val IMAGE_ID = "image/x-java-image"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(IMAGE_ID)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
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
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is Image) {
            val image: BufferedImage = toBufferedImage(transferData)
            var name = tryGetImageName(dataFlavorMap, transferable) ?: createRandomFileName(ext = "png")
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
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
    ): String? {
        dataFlavorMap[HTML_ID]?.let {
            for (dataFlavor in it) {
                if (dataFlavor.representationClass == String::class.java) {
                    (transferable.getTransferData(dataFlavor) as? String)?.let { imageHtml ->
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
}
