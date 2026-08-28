package com.crosspaste.cli

import com.crosspaste.app.AppInfo
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteContentEditor
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.item.clearRenderingFiles
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

private val configJson = Json { encodeDefaults = true }

private val cliLogger = KotlinLogging.logger("com.crosspaste.cli.CliRouting")

fun Routing.cliRouting(
    appInfo: AppInfo,
    cliPairingService: CliPairingService,
    configManager: DesktopConfigManager,
    pasteContentEditor: PasteContentEditor,
    taskSubmitter: TaskSubmitter,
    /**
     * Whether re-copying an existing paste to the system clipboard actually
     * works here. The headless daemon's PasteboardService reports success
     * without touching any clipboard, so the copy-by-id endpoint must refuse
     * instead of lying (plain POST /cli/copy keeps its store-and-sync
     * semantics in both modes).
     */
    supportsPasteCopy: Boolean,
    pasteboardService: PasteboardService,
    pasteDao: PasteDao,
    pasteDataHelper: PasteDataHelper,
    pasteItemReader: PasteItemReader,
    pasteReleaseService: PasteReleaseService,
    pasteTagDao: PasteTagDao,
    searchContentService: SearchContentService,
    syncRuntimeInfoDao: SyncRuntimeInfoDao,
    userDataPathProvider: UserDataPathProvider,
) {
    route("/cli") {
        get("/status") {
            handleStatus(call, appInfo, configManager, pasteDao, syncRuntimeInfoDao)
        }

        get("/paste/latest") {
            respondPasteDetail(
                call,
                pasteDao.getLatestLoadedPasteData(),
                pasteTagDao,
                pasteItemReader,
                userDataPathProvider,
            )
        }

        get("/paste/{id}") {
            val id = call.requireLongParameter("id", "Invalid paste id.") ?: return@get
            respondPasteDetail(
                call,
                pasteDao.getNoDeletePasteData(id),
                pasteTagDao,
                pasteItemReader,
                userDataPathProvider,
            )
        }

        get("/paste/{id}/image") {
            val id = call.requireLongParameter("id", "Invalid paste id.") ?: return@get
            handlePasteImage(call, id, pasteDao, userDataPathProvider)
        }

        post("/paste/{id}/copy") {
            if (!supportsPasteCopy) {
                call.respond(
                    HttpStatusCode.NotImplemented,
                    CliMessageDto(
                        "CrossPaste is running headless; copying a paste to the " +
                            "system clipboard requires the desktop app.",
                    ),
                )
                return@post
            }
            val id = call.requireLongParameter("id", "Invalid paste id.") ?: return@post
            handlePasteCopy(call, id, pasteDao, pasteboardService)
        }

        get("/history") {
            handleList(
                call = call,
                searchContentService = searchContentService,
                pasteDao = pasteDao,
                pasteDataHelper = pasteDataHelper,
                pasteTagDao = pasteTagDao,
            )
        }

        get("/watch") {
            handleWatch(
                call = call,
                pasteDao = pasteDao,
                pasteDataHelper = pasteDataHelper,
                pasteTagDao = pasteTagDao,
            )
        }

        get("/devices") {
            handleDevices(call, syncRuntimeInfoDao)
        }

        get("/tags") {
            handleTagList(call, pasteTagDao)
        }

        post("/tags") {
            handleTagCreate(call, pasteTagDao)
        }

        delete("/tags/{id}") {
            val id = call.requireLongParameter("id", "Invalid tag id.") ?: return@delete
            withContext(ioDispatcher) { pasteTagDao.deletePasteTagBlock(id) }
            call.respond(CliMessageDto("Tag #$id deleted."))
        }

        get("/config") {
            call.respond(configManager.getCurrentConfig())
        }

        put("/config") {
            handleConfigSet(call, configManager)
        }

        post("/copy") {
            handleCopy(call, appInfo, configManager, pasteDao, pasteReleaseService, pasteboardService)
        }

        delete("/paste/{id}") {
            val id = call.requireLongParameter("id", "Invalid paste id.") ?: return@delete
            handlePasteDelete(call, id, pasteDao)
        }

        put("/paste/{id}") {
            val id = call.requireLongParameter("id", "Invalid paste id.") ?: return@put
            handlePasteUpdate(
                call,
                id,
                configManager,
                pasteDao,
                pasteContentEditor,
                taskSubmitter,
                userDataPathProvider,
            )
        }

        route("/pair") {
            get("/nearby") {
                handlePairNearby(call, cliPairingService)
            }

            get("/token") {
                // `?arm=start|renew` lets `crosspaste token --wait` open/extend the
                // v3 acceptance window (see CliPairingService.armAcceptanceWindow);
                // a plain GET stays a pure read.
                CliTokenArm.fromQuery(call.request.queryParameters["arm"])?.let { arm ->
                    cliPairingService.armAcceptanceWindow(arm)
                }
                call.respond(cliPairingService.pairingToken())
            }

            post("/initiate") {
                handlePairInitiate(call, cliPairingService)
            }

            post("/submit") {
                handlePairSubmit(call, cliPairingService)
            }

            post("/cancel") {
                handlePairCancel(call, cliPairingService)
            }
        }
    }
}

