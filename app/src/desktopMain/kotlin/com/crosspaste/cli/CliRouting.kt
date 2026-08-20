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
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val configJson = Json { encodeDefaults = true }

fun Routing.cliRouting(
    appInfo: AppInfo,
    cliPairingService: CliPairingService,
    configManager: DesktopConfigManager,
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
    // The type parameter repeats for OR-matching: type=text&type=link
    val pasteTypeList =
        call.request.queryParameters
            .getAll("type")
            .orEmpty()
            .map { name ->
                PasteType.TYPES.firstOrNull { it.name.equals(name, ignoreCase = true) }?.type
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, CliMessageDto("Unknown paste type: '$name'."))
                        return
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
                    return
                }
        }
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
            pasteTypeList = pasteTypeList,
            sort = sortNewestFirst,
            tag = tagId,
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

private suspend fun PasteData.toSummaryDto(
    pasteTagDao: PasteTagDao,
    pasteDataHelper: PasteDataHelper,
): CliPasteSummaryDto =
    CliPasteSummaryDto(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        tagged = withContext(ioDispatcher) { pasteTagDao.getPasteTagsBlock(id).isNotEmpty() },
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
