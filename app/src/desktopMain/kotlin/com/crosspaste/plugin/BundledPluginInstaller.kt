package com.crosspaste.plugin

import com.crosspaste.utils.DesktopResourceUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.FileSystem
import okio.Path

/** A sample plugin shipped as app resources under `plugins/<directory>/`. */
class BundledPlugin(
    val directory: String,
    val files: List<String>,
)

/**
 * Copies the plugins shipped inside the app into the plugins directory, once per
 * plugin: a plugin the user deleted or edited is never restored or overwritten.
 * Copied plugins start disabled like any other.
 */
class BundledPluginInstaller(
    private val pluginsDirectory: () -> Path,
    private val fileSystem: FileSystem,
) {

    companion object {
        /** Lists the bundled plugin directories already copied once; the scanner ignores plain files. */
        const val INSTALLED_MARKER = ".bundled"

        val BUNDLED_PLUGINS =
            listOf(
                BundledPlugin("translate", listOf("manifest.json", "run.js", "run.py", "run.ps1")),
            )
    }

    private val logger = KotlinLogging.logger {}

    fun install() {
        runCatching {
            val pluginsDir = pluginsDirectory()
            val marker = pluginsDir.resolve(INSTALLED_MARKER)
            val installed =
                if (fileSystem.exists(marker)) {
                    fileSystem
                        .read(marker) { readUtf8() }
                        .lines()
                        .filter { it.isNotBlank() }
                        .toSet()
                } else {
                    emptySet()
                }
            val pending = BUNDLED_PLUGINS.filter { it.directory !in installed }
            if (pending.isEmpty()) return

            fileSystem.createDirectories(pluginsDir)
            pending.forEach { plugin ->
                val target = pluginsDir.resolve(plugin.directory)
                // A folder the user already has under this name is theirs; leave it alone
                if (!fileSystem.exists(target)) {
                    copy(plugin, target)
                }
            }
            fileSystem.write(marker) {
                writeUtf8((installed + pending.map { it.directory }).joinToString("\n"))
            }
        }.onFailure { logger.warn(it) { "Failed to install bundled plugins" } }
    }

    private fun copy(
        plugin: BundledPlugin,
        target: Path,
    ) {
        fileSystem.createDirectories(target)
        plugin.files.forEach { file ->
            val bytes = DesktopResourceUtils.readResourceBytes("plugins/${plugin.directory}/$file")
            fileSystem.write(target.resolve(file)) { write(bytes) }
        }
        logger.info { "Installed bundled plugin ${plugin.directory}" }
    }
}
