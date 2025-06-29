package com.crosspaste.rendering

import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.GenerateImageService
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JEditorPane
import javax.swing.text.rtf.RTFEditorKit
import kotlin.math.max

class DesktopRtfRenderingService(
    private val filePersist: FilePersist,
    private val generateImageService: GenerateImageService,
    private val renderingHelper: RenderingHelper,
    private val userDataPathProvider: UserDataPathProvider,
) : RenderingService<String> {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override suspend fun render(pasteData: PasteData) {
        pasteData.getPasteItem(PasteRtf::class)?.let { pasteRtf ->
            val rtf2ImagePath = pasteRtf.getRtfImagePath(userDataPathProvider)
            if (fileUtils.existFile(rtf2ImagePath)) {
                logger.info { "RTF file $rtf2ImagePath exists" }
            } else {
                val editorPane = JEditorPane()
                editorPane.editorKit = RTFEditorKit()
                editorPane.contentType = "text/rtf"
                editorPane.text = pasteRtf.rtf

                val scale = renderingHelper.scale
                val dimension = renderingHelper.dimension

                val scaledWidth = (dimension.width * scale).toInt()
                val scaledHeight = (dimension.height * scale).toInt()

                editorPane.setSize(scaledWidth, Int.MAX_VALUE)
                val preferredSize = editorPane.preferredSize
                val width = max(preferredSize.width, scaledWidth)
                val height = max(preferredSize.height, scaledHeight)
                editorPane.setSize(width, height)

                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                val graphics = image.createGraphics()

                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

                graphics.scale(1.0, 1.0)
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, width, height)
                editorPane.print(graphics)

                graphics.dispose()

                filePersist.createOneFilePersist(rtf2ImagePath)
                    .createEmptyFile()
                ImageIO.write(image, "png", rtf2ImagePath.toFile())
                generateImageService.getGenerateState(rtf2ImagePath).emit(true)
            }
        }
    }

    override fun start() {
    }

    override fun stop() {
    }
}