private suspend fun handlePairNearby(
    call: ApplicationCall,
    cliPairingService: CliPairingService,
) {
    val refresh = call.request.queryParameters["refresh"] == "true"
    call.respond(cliPairingService.nearbyDevices(refresh))
}

private suspend fun handlePairInitiate(
    call: ApplicationCall,
    cliPairingService: CliPairingService,
) {
    val request = call.receive<CliPairInitiateRequest>()
    if (request.appInstanceId.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, CliMessageDto("appInstanceId must not be blank."))
        return
    }
    when (val outcome = cliPairingService.initiate(request.appInstanceId)) {
        is CliPairingService.InitiateOutcome.Started ->
            call.respond(outcome.session)

        is CliPairingService.InitiateOutcome.DeviceNotFound ->
            call.respond(HttpStatusCode.NotFound, CliMessageDto(outcome.message))

        is CliPairingService.InitiateOutcome.AlreadyPaired ->
            call.respond(HttpStatusCode.Conflict, CliMessageDto(outcome.message))

        is CliPairingService.InitiateOutcome.UnsupportedPeer ->
            call.respond(HttpStatusCode.BadRequest, CliMessageDto(outcome.message))

        is CliPairingService.InitiateOutcome.Unavailable ->
            call.respond(HttpStatusCode.BadGateway, CliMessageDto(outcome.message))
    }
}

private suspend fun handlePairSubmit(
    call: ApplicationCall,
    cliPairingService: CliPairingService,
) {
    val request = call.receive<CliPairSubmitRequest>()
    when (val outcome = cliPairingService.submit(request.sessionId, request.code)) {
        is CliPairingService.SubmitOutcome.Completed ->
            call.respond(outcome.result)

        CliPairingService.SubmitOutcome.SessionNotFound ->
            call.respond(
                HttpStatusCode.NotFound,
                CliMessageDto("Pairing session not found or expired; start pairing again."),
            )

        CliPairingService.SubmitOutcome.InvalidCode ->
            call.respond(
                HttpStatusCode.BadRequest,
                CliMessageDto("The pairing code must be exactly 6 digits."),
            )
    }
}

private suspend fun handlePairCancel(
    call: ApplicationCall,
    cliPairingService: CliPairingService,
) {
    val request = call.receive<CliPairCancelRequest>()
    if (cliPairingService.cancel(request.sessionId)) {
        call.respond(CliMessageDto("Pairing cancelled."))
    } else {
        call.respond(
            HttpStatusCode.NotFound,
            CliMessageDto("Pairing session not found or expired."),
        )
    }
}

private suspend fun ApplicationCall.requireLongParameter(
    name: String,
    errorMessage: String,
): Long? {
    val value = parameters[name]?.toLongOrNull()
    if (value == null) {
        respond(HttpStatusCode.BadRequest, CliMessageDto(errorMessage))
    }
    return value
}

