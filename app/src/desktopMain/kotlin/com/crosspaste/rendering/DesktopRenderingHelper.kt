package com.crosspaste.rendering

import androidx.compose.ui.unit.Density
import com.crosspaste.app.AppSize
import com.crosspaste.ui.theme.AppUISize.large2X
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
        val htmlWidthValue = detailViewDpSize.width - large2X
        val htmlHeightValue = detailViewDpSize.height - large2X
        val width: Int = htmlWidthValue.value.toInt()
        val height: Int = htmlHeightValue.value.toInt()
        return RenderingDimension(width, height)
    }

    override fun refresh() {
        scale = readScale()
        dimension = readWindowDimension()
    }
}
