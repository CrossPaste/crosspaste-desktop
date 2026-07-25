package com.crosspaste.config

import com.crosspaste.utils.DesktopResourceUtils

object DevConfig {

    private val development = DesktopResourceUtils.loadProperties("development.properties")

    val pasteAppPath: String? = development.getProperty("pasteAppPath")

    val pasteUserPath: String? = development.getProperty("pasteUserPath")

    val marketingMode: Boolean = development.getProperty("marketingMode")?.toBoolean() == true

    val pairingV3InteropEnabled: Boolean =
        development.getProperty("pairingV3InteropEnabled")?.toBoolean() == true
}
