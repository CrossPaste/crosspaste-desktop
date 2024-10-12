package com.crosspaste.rendering

import com.crosspaste.presist.FilePersist
import okio.Path
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JEditorPane
import javax.swing.text.rtf.RTFEditorKit
import kotlin.math.max

class DesktopRtfRenderingService(
    private val filePersist: FilePersist,
    private val renderingHelper: RenderingHelper,
) : RenderingService<String> {

    @Synchronized
    override fun saveRenderImage(
        input: String,
        savePath: Path,
    ) {
        val editorPane = JEditorPane()
        editorPane.editorKit = RTFEditorKit()
        editorPane.contentType = "text/rtf"
        editorPane.text = input

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

        graphics.scale(scale, scale)
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        editorPane.print(graphics)

        graphics.dispose()

        filePersist.createOneFilePersist(savePath)
            .createEmptyFile()
        ImageIO.write(image, "png", savePath.toFile())
    }

    override fun start() {
    }

    override fun stop() {
    }
}
