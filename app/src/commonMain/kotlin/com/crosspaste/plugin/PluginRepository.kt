package com.crosspaste.plugin

import com.crosspaste.app.AppFileType
import com.crosspaste.db.plugin.PluginState
import com.crosspaste.db.plugin.PluginStateDao
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** A plugin directory as found on disk, with everything the UI and the menu need to know about it. */
data class LocalPlugin(
    val directory: Path,
    /** Null when `manifest.json` is missing or unreadable; [problem] then says why. */
    val manifest: PluginManifest?,
    val problem: PluginProblem?,
    val missingRequirements: List<String>,
    val enabled: Boolean,
) {
    val runnable: Boolean
        get() = enabled && problem == null && missingRequirements.isEmpty()

    fun platform(environment: PluginEnvironment): PluginPlatform? =
        pluginPlatformKey(environment.platform)?.let { manifest?.platforms?.get(it) }
}

/**
 * Scans the plugins directory into [plugins]. Readers (the context menu) only
 * ever see this cached list; disk is touched only by [reload].
 *
 * [findMissingRequirements] probes `requires` executables; platforms that run
 * no external commands keep the default.
 */
class PluginRepository(
    private val environment: PluginEnvironment,
    private val pluginStateDao: PluginStateDao,
    private val userDataPathProvider: UserDataPathProvider,
    private val fileSystem: FileSystem,
    private val findMissingRequirements: (List<String>) -> List<String> = { emptyList() },
) {

    companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"

        private val manifestJson = Json { ignoreUnknownKeys = true }
    }

    private val logger = KotlinLogging.logger {}

    private val reloadMutex = Mutex()

    private val _plugins = MutableStateFlow<List<LocalPlugin>>(emptyList())
    val plugins: StateFlow<List<LocalPlugin>> = _plugins.asStateFlow()

    fun pluginsDirectory(): Path = userDataPathProvider.resolve(appFileType = AppFileType.PLUGIN)

    suspend fun reload() {
        reloadMutex.withLock {
            val states = pluginStateDao.getAll().associateBy { it.id }
            val plugins = withContext(ioDispatcher) { scan(states) }
            _plugins.value = plugins
            logger.info { "Loaded ${plugins.size} plugins, ${plugins.count { it.runnable }} runnable" }
        }
    }

    /**
     * Records the user's decision. Enabling binds consent to the current
     * manifest version: a plugin replaced by another version shows as disabled
     * until the user confirms again.
     */
    suspend fun setEnabled(
        plugin: LocalPlugin,
        enabled: Boolean,
    ) {
        val manifest = plugin.manifest ?: return
        // A duplicate shares its id's state with the first directory; changing it here would toggle that one
        if (plugin.problem is PluginProblem.DuplicateId) return
        // Disabling stays possible so a plugin that broke after being enabled can still be turned off
        if (enabled && plugin.problem != null) return
        reloadMutex.withLock {
            pluginStateDao.upsert(
                PluginState(
                    id = manifest.id,
                    version = manifest.version,
                    enabled = enabled,
                    source = PluginState.SOURCE_LOCAL,
                    installedAt = DateUtils.nowEpochMilliseconds(),
                ),
            )
            _plugins.update { plugins ->
                plugins.map { if (it.directory == plugin.directory) it.copy(enabled = enabled) else it }
            }
        }
    }

    private fun scan(states: Map<String, PluginState>): List<LocalPlugin> {
        val directories =
            runCatching { fileSystem.list(pluginsDirectory()) }
                .onFailure { logger.warn(it) { "Failed to list plugins directory" } }
                .getOrDefault(emptyList())
                .filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
                .sortedBy { it.name }

        val seenIds = mutableSetOf<String>()
        return directories.map { directory ->
            val manifest =
                readManifest(directory).getOrElse { e ->
                    return@map LocalPlugin(
                        directory = directory,
                        manifest = null,
                        problem = PluginProblem.InvalidManifest(e.message ?: e::class.simpleName.orEmpty()),
                        missingRequirements = emptyList(),
                        enabled = false,
                    )
                }
            val problem =
                if (!seenIds.add(manifest.id)) {
                    PluginProblem.DuplicateId(manifest.id)
                } else {
                    checkPluginCompatibility(manifest, environment)
                }
            val state = states[manifest.id]
            val plugin =
                LocalPlugin(
                    directory = directory,
                    manifest = manifest,
                    problem = problem,
                    missingRequirements = emptyList(),
                    enabled = state != null && state.enabled && state.version == manifest.version,
                )
            if (problem == null) {
                plugin.copy(
                    missingRequirements =
                        findMissingRequirements(plugin.platform(environment)!!.requires).distinct(),
                )
            } else {
                plugin
            }
        }
    }

    private fun readManifest(directory: Path): Result<PluginManifest> =
        runCatching {
            val text = fileSystem.read(directory.resolve(MANIFEST_FILE_NAME)) { readUtf8() }
            manifestJson.decodeFromString(PluginManifest.serializer(), text)
        }
}