private suspend fun handleStatus(
    call: ApplicationCall,
    appInfo: AppInfo,
    configManager: DesktopConfigManager,
    pasteDao: PasteDao,
    syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {
    val config = configManager.getCurrentConfig()
    call.respond(
        CliStatusDto(
            appVersion = appInfo.appVersion,
            appInstanceId = appInfo.appInstanceId,
            apiVersion = CLI_API_VERSION,
            port = config.port,
            pasteboardListening = config.enablePasteboardListening,
            deviceCount = syncRuntimeInfoDao.getAllSyncRuntimeInfos().size,
            pasteCount = pasteDao.getActiveCount(),
        ),
    )
}

private suspend fun respondPasteDetail(
    call: ApplicationCall,
    pasteData: PasteData?,
    pasteTagDao: PasteTagDao,
    pasteItemReader: PasteItemReader,
    userDataPathProvider: UserDataPathProvider,
) {
    // Raw markup is opt-in (?includeRaw=true): for plain text raw == summary,
    // so sending both unconditionally would ship large pastes twice per
    // request (the CLI buffers the whole body before decoding)
    val includeRaw = call.request.queryParameters["includeRaw"] == "true"
    if (pasteData == null) {
        call.respond(HttpStatusCode.NotFound, CliMessageDto("Paste not found."))
    } else {
        call.respond(pasteData.toDetailDto(pasteTagDao, pasteItemReader, includeRaw, userDataPathProvider))
    }
}

/** Input-file ceiling for CLI image transcoding; larger files are refused. */
private const val CLI_IMAGE_MAX_SOURCE_BYTES = 64L * 1024 * 1024

/**
 * Serializes CLI image transcodes process-wide: one decode can hold hundreds
 * of MiB of intermediate pixels (up to 64 MiB input + ~256 MiB decoded), and
 * concurrent CLI requests must never multiply that against the desktop app's
 * heap. Previews are bursty, not throughput-bound, so one permit is enough.
 */
private val cliImageTranscodeSemaphore = Semaphore(permits = 1)

/**
 * Reads at most [limit] bytes through a stream with a hard cap — the cap
 * holds even when the file grows or is swapped between any size check and the
 * read (reference-backed paste files can change underneath us). Returns null
 * for an empty, oversized, or unreadable file. Only I/O failures are caught:
 * JVM resource errors must surface, not masquerade as a 404.
 */
private fun readImageBytesBounded(
    file: File,
    limit: Long,
): ByteArray? =
    try {
        file.inputStream().use { input ->
            val bytes = input.readNBytes(limit.toInt() + 1)
            if (bytes.isEmpty() || bytes.size > limit) null else bytes
        }
    } catch (_: IOException) {
        null
    }

/**
 * Serves decoded pixels for the CLI's sixel rendering: the stored image at
 * `index` of the paste's file list, decoded and aspect-fit scaled by
 * [CliImageTranscoder] into the requested `maxWidth` x `maxHeight` box (both
 * clamped to [CliImageTranscoder.MAX_BOX_PX], defaulting to it), returned as
 * straight-alpha RGBA8888 with the exact dimensions in X-Image-Width /
 * X-Image-Height headers. Every not-servable case — unknown id, no file at
 * the index, unreadable/oversized file, undecodable bytes — is a 404 with a
 * message, except an unavailable native decode backend (missing libGL on a
 * headless server, #4854), which is a 503 whose message carries the remedy;
 * the CLI falls back to printing paths on any non-200 and surfaces the 503
 * message.
 */
private suspend fun handlePasteImage(
    call: ApplicationCall,
    id: Long,
    pasteDao: PasteDao,
    userDataPathProvider: UserDataPathProvider,
) {
    val index = call.request.queryParameters["index"]?.toIntOrNull() ?: 0
    val maxWidth =
        call.request.queryParameters["maxWidth"]?.toIntOrNull()
            ?: CliImageTranscoder.MAX_BOX_PX
    val maxHeight =
        call.request.queryParameters["maxHeight"]?.toIntOrNull()
            ?: CliImageTranscoder.MAX_BOX_PX
    val pasteData =
        pasteDao.getNoDeletePasteData(id) ?: run {
            call.respond(HttpStatusCode.NotFound, CliMessageDto("Paste #$id not found."))
            return
        }
    val path =
        (pasteData.pasteAppearItem as? PasteFiles)
            ?.getFilePaths(userDataPathProvider)
            ?.getOrNull(index)
            ?: run {
                call.respond(
                    HttpStatusCode.NotFound,
                    CliMessageDto("Paste #$id has no stored file at index $index."),
                )
                return
            }
    val raw =
        try {
            withContext(ioDispatcher) {
                cliImageTranscodeSemaphore.withPermit {
                    readImageBytesBounded(path.toFile(), CLI_IMAGE_MAX_SOURCE_BYTES)
                        ?.let { CliImageTranscoder.transcode(it, maxWidth, maxHeight) }
                }
            }
        } catch (e: LinkageError) {
            // Skia's native library failed to load — on headless Linux servers
            // this means libGL.so.1/libEGL.so.1 are missing (#4854). It surfaces
            // as ExceptionInInitializerError on first use and NoClassDefFoundError
            // on every later call, and stays broken for the process lifetime, so
            // the remedy must include a restart.
            cliLogger.warn {
                "CLI image transcode unavailable: native graphics library failed to " +
                    "load (${e.message ?: e::class.simpleName}). On Debian/Ubuntu " +
                    "install libgl1 and libegl1, then restart CrossPaste."
            }
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                CliMessageDto(
                    "Image decoding is unavailable: CrossPaste could not load its " +
                        "native graphics library (${e.message ?: e::class.simpleName}). " +
                        "On Debian/Ubuntu install libgl1 and libegl1, then restart CrossPaste.",
                ),
            )
            return
        }
    if (raw == null) {
        call.respond(
            HttpStatusCode.NotFound,
            CliMessageDto("Paste #$id file at index $index is not a decodable image."),
        )
        return
    }
    call.response.header(CLI_IMAGE_WIDTH_HEADER, raw.width)
    call.response.header(CLI_IMAGE_HEIGHT_HEADER, raw.height)
    call.respondBytes(raw.rgba, ContentType.Application.OctetStream)
}

