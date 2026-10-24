package com.crosspaste.plugin

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.cli.CliPasteDetailDto
import com.crosspaste.cli.toDetailDto
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.UISupport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Runs a process plugin on a paste: the paste goes to stdin in the same JSON
 * shape as `crosspaste paste --json` (with rawContent), and stdout is applied
 * according to the manifest's output.
 */
class ProcessPluginRunner(
    private val appInfo: AppInfo,
    private val appWindowManager: DesktopAppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val environment: PluginEnvironment,
    private val executor: ProcessPluginExecutor,
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val pasteItemReader: PasteItemReader,
    private val pasteReleaseService: PasteReleaseService,
    private val pasteTagDao: PasteTagDao,
    private val uiSupport: UISupport,
    private val userDataPathProvider: UserDataPathProvider,
) {

    companion object {
        private val inputJson = Json { encodeDefaults = true }

        private const val MAX_ERROR_DETAIL_LENGTH = 300
    }

    private val logger = KotlinLogging.logger {}

    suspend fun run(
        plugin: LocalPlugin,
        pasteData: PasteData,
        action: String? = null,
    ) {
        val manifest = plugin.manifest ?: return
        val output = PluginOutput.fromWireName(manifest.output) ?: return
        val name = manifest.displayName(copywriter.language())
        val text = execute(plugin, manifest, name, pasteData, action) ?: return
        applyResult(manifest, name) { applyOutput(name, output, text) }
    }

    /**
     * For the search window: a plugin that replaces the clipboard also pastes its
     * result into the app the window was opened over. Other outputs behave as
     * [run], and a failure leaves the window open so its notification is seen.
     */
    suspend fun runAndPaste(
        plugin: LocalPlugin,
        pasteData: PasteData,
        action: String? = null,
    ) {
        val manifest = plugin.manifest ?: return
        if (PluginOutput.fromWireName(manifest.output) != PluginOutput.REPLACE_CLIPBOARD) {
            run(plugin, pasteData, action)
            return
        }
        val name = manifest.displayName(copywriter.language())
        val text = execute(plugin, manifest, name, pasteData, action) ?: return
        val pasteItem = textPasteItem(text)
        // Hiding the window cancels the caller; once the result exists the paste must still land
        withContext(NonCancellable) {
            applyResult(manifest, name) {
                appWindowManager.hideSearchWindowAndPaste(1) {
                    // Our own pasteboard writes are not captured, so record the paste directly
                    val id = createLocalPaste(pasteItem)
                    pasteboardService
                        .tryWritePasteboard(id = id, pasteItem = pasteItem, localOnly = true)
                        .onFailure { notifyFailure(name, it.message ?: copywriter.getText("copy_failed")) }
                        .isSuccess
                }
            }
        }
    }

    /** The plugin's stdout without trailing newlines, or null once the user has been told why there is none. */
    private suspend fun execute(
        plugin: LocalPlugin,
        manifest: PluginManifest,
        name: String,
        pasteData: PasteData,
        action: String?,
    ): String? {
        val command = plugin.platform(environment)?.command ?: return null
        if (command.isEmpty()) return null

        val result =
            try {
                val input = pasteData.toDetailDto(pasteTagDao, pasteItemReader, true, userDataPathProvider)
                executor.execute(
                    directory = plugin.directory,
                    command = command,
                    input = inputJson.encodeToString(CliPasteDetailDto.serializer(), input),
                    action = action,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Plugin ${manifest.id} failed to start" }
                notifyFailure(name, e.message ?: e::class.simpleName.orEmpty())
                return null
            }

        if (result.stderr.isNotBlank()) {
            logger.info { "Plugin ${manifest.id} stderr:\n${result.stderr}" }
        }
        return when (result.exitCode) {
            null -> {
                logger.warn { "Plugin ${manifest.id} timed out" }
                notifyFailure(name, copywriter.getText("plugin_timed_out"))
                null
            }
            0 -> {
                val text = result.stdout.trimEnd('\n')
                when {
                    result.stdoutTruncated -> {
                        logger.warn { "Plugin ${manifest.id} output exceeded the size limit" }
                        notifyFailure(name, copywriter.getText("plugin_output_too_large"))
                        null
                    }
                    text.isEmpty() -> {
                        notifyFailure(name, copywriter.getText("plugin_no_output"))
                        null
                    }
                    else -> text
                }
            }
            else -> {
                logger.warn { "Plugin ${manifest.id} exited with ${result.exitCode}" }
                notifyFailure(
                    name,
                    result.stderr
                        .trim()
                        .takeLast(MAX_ERROR_DETAIL_LENGTH)
                        .ifEmpty { "exit ${result.exitCode}" },
                )
                null
            }
        }
    }

    private suspend fun applyResult(
        manifest: PluginManifest,
        name: String,
        apply: suspend () -> Unit,
    ) {
        try {
            apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Plugin ${manifest.id} failed to apply output" }
            notifyFailure(name, e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private suspend fun applyOutput(
        name: String,
        output: PluginOutput,
        text: String,
    ) {
        when (output) {
            PluginOutput.REPLACE_CLIPBOARD -> {
                val pasteItem = textPasteItem(text)
                // Our own pasteboard writes are not captured, so record the paste directly
                val id = createLocalPaste(pasteItem)
                pasteboardService
                    .tryWritePasteboard(id = id, pasteItem = pasteItem, localOnly = true)
                    .onSuccess {
                        notificationManager.sendNotification(
                            title = { name },
                            message = { it.getText("copy_successful") },
                            messageType = MessageType.Success,
                        )
                    }.onFailure {
                        notifyFailure(name, it.message ?: copywriter.getText("copy_failed"))
                    }
            }
            PluginOutput.NEW_PASTE -> {
                createLocalPaste(textPasteItem(text))
                notificationManager.sendNotification(
                    title = { name },
                    message = { it.getText("save_successful") },
                    messageType = MessageType.Success,
                )
            }
            PluginOutput.NOTIFICATION ->
                notificationManager.sendNotification(
                    title = { name },
                    message = { text },
                    messageType = MessageType.Info,
                    duration = null,
                )
            PluginOutput.OPEN_URL -> uiSupport.openUrlInBrowser(text.trim())
        }
    }

    private suspend fun createLocalPaste(pasteItem: PasteItem): Long =
        pasteReleaseService.createLocalPasteData(
            appInstanceId = appInfo.appInstanceId,
            pasteItem = pasteItem,
            source = AppName,
        )

    private fun textPasteItem(text: String) =
        createTextPasteItem(
            identifiers = listOf(DesktopTextTypePlugin.TEXT),
            text = text,
        )

    private fun notifyFailure(
        name: String,
        detail: String,
    ) {
        notificationManager.sendNotification(
            title = { it.getText("plugin_run_failed", name) },
            message = { detail },
            messageType = MessageType.Error,
        )
    }
}
