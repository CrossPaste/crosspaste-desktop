package com.crosspaste.clip.service

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.clip.ClipCollector
import com.crosspaste.clip.ClipItemService
import com.crosspaste.clip.item.ImagesClipItem
import com.crosspaste.clip.service.HtmlItemService.HtmlItemService.HTML_ID
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.image.ImageService.writeImage
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.DesktopFileUtils.createClipPath
import com.crosspaste.utils.DesktopFileUtils.createClipRelativePath
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

class ImageItemService(appInfo: AppInfo) : ClipItemService(appInfo) {

    companion object ImageItemService {
        const val IMAGE_ID = "image/x-java-image"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(IMAGE_ID)
    }

    override fun createPreClipItem(
        clipId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        ImagesClipItem().apply {
            this.identifiers = realmListOf(identifier)
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        clipId: Long,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector,
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
                createClipRelativePath(
                    appInstanceId = appInfo.appInstanceId,
                    clipId = clipId,
                    fileName = name,
                )
            val imagePath = createClipPath(relativePath, isFile = true, AppFileType.IMAGE)
            if (writeImage(image, ext, imagePath)) {
                val fileTree = DesktopFileUtils.getFileInfoTree(imagePath)

                val fileInfoTreeJsonString = DesktopJsonUtils.JSON.encodeToString(mapOf(name to fileTree))
                val count = fileTree.getCount()
                val size = fileTree.size
                val md5 = fileTree.md5

                val update: (ClipItem, MutableRealm) -> Unit = { clipItem, realm ->
                    realm.query(ImagesClipItem::class, "id == $0", clipItem.id).first().find()?.apply {
                        this.relativePathList = realmListOf(relativePath)
                        this.fileInfoTree = fileInfoTreeJsonString
                        this.count = count
                        this.size = size
                        this.md5 = md5
                    }
                }
                clipCollector.updateCollectItem(itemIndex, this::class, update)
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