private suspend fun handleDevices(
    call: ApplicationCall,
    syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {
    val devices =
        syncRuntimeInfoDao.getAllSyncRuntimeInfos().map { info ->
            CliDeviceDto(
                appInstanceId = info.appInstanceId,
                deviceName = info.deviceName,
                noteName = info.noteName,
                platform = info.platform.name,
                appVersion = info.appVersion,
                connectState = info.connectState,
                connectHostAddress = info.connectHostAddress,
                port = info.port,
                allowSend = info.allowSend,
                allowReceive = info.allowReceive,
            )
        }
    call.respond(devices)
}

private suspend fun handleTagList(
    call: ApplicationCall,
    pasteTagDao: PasteTagDao,
) {
    val tags =
        withContext(ioDispatcher) {
            pasteTagDao.getAllTagsBlock().map { tag ->
                CliTagDto(id = tag.id, name = tag.name, color = tag.color)
            }
        }
    call.respond(tags)
}

private suspend fun handleTagCreate(
    call: ApplicationCall,
    pasteTagDao: PasteTagDao,
) {
    val request = call.receive<CliTagCreateRequest>()
    if (request.name.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, CliMessageDto("Tag name must not be blank."))
        return
    }
    val color = PasteTag.getColor(pasteTagDao.getMaxSortOrder() + 1)
    val id = pasteTagDao.createPasteTag(request.name, color)
    call.respond(CliTagDto(id = id, name = request.name, color = color))
}

// These settings require workflows beyond persisting one config field. Storage
// changes migrate data and restart the app; pasteboard and MCP changes must
// start, stop, or restart their running services. A generic config write
// would report success while leaving the process in a contradictory state.
// Each entry carries the hint shown to the user; the sync port is special in
// that no app setting exists either — the server binds the configured port at
// startup and writes the actually-bound port back on conflict.
private val cliBlockedConfigKeys =
    mapOf(
        "storagePath" to "use the storage settings in the app instead.",
        "useDefaultStoragePath" to "use the storage settings in the app instead.",
        "port" to "the sync port is managed automatically by the app.",
        "enablePasteboardListening" to "use the pasteboard controls in the app instead.",
        "enableMcpServer" to "use the MCP server settings in the app instead.",
        "mcpServerPort" to "use the MCP server settings in the app instead.",
    )

private suspend fun handleConfigSet(
    call: ApplicationCall,
    configManager: DesktopConfigManager,
) {
    val request = call.receive<CliConfigSetRequest>()
    val blockedHint = cliBlockedConfigKeys[request.key]
    if (blockedHint != null) {
        call.respond(
            HttpStatusCode.BadRequest,
            CliMessageDto("Config key '${request.key}' cannot be changed via the CLI; $blockedHint"),
        )
        return
    }
    val existing = configFieldOf(configManager, request.key)
    if (existing !is JsonPrimitive) {
        call.respond(HttpStatusCode.BadRequest, CliMessageDto("Unknown config key: '${request.key}'."))
        return
    }
    val parsed = parseConfigValue(existing, request.value)
    if (parsed == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            CliMessageDto("Invalid value '${request.value}' for config key '${request.key}'."),
        )
        return
    }
    val expected = expectedPrimitive(parsed)
    val candidate = configManager.getCurrentConfig().copy(request.key, parsed)
    val candidateValue = configFieldOf(candidate, request.key) as? JsonPrimitive
    if (!candidateValue.matches(expected)) {
        call.respond(
            HttpStatusCode.BadRequest,
            CliMessageDto("Invalid value '${request.value}' for config key '${request.key}'."),
        )
        return
    }
    configManager.updateConfig(request.key, parsed)
    // updateConfig gives no result and rolls back silently when persisting
    // fails (and DesktopAppConfig.copy ignores keys it does not map), so trust
    // only the observed value
    val actual = configFieldOf(configManager, request.key) as? JsonPrimitive
    if (!actual.matches(expected)) {
        call.respond(
            HttpStatusCode.InternalServerError,
            CliMessageDto("Config '${request.key}' was not applied."),
        )
        return
    }
    call.respond(CliMessageDto("Config '${request.key}' set to '${request.value}'."))
}

private fun configFieldOf(
    configManager: DesktopConfigManager,
    key: String,
): kotlinx.serialization.json.JsonElement? = configFieldOf(configManager.getCurrentConfig(), key)

private fun configFieldOf(
    config: DesktopAppConfig,
    key: String,
): kotlinx.serialization.json.JsonElement? =
    configJson
        .encodeToJsonElement(
            DesktopAppConfig.serializer(),
            config,
        ).jsonObject[key]

private fun JsonPrimitive?.matches(expected: JsonPrimitive): Boolean =
    this != null && content == expected.content && isString == expected.isString

private fun expectedPrimitive(parsed: Any): JsonPrimitive =
    when (parsed) {
        is Boolean -> JsonPrimitive(parsed)
        is Long -> JsonPrimitive(parsed)
        else -> JsonPrimitive(parsed.toString())
    }

