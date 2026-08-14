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
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.utils.DateUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val configJson = Json { encodeDefaults = true }

fun Routing.cliRouting(
    appInfo: AppInfo,
    configManager: DesktopConfigManager,
    pasteboardService: PasteboardService,
    pasteDao: PasteDao,
    pasteDataHelper: PasteDataHelper,
    pasteItemReader: PasteItemReader,
    pasteTagDao: PasteTagDao,
    searchContentService: SearchContentService,
    syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {
    route("/cli") {
        get("/status") {
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

        get("/paste/latest") {
            val pasteData = pasteDao.getLatestLoadedPasteData()
            if (pasteData == null) {
                call.respond(HttpStatusCode.NotFound, CliMessageDto("Paste not found."))
            } else {
                call.respond(pasteData.toDetailDto(pasteTagDao, pasteItemReader))
            }
        }

        get("/paste/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, CliMessageDto("Invalid paste id."))
                return@get
            }
            val pasteData = pasteDao.getNoDeletePasteData(id)
            if (pasteData == null) {
                call.respond(HttpStatusCode.NotFound, CliMessageDto("Paste not found."))
            } else {
                call.respond(pasteData.toDetailDto(pasteTagDao, pasteItemReader))
            }
        }

        get("/history") {
            handleList(
                call = call,
                searchTerms = listOf(),
                pasteDao = pasteDao,
                pasteDataHelper = pasteDataHelper,
                pasteTagDao = pasteTagDao,
                total = { pasteDao.getActiveCount() },
            )
        }

        get("/search") {
            val query = call.request.queryParameters["q"] ?: ""
            handleList(
                call = call,
                searchTerms = searchContentService.createSearchTerms(query),
                pasteDao = pasteDao,
                pasteDataHelper = pasteDataHelper,
                pasteTagDao = pasteTagDao,
                total = null,
            )
        }

        get("/devices") {
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

        get("/tags") {
            val tags =
                pasteTagDao.getAllTagsBlock().map { tag ->
                    CliTagDto(id = tag.id, name = tag.name, color = tag.color)
                }
            call.respond(tags)
        }

        post("/tags") {
            val request = call.receive<CliTagCreateRequest>()
            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, CliMessageDto("Tag name must not be blank."))
                return@post
            }
            val color = PasteTag.getColor(pasteTagDao.getMaxSortOrder() + 1)
            val id = pasteTagDao.createPasteTag(request.name, color)
            call.respond(CliTagDto(id = id, name = request.name, color = color))
        }

        delete("/tags/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, CliMessageDto("Invalid tag id."))
                return@delete
            }
            pasteTagDao.deletePasteTagBlock(id)
            call.respond(CliMessageDto("Tag #$id deleted."))
        }

        get("/config") {
            call.respond(configManager.getCurrentConfig())
        }

        put("/config") {
            val request = call.receive<CliConfigSetRequest>()
            val currentJson =
                configJson
                    .encodeToJsonElement(
                        DesktopAppConfig.serializer(),
                        configManager.getCurrentConfig(),
                    ).jsonObject
            val existing = currentJson[request.key]
            if (existing !is JsonPrimitive) {
                call.respond(HttpStatusCode.BadRequest, CliMessageDto("Unknown config key: '${request.key}'."))
                return@put
            }
            val parsed = parseConfigValue(existing, request.value)
            if (parsed == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    CliMessageDto("Invalid value '${request.value}' for config key '${request.key}'."),
                )
                return@put
            }
            configManager.updateConfig(request.key, parsed)
            call.respond(CliMessageDto("Config '${request.key}' set to '${request.value}'."))
        }

        post("/copy") {
            val request = call.receive<CliCopyRequest>()
            if (request.text.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, CliMessageDto("Text must not be empty."))
                return@post
            }
            val pasteItem =
                createTextPasteItem(
                    identifiers = listOf(DesktopTextTypePlugin.TEXT),
                    text = request.text,
                )
            val pasteData =
                PasteData(
                    appInstanceId = appInfo.appInstanceId,
                    pasteAppearItem = pasteItem,
                    pasteCollection = PasteCollection(listOf()),
                    pasteType = PasteType.TEXT_TYPE.type,
                    source = "CLI",
                    size = pasteItem.size,
                    hash = pasteItem.hash,
                    pasteState = PasteState.LOADED,
                    createTime = DateUtils.nowEpochMilliseconds(),
                )
            val id = pasteDao.createPasteData(pasteData)
            pasteboardService.tryWritePasteboard(
                id = id,
                pasteItem = pasteItem,
                localOnly = true,
            )
            call.respond(CliCopyResponse(id))
        }

        delete("/paste/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, CliMessageDto("Invalid paste id."))
                return@delete
            }
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
    }
}

private suspend fun handleList(
    call: ApplicationCall,
    searchTerms: List<String>,
    pasteDao: PasteDao,
    pasteDataHelper: PasteDataHelper,
    pasteTagDao: PasteTagDao,
    total: (suspend () -> Long)?,
) {
    val limit =
        call.request.queryParameters["limit"]
            ?.toIntOrNull()
            ?.coerceIn(1, 1000)
            ?: DEFAULT_LIST_LIMIT
    val typeName = call.request.queryParameters["type"]
    val pasteType =
        typeName?.let { name ->
            PasteType.TYPES.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, CliMessageDto("Unknown paste type: '$name'."))
                    return
                }
        }
    val tagName = call.request.queryParameters["tag"]
    val tagId =
        tagName?.let { name ->
            pasteTagDao
                .getAllTagsBlock()
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.id
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, CliMessageDto("Unknown tag: '$name'."))
                    return
                }
        }

    val results =
        pasteDao.searchPasteData(
            searchTerms = searchTerms,
            pasteTypeList = listOfNotNull(pasteType?.type),
            tag = tagId,
            limit = limit,
        )
    call.respond(
        CliPasteListDto(
            items = results.map { it.toSummaryDto(pasteTagDao, pasteDataHelper) },
            total = total?.invoke() ?: results.size.toLong(),
        ),
    )
}

private const val DEFAULT_LIST_LIMIT = 20

private fun PasteData.toSummaryDto(
    pasteTagDao: PasteTagDao,
    pasteDataHelper: PasteDataHelper,
): CliPasteSummaryDto =
    CliPasteSummaryDto(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        tagged = pasteTagDao.getPasteTagsBlock(id).isNotEmpty(),
        createTime = createTime,
        preview = pasteDataHelper.getSummary(this, "Loading...", ""),
        remote = remote,
    )

private fun PasteData.toDetailDto(
    pasteTagDao: PasteTagDao,
    pasteItemReader: PasteItemReader,
): CliPasteDetailDto =
    CliPasteDetailDto(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        tagged = pasteTagDao.getPasteTagsBlock(id).isNotEmpty(),
        createTime = createTime,
        remote = remote,
        hash = hash,
        content = pasteAppearItem?.let { pasteItemReader.getSummary(it) },
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
