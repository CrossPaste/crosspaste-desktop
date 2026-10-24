package com.crosspaste.config

import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.getAppEnvUtils
import io.github.oshai.kotlinlogging.KotlinLogging

object DevConfig {

    private val logger = KotlinLogging.logger {}

    // Release jars exclude development.properties, so a missing file means no overrides.
    private val development = DesktopResourceUtils.loadOptionalProperties("development.properties")

    init {
        if (development.isEmpty && getAppEnvUtils().isDevelopment()) {
            logger.warn {
                "development.properties is missing; DevConfig overrides are disabled. " +
                    "A development build normally restores it from development.properties.template."
            }
        }
    }

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
