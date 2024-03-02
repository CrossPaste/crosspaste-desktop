package com.clipevery.clip.service

import com.clipevery.app.AppFileType
import com.clipevery.app.AppInfo
import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.ImageClipItem
import com.clipevery.clip.service.HtmlItemService.HtmlItemService.HTML_ID
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.DesktopFileUtils.createClipPath
import com.clipevery.utils.DesktopFileUtils.createClipRelativePath
import com.clipevery.utils.DesktopFileUtils.createRandomFileName
import com.clipevery.utils.DesktopFileUtils.getExtFromFileName
import com.clipevery.utils.DesktopFileUtils.getFileMd5
import io.realm.kotlin.MutableRealm
import org.jsoup.Jsoup
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import javax.imageio.ImageIO


class ImageItemService(appInfo: AppInfo) : ClipItemService(appInfo) {

    companion object ImageItemService {
        const val IMAGE_ID = "image/x-java-image"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(IMAGE_ID)
    }

    override fun createPreClipItem(
        clipId: Int,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        ImageClipItem().apply {
            this.identifier = identifier
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        if (transferData is Image) {
           val image: BufferedImage = toBufferedImage(transferData)
           var name = tryGetImageName(dataFlavorMap, transferable) ?: createRandomFileName(ext = "png")
           val ext = getExtFromFileName(name) ?: run {
               name += ".png"
               "png"
           }
           val relativePath = createClipRelativePath(appInfo.appInstanceId, clipId, name)
           val imagePath = createClipPath(relativePath, isFile = true, AppFileType.IMAGE)
           if (writeImage(image, ext, imagePath)) {
               val md5 = getFileMd5(imagePath)
               val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
                   realm.query(ImageClipItem::class).query("id == $0", clipItem.id).first().find()?.apply {
                       this.relativePath = relativePath
                       this.md5 = md5
                   }
               }
                clipCollector.updateCollectItem(itemIndex, this::class, update)
           }
        }
    }

    private fun writeImage(image: BufferedImage, ext: String, imagePath: Path): Boolean {
        if (!ImageIO.write(image, ext, imagePath.toFile())) {
            val convertedImage =
                BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

            val g2d = convertedImage.createGraphics()
            g2d.drawImage(image, 0, 0, null)
            g2d.dispose()

            return ImageIO.write(convertedImage, ext, imagePath.toFile())
        } else {
            return true
        }
    }

    private fun tryGetImageName(dataFlavorMap: Map<String, List<DataFlavor>>,
                                transferable: Transferable): String? {
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