private suspend fun handleCopy(
    call: ApplicationCall,
    appInfo: AppInfo,
    configManager: DesktopConfigManager,
    pasteDao: PasteDao,
    pasteReleaseService: PasteReleaseService,
    pasteboardService: PasteboardService,
) {
    val request = call.receive<CliCopyRequest>()
    if (request.text.isEmpty()) {
        call.respond(HttpStatusCode.BadRequest, CliMessageDto("Text must not be empty."))
        return
    }
    val pasteItem =
        createTextPasteItem(
            identifiers = listOf(DesktopTextTypePlugin.TEXT),
            text = request.text,
        )
    // Enforce the non-file size limit up front: past this point,
    // DiscardOversizedNonFilePlugin would empty the item list during release,
    // the row would be mark-deleted, and the success response below would
    // report an id that no longer exists (and never syncs)
    val maxNonFilePasteSizeMb = configManager.getCurrentConfig().maxNonFilePasteSize
    if (pasteItem.size > getFileUtils().bytesSize(maxNonFilePasteSizeMb)) {
        call.respond(
            HttpStatusCode.PayloadTooLarge,
            CliMessageDto(
                "Text (${pasteItem.size} bytes) exceeds the configured non-file paste " +
                    "limit of $maxNonFilePasteSizeMb MB (maxNonFilePasteSize).",
            ),
        )
        return
    }
    // Same lifecycle as a pasteboard capture: LOADING row, then the standard
    // local release (process plugins, dedup, sync and rendering tasks) — a
    // direct LOADED insert would silently skip all of that
    val pasteData =
        PasteData(
            appInstanceId = appInfo.appInstanceId,
            pasteCollection = PasteCollection(listOf(pasteItem)),
            pasteType = PasteType.INVALID_TYPE.type,
            source = "CLI",
            size = 0L,
            hash = "",
            pasteState = PasteState.LOADING,
            createTime = DateUtils.nowEpochMilliseconds(),
        )
    val id = pasteDao.createPasteData(pasteData)
    pasteReleaseService.releaseLocalPasteData(
        id = id,
        pasteItems = listOf(pasteItem),
        targetAppInstanceIds = null,
    )
    val written =
        pasteboardService.tryWritePasteboard(
            id = id,
            pasteItem = pasteItem,
            localOnly = true,
        )
    if (written.isFailure) {
        call.respond(
            HttpStatusCode.InternalServerError,
            CliMessageDto("Stored paste #$id in history but failed to write the system clipboard."),
        )
        return
    }
    call.respond(CliCopyResponse(id))
}

/**
 * Re-copies an existing paste of any type — the same action as pressing Enter
 * on an item in the search window: the item (with its full type, not a text
 * rendering) is written back to the pasteboard and bumped to the top of
 * history. This is what `pick` (and any "copy item N again" flow) uses; the
 * text-only POST /cli/copy creates new pastes from terminal input instead.
 */
private suspend fun handlePasteCopy(
    call: ApplicationCall,
    id: Long,
    pasteDao: PasteDao,
    pasteboardService: PasteboardService,
) {
    val pasteData =
        pasteDao.getNoDeletePasteData(id) ?: run {
            call.respond(HttpStatusCode.NotFound, CliMessageDto("Paste #$id not found."))
            return
        }
    val written =
        pasteboardService.tryWritePasteboard(
            pasteData = pasteData,
            localOnly = true,
            updateCreateTime = true,
        )
    if (written.isFailure) {
        call.respond(
            HttpStatusCode.InternalServerError,
            CliMessageDto("Failed to write paste #$id to the system clipboard."),
        )
        return
    }
    call.respond(CliMessageDto("Paste #$id copied."))
}

/**
 * Updates a paste's content in place: the row keeps its id, type, createTime,
 * and tags; the appear item and every derived clipboard flavor in the
 * collection are rebuilt together by [PasteContentEditor], guarded by the
 * editor's expected hash and a complete old-value CAS against concurrent
 * writes. Like in-app edits, the change is local only (not re-synced to
 * devices that already hold the original) and the system clipboard is not
 * rewritten.
 */
