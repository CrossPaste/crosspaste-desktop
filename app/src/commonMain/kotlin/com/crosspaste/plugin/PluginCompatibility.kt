package com.crosspaste.plugin

import com.crosspaste.platform.Platform
import io.github.z4kn4fein.semver.toVersionOrNull
import io.github.z4kn4fein.semver.withoutSuffixes

/** What the running app offers to plugins; the same values drive install, listing and the menu. */
data class PluginEnvironment(
    val platform: Platform,
    val appVersion: String,
    val runtimes: Set<String>,
) {
    companion object {
        const val RUNTIME_PROCESS = "process"
    }
}

/** Why a plugin cannot run here. Rendered by the UI, so each case carries its own detail. */
sealed interface PluginProblem {
    data class InvalidManifest(
        val detail: String,
    ) : PluginProblem

    data class DuplicateId(
        val id: String,
    ) : PluginProblem

    data class UnsupportedPlatform(
        val os: String,
    ) : PluginProblem

    data class UnsupportedArch(
        val arch: String,
    ) : PluginProblem

    data class AppTooOld(
        val minAppVersion: String,
    ) : PluginProblem

    data class UnsupportedRuntime(
        val runtime: String,
    ) : PluginProblem

    data class UnsupportedOutput(
        val output: String,
    ) : PluginProblem
}

private val PLUGIN_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

/** Manifest key for a [Platform.name]; null for platforms that never run plugins. */
fun pluginPlatformKey(platform: Platform): String? =
    when {
        platform.isMacos() -> "macos"
        platform.isWindows() -> "windows"
        platform.isLinux() -> "linux"
        platform.isIphone() || platform.isIpad() -> "ios"
        platform.isAndroid() -> "android"
        else -> null
    }

/** Maps the raw `os.arch`-style values (`aarch64`, `amd64`, `x86_64`) onto the manifest's `arm64` / `x64`. */
fun normalizePluginArch(arch: String): String =
    when (val lower = arch.lowercase()) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64", "x64" -> "x64"
        else -> lower
    }

/**
 * The single compatibility check shared by listing, install and the menu.
 * Returns null when [manifest] can run in [environment]. Missing `requires`
 * executables are probed separately, since that needs the file system.
 */
fun checkPluginCompatibility(
    manifest: PluginManifest,
    environment: PluginEnvironment,
): PluginProblem? {
    validateManifest(manifest)?.let { return it }
    if (manifest.runtime !in environment.runtimes) {
        return PluginProblem.UnsupportedRuntime(manifest.runtime)
    }
    val platformKey = pluginPlatformKey(environment.platform)
    val platform =
        platformKey?.let { manifest.platforms[it] }
            ?: return PluginProblem.UnsupportedPlatform(environment.platform.displayName())
    val arch = normalizePluginArch(environment.platform.arch)
    if (platform.arch != null && platform.arch.none { normalizePluginArch(it) == arch }) {
        return PluginProblem.UnsupportedArch(arch)
    }
    if (platform.command.isEmpty() || platform.command.firstOrNull()?.isBlank() == true) {
        return PluginProblem.InvalidManifest("platforms.$platformKey.command is empty")
    }
    manifest.minAppVersion?.let { minAppVersion ->
        val required = minAppVersion.toVersionOrNull(strict = false)
        val current = environment.appVersion.toVersionOrNull(strict = false)?.withoutSuffixes()
        // An unparsable app version (dev builds) never blocks a plugin
        if (required != null && current != null && current < required) {
            return PluginProblem.AppTooOld(minAppVersion)
        }
    }
    if (PluginOutput.fromWireName(manifest.output) == null) {
        return PluginProblem.UnsupportedOutput(manifest.output)
    }
    return null
}

private fun validateManifest(manifest: PluginManifest): PluginProblem? {
    val invalidActionId = manifest.actions.firstOrNull { !PLUGIN_ID_REGEX.matches(it.id) }?.id
    return when {
        !PLUGIN_ID_REGEX.matches(manifest.id) -> PluginProblem.InvalidManifest("invalid id: ${manifest.id}")
        manifest.version.toVersionOrNull(strict = false) == null ->
            PluginProblem.InvalidManifest("invalid version: ${manifest.version}")
        manifest.minAppVersion != null && manifest.minAppVersion.toVersionOrNull(strict = false) == null ->
            PluginProblem.InvalidManifest("invalid minAppVersion: ${manifest.minAppVersion}")
        manifest.supportedPasteTypes().isEmpty() ->
            PluginProblem.InvalidManifest("no supported pasteTypes")
        manifest.actions.any { it.id.isBlank() } -> PluginProblem.InvalidManifest("blank action id")
        invalidActionId != null -> PluginProblem.InvalidManifest("invalid action id: $invalidActionId")
        manifest.actions.distinctBy { it.id }.size != manifest.actions.size ->
            PluginProblem.InvalidManifest("duplicate action id")
        manifest.actions.any { it.name.isEmpty() } -> PluginProblem.InvalidManifest("action without name")
        manifest.actions.any { it.name.values.any { v -> v.isBlank() } } ->
            PluginProblem.InvalidManifest("blank action name")
        else -> null
    }
}
