package com.crosspaste.config

import com.crosspaste.utils.DesktopResourceUtils

object DevConfig {

    private val development = DesktopResourceUtils.loadProperties("development.properties")

    val pasteAppPath: String? = development.getProperty("pasteAppPath")

    val pasteUserPath: String? = development.getProperty("pasteUserPath")

    val marketingMode: Boolean = development.getProperty("marketingMode")?.toBoolean() == true

    /**
     * Optional dev override for the CLI executable shown/probed by
     * CliSymlinkService; when unset the conventional cli/dist output is used.
     * A configured-but-missing path fails fast at first use.
     */
    val cliBinaryPath: String? = development.getProperty("cliBinaryPath")

    val pairingV3InteropEnabled: Boolean =
        developmentPairingV3InteropEnabled(
            development.getProperty("pairingV3InteropEnabled"),
        )
}

internal fun developmentPairingV3InteropEnabled(configuredValue: String?): Boolean =
    when (configuredValue) {
        null -> true
        else -> configuredValue.toBooleanStrictOrNull() ?: false
    }