private suspend fun handlePasteUpdate(
    call: ApplicationCall,
    id: Long,
    configManager: DesktopConfigManager,
    pasteDao: PasteDao,
    pasteContentEditor: PasteContentEditor,
    taskSubmitter: TaskSubmitter,
    userDataPathProvider: UserDataPathProvider,
) {
    val request = call.receive<CliPasteUpdateRequest>()
    if (request.content.isEmpty()) {
        call.respond(HttpStatusCode.BadRequest, CliMessageDto("Content must not be empty."))
        return
    }
    val pasteData =
        pasteDao.getNoDeletePasteData(id) ?: run {
            call.respond(HttpStatusCode.NotFound, CliMessageDto("Paste #$id not found."))
            return
        }
    if (pasteData.pasteState != PasteState.LOADED) {
        call.respond(
            HttpStatusCode.Conflict,
            CliMessageDto("Paste #$id is still loading; try again shortly."),
        )
        return
    }
    // The content edit bypasses the release plugin pipeline, so the non-file
    // size cap must be enforced up front (mirrors POST /cli/copy)
    val maxNonFilePasteSizeMb = configManager.getCurrentConfig().maxNonFilePasteSize
    val contentSize =
        request.content
            .encodeToByteArray()
            .size
            .toLong()
    if (contentSize > getFileUtils().bytesSize(maxNonFilePasteSizeMb)) {
        call.respond(
            HttpStatusCode.PayloadTooLarge,
            CliMessageDto(
                "Content ($contentSize bytes) exceeds the configured non-file paste " +
                    "limit of $maxNonFilePasteSizeMb MB (maxNonFilePasteSize).",
            ),
        )
        return
    }
    val oldUrlItem = pasteData.pasteAppearItem as? UrlPasteItem
    when (
        val outcome =
            pasteContentEditor.updateContent(pasteData, request.content, request.expectedHash)
    ) {
        is PasteContentEditor.EditOutcome.Updated -> {
            if (outcome.urlChanged && oldUrlItem != null) {
                try {
                    refreshUrlRendering(pasteData, oldUrlItem, taskSubmitter, userDataPathProvider)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The content CAS has already committed. Preview refresh
                    // is best-effort and must not turn that success into a
                    // misleading 500 response.
                    cliLogger.warn(e) { "Paste #$id updated, but URL preview refresh failed" }
                }
            }
            call.respond(CliMessageDto("Paste #$id updated."))
        }

        PasteContentEditor.EditOutcome.Conflict ->
            call.respond(
                HttpStatusCode.Conflict,
                CliMessageDto(
                    "Paste #$id changed while it was being edited; fetch it again and retry.",
                ),
            )

        PasteContentEditor.EditOutcome.NotEditable ->
            call.respond(
                HttpStatusCode.BadRequest,
                CliMessageDto("Paste #$id (${pasteData.getTypeName()}) cannot be edited as text."),
            )

        is PasteContentEditor.EditOutcome.InvalidContent ->
            call.respond(HttpStatusCode.BadRequest, CliMessageDto(outcome.reason))
    }
}

/**
 * A URL edit leaves Open Graph residue of the OLD page behind: the preview
 * image file and no pending fetch for the new link. Delete the old URL's
 * content-addressed image — unless it is a shared marketing asset — and
 * submit a fresh rendering task. A late task writes only its old URL's cache
 * path, and its title writeback is guarded by the complete old item.
 */
private suspend fun refreshUrlRendering(
    pasteData: PasteData,
    oldUrlItem: UrlPasteItem,
    taskSubmitter: TaskSubmitter,
    userDataPathProvider: UserDataPathProvider,
) {
    if (oldUrlItem.getMarketingPath() == null) {
        oldUrlItem.clearRenderingFiles(
            pasteCoordinate = pasteData.getPasteCoordinate(),
            userDataPathProvider = userDataPathProvider,
        )
    }
    taskSubmitter.submit {
        addRenderingTask(pasteData.id, PasteType.URL_TYPE)
    }
}

private suspend fun handlePasteDelete(
    call: ApplicationCall,
    id: Long,
    pasteDao: PasteDao,
) {
    pasteDao
        .markDeletePasteData(id)
        .onSuccess {
            call.respond(CliMessageDto("Paste #$id deleted."))
        }.onFailure { e ->
            call.respond(
                HttpStatusCode.NotFound,
                CliMessageDto(e.message ?: "Failed to delete paste #$id."),
            )
        }
}

private suspend fun handleList(
    call: ApplicationCall,
    searchContentService: SearchContentService,
    pasteDao: PasteDao,
    pasteDataHelper: PasteDataHelper,
    pasteTagDao: PasteTagDao,
) {
    val limit =
        call.request.queryParameters["limit"]
            ?.toIntOrNull()
            ?.coerceIn(1, 1000)
            ?: DEFAULT_LIST_LIMIT
    val filters = parseListFilters(call, pasteTagDao) ?: return
    val sortParam = call.request.queryParameters["sort"]
    val sortNewestFirst =
        when (sortParam?.lowercase()) {
            null, SORT_NEWEST -> true
            SORT_OLDEST -> false
            else -> {
                call.respond(
                    HttpStatusCode.BadRequest,
                    CliMessageDto("Unknown sort order: '$sortParam'. Use '$SORT_NEWEST' or '$SORT_OLDEST'."),
                )
                return
            }
        }
    val query = call.request.queryParameters["q"].orEmpty()
    val searchTerms = if (query.isBlank()) listOf() else searchContentService.createSearchTerms(query)

    val results =
        pasteDao.searchPasteData(
            searchTerms = searchTerms,
            pasteTypeList = filters.pasteTypeList,
            sort = sortNewestFirst,
            tag = filters.tagId,
            limit = limit,
        )
    call.respond(
        CliPasteListDto(
            items = results.map { it.toSummaryDto(pasteTagDao, pasteDataHelper) },
            // A keyword search has no cheap total; report the result size there.
            total = if (searchTerms.isEmpty()) pasteDao.getActiveCount() else results.size.toLong(),
        ),
    )
}

