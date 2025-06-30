package com.crosspaste.rendering

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.GenerateImageService
import com.crosspaste.image.ImageWriter
import com.crosspaste.net.DesktopClient
import com.crosspaste.paste.item.PasteItem.Companion.updateExtraInfo
import com.crosspaste.paste.item.PasteItemProperties.TITLE
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.fleeksoft.ksoup.Ksoup
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.put
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class DesktopOpenGraphService(
    private val generateImageService: GenerateImageService,
    private val imageWriter: ImageWriter<BufferedImage>,
    private val pasteDao: PasteDao,
    private val userDataPathProvider: UserDataPathProvider,
) : RenderingService<String> {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override suspend fun render(pasteData: PasteData) {
        pasteData.getPasteItem(UrlPasteItem::class)?.let { urlPasteItem ->
            val openGraphImage =
                urlPasteItem.getRenderingFilePath(
                    pasteData.getPasteCoordinate(),
                    userDataPathProvider,
                )

            if (fileUtils.existFile(openGraphImage)) {
                logger.info { "Open graph image file exists" }
            } else {
                DesktopClient.suspendRequest(urlPasteItem.url) { response ->
                    val bytes = response.body().readBytes()
                    val html = String(bytes)
                    val doc = Ksoup.parse(html)

                    val htmlTitle =
                        doc.select("title").firstOrNull()?.text()
                            ?: doc.select("meta[property=og:title]").firstOrNull()?.attr("content")

                    htmlTitle?.let { title ->
                        val newUrlPasteItem =
                            UrlPasteItem(
                                identifiers = urlPasteItem.identifiers,
                                hash = urlPasteItem.hash,
                                size = urlPasteItem.size,
                                url = urlPasteItem.url,
                                extraInfo =
                                    updateExtraInfo(
                                        urlPasteItem.extraInfo,
                                        update = {
                                            put(TITLE, title)
                                        },
                                    ),
                            )
                        pasteDao.updatePasteAppearItem(pasteData.id, newUrlPasteItem)
                    }

                    val ogImage = doc.select("meta[property=og:image]").firstOrNull()?.attr("content")

                    ogImage?.let { imageUrl ->
                        DesktopClient.suspendRequest(imageUrl) { imageResponse ->
                            val inputStream = imageResponse.body()
                            val image = ImageIO.read(inputStream)
                            imageWriter.writeImage(image, "png", openGraphImage)
                            generateImageService.getGenerateState(openGraphImage).emit(true)
                        }
                    } ?: run {
                        logger.warn { "No Open Graph image found for URL: ${urlPasteItem.url}" }
                    }
                }
            }
        }
    }

    override fun start() {
    }

    override fun stop() {
    }
}
