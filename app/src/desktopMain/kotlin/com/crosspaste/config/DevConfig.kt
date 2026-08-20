package com.crosspaste.config

import com.crosspaste.utils.DesktopResourceUtils

object DevConfig {

    private val development = DesktopResourceUtils.loadProperties("development.properties")

    val pasteAppPath: String? = development.getProperty("pasteAppPath")

    val pasteUserPath: String? = development.getProperty("pasteUserPath")

    val marketingMode: Boolean = development.getProperty("marketingMode")?.toBoolean() == true

    val pairingV3InteropEnabled: Boolean =
        developmentPairingV3InteropEnabled(
            development.getProperty("pairingV3InteropEnabled"),
        )

    val mouseBinaryPath: String? =
        developmentOptionalProperty(development.getProperty("mouseBinaryPath"))
}

internal fun developmentPairingV3InteropEnabled(configuredValue: String?): Boolean =
    when (configuredValue) {
        null -> true
        else -> configuredValue.toBooleanStrictOrNull() ?: false
    }

internal fun developmentOptionalProperty(configuredValue: String?): String? =
    configuredValue?.trim()?.takeIf {
        it.isNotEmpty()
    }
