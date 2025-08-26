package com.crosspaste.rendering

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.image.GenerateImageService
import com.crosspaste.image.ImageWriter
import com.crosspaste.net.ResourcesClient
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.PasteItem.Companion.updateExtraInfo
import com.crosspaste.paste.item.PasteItemProperties.TITLE
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.put
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class DesktopOpenGraphService(
    private val generateImageService: GenerateImageService,
    private val imageWriter: ImageWriter<BufferedImage>,
    private val resourcesClient: ResourcesClient,
    private val pasteDao: PasteDao,
    private val searchContentService: SearchContentService,
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
                resourcesClient.suspendRequest(urlPasteItem.url) { response ->
                    val bytes = response.getBody().toInputStream().readBytes()
                    val html = String(bytes)
                    val doc = Ksoup.parse(html)

                    val htmlTitle =
                        sequenceOf(
                            { doc.select("meta[property=og:title]").firstOrNull()?.attr("content") },
                            { doc.select("meta[name=og:title]").firstOrNull()?.attr("content") },
                            { doc.select("meta[name=twitter:title]").firstOrNull()?.attr("content") },
                            { doc.select("meta[property=twitter:title]").firstOrNull()?.attr("content") },
                            { doc.select("title").firstOrNull()?.text() },
                            { doc.select("meta[name=title]").firstOrNull()?.attr("content") },
                            { doc.select("meta[itemprop=name]").firstOrNull()?.attr("content") },
                            { doc.select("h1").firstOrNull()?.text() },
                        ).mapNotNull { it() }.firstOrNull { it.isNotBlank() }

                    htmlTitle?.let { title ->
                        val extraInfo =
                            updateExtraInfo(
                                urlPasteItem.extraInfo,
                                update = {
                                    put(TITLE, title)
                                },
                            )
                        val newUrlPasteItem =
                            UrlPasteItem(
                                identifiers = urlPasteItem.identifiers,
                                hash = urlPasteItem.hash,
                                size = urlPasteItem.size + title.length,
                                url = urlPasteItem.url,
                                extraInfo = extraInfo,
                            )
                        pasteDao.updatePasteAppearItem(
                            id = pasteData.id,
                            pasteItem = newUrlPasteItem,
                            pasteSearchContent =
                                searchContentService.createSearchContent(
                                    pasteData.source,
                                    newUrlPasteItem.getSearchContent(),
                                ),
                            addedSize = title.length.toLong(),
                        )
                    }

                    val ogImage =
                        sequenceOf(
                            // 1. Open Graph Protocol (most common)
                            { doc.select("meta[property=og:image]").firstOrNull()?.attr("content") },
                            { doc.select("meta[property=og:image:url]").firstOrNull()?.attr("content") },
                            { doc.select("meta[property=og:image:secure_url]").firstOrNull()?.attr("content") },
                            // 2. Twitter Card
                            { doc.select("meta[name=twitter:image]").firstOrNull()?.attr("content") },
                            { doc.select("meta[property=twitter:image]").firstOrNull()?.attr("content") },
                            { doc.select("meta[name=twitter:image:src]").firstOrNull()?.attr("content") },
                            // 3. Schema.org / JSON-LD
                            { extractFromJsonLd(doc) },
                            // 4. Standard HTML <meta> tags
                            { doc.select("meta[name=image]").firstOrNull()?.attr("content") },
                            { doc.select("meta[itemprop=image]").firstOrNull()?.attr("content") },
                            { doc.select("meta[name=thumbnail]").firstOrNull()?.attr("content") },
                            { doc.select("meta[name=thumbnailUrl]").firstOrNull()?.attr("content") },
                            // 5. <link> tags
                            { doc.select("link[rel=image_src]").firstOrNull()?.attr("href") },
                            { doc.select("link[rel=apple-touch-icon]").firstOrNull()?.attr("href") },
                            {
                                doc
                                    .select("link[rel=icon]")
                                    .firstOrNull {
                                        it.attr("sizes").contains("192") || it.attr("sizes").contains("512")
                                    }?.attr("href")
                            },
                            // 6. Article-specific selectors
                            { doc.select("article img").firstOrNull()?.attr("src") },
                            { doc.select("main img").firstOrNull()?.attr("src") },
                            { doc.select(".post img").firstOrNull()?.attr("src") },
                            { doc.select(".content img").firstOrNull()?.attr("src") },
                            // 7. Common hero/banner images
                            { doc.select(".hero img").firstOrNull()?.attr("src") },
                            { doc.select(".banner img").firstOrNull()?.attr("src") },
                            { doc.select("header img").firstOrNull()?.attr("src") },
                            // 8. The largest image on the page (final fallback)
                            { findLargestImage(doc) },
                        ).mapNotNull { it() }.firstOrNull { it.isNotBlank() }

                    ogImage?.let { imageUrl ->
                        resourcesClient.suspendRequest(imageUrl) { imageResponse ->
                            val inputStream = imageResponse.getBody().toInputStream()
                            val image = ImageIO.read(inputStream)
                            imageWriter.writeImage(image, "png", openGraphImage)
                            generateImageService.markGenerationComplete(openGraphImage)
                        }
                    } ?: run {
                        logger.warn { "No Open Graph image found for URL: ${urlPasteItem.url}" }
                    }
                }
            }
        }
    }

    private fun extractFromJsonLd(doc: Document): String? {
        val jsonLdScripts = doc.select("script[type=application/ld+json]")

        for (script in jsonLdScripts) {
            runCatching {
                val json = script.data()
                val imagePattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
                val match = imagePattern.find(json)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        }
        return null
    }

    private fun findLargestImage(doc: Document): String? =
        doc
            .select("img[src]")
            .filter { img ->
                val src = img.attr("src")
                !src.contains("pixel") &&
                    !src.contains("tracking") &&
                    !src.contains("1x1") &&
                    !src.endsWith(".gif")
            }.maxByOrNull { img ->
                val width = img.attr("width").toIntOrNull() ?: 0
                val height = img.attr("height").toIntOrNull() ?: 0
                width * height
            }?.attr("src")

    override fun start() {
    }

    override fun stop() {
    }
}
