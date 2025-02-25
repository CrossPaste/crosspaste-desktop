package com.crosspaste.rendering

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppSize
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment

class DesktopRenderingHelper(private val appSize: AppSize) : RenderingHelper {

    private val globalDensity =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .density

    private val GraphicsConfiguration.density: Density
        get() =
            Density(
                defaultTransform.scaleX.toFloat(),
                fontScale = 1f,
            )

    override var scale: Double = globalDensity.density.toDouble()

    override var dimension: RenderingDimension = readWindowDimension()

    private fun readScale(): Double {
        return globalDensity.density.toDouble()
    }

    private fun readWindowDimension(): RenderingDimension {
        val detailViewDpSize = appSize.searchWindowDetailViewDpSize
        val htmlWidthValue = detailViewDpSize.width - 20.dp
        val htmlHeightValue = detailViewDpSize.height - 20.dp
        val width: Int = htmlWidthValue.value.toInt()
        val height: Int = htmlHeightValue.value.toInt()
        return RenderingDimension(width, height)
    }

    override fun refresh() {
        scale = readScale()
        dimension = readWindowDimension()
    }
}
