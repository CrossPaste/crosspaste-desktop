package com.crosspaste.plugin

import com.crosspaste.app.AppInfo
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
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.UISupport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Runs a process plugin on a paste: the paste goes to stdin in the same JSON
 * shape as `crosspaste paste --json` (with rawContent), and stdout is applied
 * according to the manifest's output.
 */
class ProcessPluginRunner(
    private val appInfo: AppInfo,
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
    ) {
        val manifest = plugin.manifest ?: return
        val command = plugin.platform(environment)?.command ?: return
        val output = PluginOutput.fromWireName(manifest.output) ?: return
        val name = manifest.displayName(copywriter.language())

        val result =
            try {
                val input = pasteData.toDetailDto(pasteTagDao, pasteItemReader, true, userDataPathProvider)
                executor.execute(
                    directory = plugin.directory,
                    command = command,
                    input = inputJson.encodeToString(CliPasteDetailDto.serializer(), input),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Plugin ${manifest.id} failed to start" }
                notifyFailure(name, e.message ?: e::class.simpleName.orEmpty())
                return
            }

        if (result.stderr.isNotBlank()) {
            logger.info { "Plugin ${manifest.id} stderr:\n${result.stderr}" }
        }
        when (result.exitCode) {
            null -> {
                logger.warn { "Plugin ${manifest.id} timed out" }
                notifyFailure(name, copywriter.getText("plugin_timed_out"))
            }
            0 -> applyOutput(manifest, name, output, result.stdout.trimEnd('\n'))
            else -> {
                logger.warn { "Plugin ${manifest.id} exited with ${result.exitCode}" }
                notifyFailure(
                    name,
                    result.stderr
                        .trim()
                        .takeLast(MAX_ERROR_DETAIL_LENGTH)
                        .ifEmpty { "exit ${result.exitCode}" },
                )
            }
        }
    }

    private suspend fun applyOutput(
        manifest: PluginManifest,
        name: String,
        output: PluginOutput,
        text: String,
    ) {
        if (text.isEmpty()) {
            notifyFailure(name, copywriter.getText("plugin_no_output"))
            return
        }
        when (output) {
            PluginOutput.REPLACE_CLIPBOARD ->
                pasteboardService
                    .tryWritePasteboard(pasteItem = textPasteItem(text), localOnly = false)
                    .onSuccess {
                        notificationManager.sendNotification(
                            title = { name },
                            message = { it.getText("copy_successful") },
                            messageType = MessageType.Success,
                        )
                    }
            PluginOutput.NEW_PASTE ->
                pasteReleaseService.createLocalPasteData(
                    appInstanceId = appInfo.appInstanceId,
                    pasteItem = textPasteItem(text),
                    source = manifest.id,
                )
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
