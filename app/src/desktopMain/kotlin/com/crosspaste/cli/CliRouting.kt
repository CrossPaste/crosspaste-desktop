package com.crosspaste.cli

import com.crosspaste.app.AppInfo
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.path.UserDataPathProvider
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

private val configJson = Json { encodeDefaults = true }

fun Routing.cliRouting(
    appInfo: AppInfo,
    cliPairingService: CliPairingService,
    configManager: DesktopConfigManager,
    updatePasteItemHelper: UpdatePasteItemHelper,
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
            handlePasteUpdate(call, id, configManager, pasteDao, updatePasteItemHelper)
        }

        route("/pair") {
            get("/nearby") {
                handlePairNearby(call, cliPairingService)
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
 * Updates a paste's content in place — the same semantics as editing inside
 * the app: the row keeps its id, type, createTime, and tags; search content
 * and size are refreshed via [UpdatePasteItemHelper]. Like in-app edits, the
 * change is local only (not re-synced to devices that already hold the
 * original) and the system clipboard is not rewritten.
 */
private suspend fun handlePasteUpdate(
    call: ApplicationCall,
    id: Long,
    configManager: DesktopConfigManager,
    pasteDao: PasteDao,
    updatePasteItemHelper: UpdatePasteItemHelper,
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
    // updatePasteAppearItem bypasses the release plugin pipeline, so the
    // non-file size cap must be enforced up front (mirrors POST /cli/copy)
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
    val result =
        when (val item = pasteData.pasteAppearItem) {
            is TextPasteItem -> updatePasteItemHelper.updateText(pasteData, request.content, item)
            is HtmlPasteItem -> updatePasteItemHelper.updateHtml(pasteData, request.content, htmlPasteItem = item)
            is RtfPasteItem -> updatePasteItemHelper.updateRtf(pasteData, request.content, item)
            is UrlPasteItem -> updatePasteItemHelper.updateUrl(pasteData, request.content, item)
            is ColorPasteItem -> {
                val color =
                    parseColorHex(request.content) ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            CliMessageDto(
                                "Invalid color '${request.content.trim()}'; expected #RRGGBB or #RRGGBBAA.",
                            ),
                        )
                        return
                    }
                updatePasteItemHelper.updateColor(pasteData, color, item)
            }

            else -> {
                call.respond(
                    HttpStatusCode.BadRequest,
                    CliMessageDto("Paste #$id (${pasteData.getTypeName()}) cannot be edited as text."),
                )
                return
            }
        }
    result
        .onSuccess {
            call.respond(CliMessageDto("Paste #$id updated."))
        }.onFailure { e ->
            call.respond(
                HttpStatusCode.InternalServerError,
                CliMessageDto(e.message ?: "Failed to update paste #$id."),
            )
        }
}

/**
 * Parses the color notation the CLI shows (`PasteColor.toHexString`:
 * #RRGGBBAA, alpha last) plus the plain #RRGGBB shorthand (alpha = FF).
 */
private fun parseColorHex(value: String): Long? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    val red = hex.substring(0, 2).toLong(16)
    val green = hex.substring(2, 4).toLong(16)
    val blue = hex.substring(4, 6).toLong(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toLong(16) else 0xFFL
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
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