private const val SORT_NEWEST = "newest"
private const val SORT_OLDEST = "oldest"

private const val DEFAULT_LIST_LIMIT = 20

/** Shared ?type=&tag= filters of the list and watch endpoints. */
private class ListFilters(
    /** Resolved [PasteType] codes; empty means all types. */
    val pasteTypeList: List<Int>,
    /** Resolved tag id; null when no tag filter was given. */
    val tagId: Long?,
)

/**
 * Parses the repeated `type` and single `tag` query parameters. Returns null
 * after responding 400 when a value does not resolve.
 */
private suspend fun parseListFilters(
    call: ApplicationCall,
    pasteTagDao: PasteTagDao,
): ListFilters? {
    // The type parameter repeats for OR-matching: type=text&type=link
    val pasteTypeList =
        call.request.queryParameters
            .getAll("type")
            .orEmpty()
            .map { name ->
                PasteType.TYPES.firstOrNull { it.name.equals(name, ignoreCase = true) }?.type
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, CliMessageDto("Unknown paste type: '$name'."))
                        return null
                    }
            }
    val tagName = call.request.queryParameters["tag"]
    val tagId =
        tagName?.let { name ->
            withContext(ioDispatcher) { pasteTagDao.getAllTagsBlock() }
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.id
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, CliMessageDto("Unknown tag: '$name'."))
                    return null
                }
        }
    return ListFilters(pasteTypeList, tagId)
}

/** How many newest rows the watch diff window covers per snapshot. */
private const val WATCH_WINDOW_LIMIT = 100L

/**
 * Keeps idle connections visibly alive: the client treats a long silence as a
 * broken socket, and the periodic write also keeps the server's idle-connection
 * reaper away. Must stay well below the CLI's read-silence budget.
 */
private val WATCH_HEARTBEAT_INTERVAL = 10.seconds

private val watchLineJson = Json { encodeDefaults = true }

/**
 * Streams newly arriving pastes (local captures, CLI copies, re-copies, and
 * entries synced from remote devices) as NDJSON: one [CliPasteSummaryDto] per
 * line, with a blank line as heartbeat. Existing history is never replayed —
 * the stream starts at "now". The source of truth is the same SQLDelight
 * query flow the UI observes, diffed by [CliWatchTracker], so the stream is a
 * best-effort live feed, not a durable queue: a burst larger than the diff
 * window between two snapshots can drop events.
 */
private suspend fun handleWatch(
    call: ApplicationCall,
    pasteDao: PasteDao,
    pasteDataHelper: PasteDataHelper,
    pasteTagDao: PasteTagDao,
) {
    val filters = parseListFilters(call, pasteTagDao) ?: return
    call.respondBytesWriter(contentType = ContentType("application", "x-ndjson")) {
        try {
            streamWatchEvents(this, filters, pasteDao, pasteDataHelper, pasteTagDao)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ending the stream is all that can be done either way — the
            // response is already committed, so letting the exception escape
            // would only make StatusPages fail on a second respond. A closed
            // channel just means the client went away; anything else (tag
            // lookup, serialization) is a real error and must be visible.
            if (e is IOException || e is ClosedWriteChannelException) {
                watchLogger.debug { "CLI watch client disconnected: ${e.message}" }
            } else {
                watchLogger.error(e) { "CLI watch stream failed" }
            }
        }
    }
}

private val watchLogger = KotlinLogging.logger("com.crosspaste.cli.CliWatch")

