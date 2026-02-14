package com.crosspaste.headless

import com.crosspaste.paste.PasteData
import com.crosspaste.ui.base.UISupport
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path

class HeadlessUISupport : UISupport {

    private val logger = KotlinLogging.logger {}

    override fun openUrlInBrowser(url: String) {
        logger.info { "Headless mode: cannot open URL in browser: $url" }
    }

    override fun getCrossPasteWebUrl(path: String): String = "https://crosspaste.com/$path"

    override fun openEmailClient(email: String?) {
        logger.info { "Headless mode: cannot open email client" }
    }

    override fun openHtml(
        id: Long,
        html: String,
    ) {
        logger.info { "Headless mode: cannot open HTML viewer" }
    }

    override fun browseFile(filePath: Path) {
        logger.info { "Headless mode: cannot browse file: $filePath" }
    }

    override fun openColorPicker(pasteData: PasteData) {
        logger.info { "Headless mode: cannot open color picker" }
    }

    override fun openImage(imagePath: Path) {
        logger.info { "Headless mode: cannot open image: $imagePath" }
    }

    override fun openText(pasteData: PasteData) {
        logger.info { "Headless mode: cannot open text viewer" }
    }

    override fun openRtf(pasteData: PasteData) {
        logger.info { "Headless mode: cannot open RTF viewer" }
    }

    override fun openPasteData(
        pasteData: PasteData,
        index: Int,
    ) {
        logger.info { "Headless mode: cannot open paste data viewer" }
    }

    override fun jumpPrivacyAccessibility() {
        logger.info { "Headless mode: cannot open privacy accessibility settings" }
    }
}