private suspend fun streamWatchEvents(
    channel: ByteWriteChannel,
    filters: ListFilters,
    pasteDao: PasteDao,
    pasteDataHelper: PasteDataHelper,
    pasteTagDao: PasteTagDao,
) = coroutineScope {
    val writeMutex = Mutex()
    val heartbeat =
        launch {
            while (true) {
                delay(WATCH_HEARTBEAT_INTERVAL)
                writeMutex.withLock {
                    channel.writeStringUtf8("\n")
                    channel.flush()
                }
            }
        }
    // Sampled BEFORE subscribing so the count and the snapshots cannot
    // disagree about a paste created in between: with the count at zero here,
    // anything created afterwards shows up in a snapshot, never behind one.
    // Sampling inside the collect instead would race — a paste created between
    // an honestly empty first snapshot and the count query would make the
    // count read > 0, the empty snapshot look like a masked failure, and the
    // next snapshot (carrying that first paste) get swallowed as baseline.
    val activeCountAtConnect = pasteDao.getActiveCount()
    try {
        var tracker: CliWatchTracker? = null
        // The DAO flow only ever completes after swallowing a transient DB
        // error (its catch emits an empty snapshot and ends). Resubscribe with
        // the tracker intact — resetting it would replay the whole window as
        // fresh arrivals — instead of ending the client's stream over a blip.
        // The delay keeps a persistently broken DB from turning this into a
        // busy loop.
        while (true) {
            pasteDao.getPasteDataFlow(WATCH_WINDOW_LIMIT).collect { rows ->
                val current = tracker
                if (current == null) {
                    // The first snapshot is the pre-subscription history
                    // baseline. An empty snapshot is ambiguous: it is either a
                    // genuinely empty history or the DAO flow masking a failed
                    // query. Building a baseline from the latter would replay
                    // the whole window as arrivals once the DB recovers, so an
                    // empty snapshot may only seed the baseline when the
                    // connect-time count confirms the history really is empty.
                    // (Accepted residue: a history that is fully deleted
                    // between the count and the first snapshot makes the next
                    // non-empty snapshot seed the baseline silently — a
                    // one-time swallow in a vanishingly rare sequence.)
                    if (rows.isEmpty() && activeCountAtConnect > 0L) {
                        return@collect
                    }
                    tracker = CliWatchTracker(rows, WATCH_WINDOW_LIMIT.toInt())
                    return@collect
                }
                // Filtering, tag lookups and JSON encoding all happen before
                // taking the write lock, which exists only to keep event
                // writes and heartbeats from interleaving
                val lines =
                    current.onWindow(rows).mapNotNull { row ->
                        encodeWatchEvent(row, filters, pasteTagDao, pasteDataHelper)
                    }
                if (lines.isEmpty()) return@collect
                writeMutex.withLock {
                    lines.forEach { channel.writeStringUtf8(it) }
                    channel.flush()
                }
            }
            delay(WATCH_RESUBSCRIBE_DELAY)
        }
    } finally {
        heartbeat.cancel()
    }
}

private val WATCH_RESUBSCRIBE_DELAY = 1.seconds

/** Applies the type/tag filters and returns the NDJSON line, or null when filtered out. */
private suspend fun encodeWatchEvent(
    row: PasteData,
    filters: ListFilters,
    pasteTagDao: PasteTagDao,
    pasteDataHelper: PasteDataHelper,
): String? {
    if (filters.pasteTypeList.isNotEmpty() && row.pasteType !in filters.pasteTypeList) {
        return null
    }
    val tagIds = withContext(ioDispatcher) { pasteTagDao.getPasteTagsBlock(row.id) }
    if (filters.tagId != null && filters.tagId !in tagIds) {
        return null
    }
    val dto = row.toSummaryDto(tagged = tagIds.isNotEmpty(), pasteDataHelper = pasteDataHelper)
    return watchLineJson.encodeToString(CliPasteSummaryDto.serializer(), dto) + "\n"
}

private suspend fun PasteData.toSummaryDto(
    pasteTagDao: PasteTagDao,
    pasteDataHelper: PasteDataHelper,
): CliPasteSummaryDto =
    toSummaryDto(
        tagged = withContext(ioDispatcher) { pasteTagDao.getPasteTagsBlock(id).isNotEmpty() },
        pasteDataHelper = pasteDataHelper,
    )

private fun PasteData.toSummaryDto(
    tagged: Boolean,
    pasteDataHelper: PasteDataHelper,
): CliPasteSummaryDto =
    CliPasteSummaryDto(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        tagged = tagged,
        createTime = createTime,
        preview = pasteDataHelper.getSummary(this, "Loading...", ""),
        remote = remote,
    )

private suspend fun PasteData.toDetailDto(
    pasteTagDao: PasteTagDao,
    pasteItemReader: PasteItemReader,
    includeRaw: Boolean,
    userDataPathProvider: UserDataPathProvider,
): CliPasteDetailDto =
    CliPasteDetailDto(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        tagged = withContext(ioDispatcher) { pasteTagDao.getPasteTagsBlock(id).isNotEmpty() },
        createTime = createTime,
        remote = remote,
        hash = hash,
        content = pasteAppearItem?.let { pasteItemReader.getSummary(it) },
        rawContent = if (includeRaw) pasteAppearItem?.let { pasteItemReader.getRaw(it) } else null,
        filePaths =
            (pasteAppearItem as? PasteFiles)
                ?.getFilePaths(userDataPathProvider)
                ?.map { it.toString() }
                .orEmpty(),
    )

private fun parseConfigValue(
    existing: JsonPrimitive,
    value: String,
): Any? =
    when {
        existing.booleanOrNull != null -> value.lowercase().toBooleanStrictOrNull()
        !existing.isString && existing.longOrNull != null -> value.toLongOrNull()
        else -> value
    }
