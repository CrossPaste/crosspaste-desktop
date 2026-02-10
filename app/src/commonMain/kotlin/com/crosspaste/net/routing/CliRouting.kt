package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.dto.cli.ConfigEntryDto
import com.crosspaste.dto.cli.ConfigUpdateRequest
import com.crosspaste.dto.cli.CopyRequest
import com.crosspaste.dto.cli.CreateTagRequest
import com.crosspaste.dto.cli.DeviceSummary
import com.crosspaste.dto.cli.PasteDetailResponse
import com.crosspaste.dto.cli.PasteListResponse
import com.crosspaste.dto.cli.PasteSummaryDto
import com.crosspaste.dto.cli.StatusResponse
import com.crosspaste.dto.cli.TagSummary
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.Server
import com.crosspaste.net.cli.CliTokenManager
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first

private const val PREVIEW_MAX_LENGTH = 200

fun Routing.cliRouting(
    appInfo: AppInfo,
    cliTokenManager: CliTokenManager,
    configManager: CommonConfigManager,
    pasteDao: PasteDao,
    pasteboardService: PasteboardService,
    server: Server,
    syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {
    val logger = KotlinLogging.logger {}

    route("/cli") {
        intercept(ApplicationCallPipeline.Setup) {
            val remoteHost = call.request.local.remoteHost
            if (remoteHost != "127.0.0.1" && remoteHost != "::1" && remoteHost != "0:0:0:0:0:0:0:1") {
                logger.warn { "CLI request rejected from non-local address: $remoteHost" }
                failResponse(
                    call,
                    StandardErrorCode.CLI_FORBIDDEN.toErrorCode(),
                    "CLI access is restricted to localhost",
                )
                finish()
                return@intercept
            }

            val authHeader = call.request.headers["Authorization"]
            val token = authHeader?.removePrefix("Bearer ")?.trim()
            if (token == null || !cliTokenManager.validate(token)) {
                logger.warn { "CLI request rejected: invalid or missing token" }
                failResponse(call, StandardErrorCode.CLI_FORBIDDEN.toErrorCode(), "Invalid or missing CLI token")
                finish()
                return@intercept
            }
        }

        get("/status") {
            val config = configManager.getCurrentConfig()
            val deviceCount = syncRuntimeInfoDao.getAllSyncRuntimeInfos().size
            val pasteCount = pasteDao.getActiveCount()
            val response =
                StatusResponse(
                    appVersion = appInfo.appVersion,
                    appInstanceId = appInfo.appInstanceId,
                    port = server.port(),
                    pasteboardListening = config.enablePasteboardListening,
                    deviceCount = deviceCount,
                    pasteCount = pasteCount,
                )
            successResponse(call, response)
        }

        get("/paste/current") {
            val results =
                pasteDao.searchPasteData(
                    searchTerms = listOf(),
                    limit = 1,
                )
            val paste = results.firstOrNull()
            if (paste == null) {
                failResponse(call, StandardErrorCode.CLI_NOT_FOUND.toErrorCode(), "No pastes found")
                return@get
            }
            successResponse(call, paste.toDetailResponse())
        }

        get("/paste/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                failResponse(call, StandardErrorCode.CLI_INVALID_REQUEST.toErrorCode(), "Invalid paste ID")
                return@get
            }
            val paste = pasteDao.getNoDeletePasteData(id)
            if (paste == null) {
                failResponse(call, StandardErrorCode.CLI_NOT_FOUND.toErrorCode(), "Paste #$id not found")
                return@get
            }
            successResponse(call, paste.toDetailResponse())
        }

        get("/paste/list") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val typeParam = call.request.queryParameters["type"]
            val favoriteParam = call.request.queryParameters["favorite"]

            val pasteType = typeParam?.let { resolveTypeFilter(it) }
            val favorite = favoriteParam?.toBooleanStrictOrNull()

            val results =
                pasteDao.searchPasteData(
                    searchTerms = listOf(),
                    favorite = favorite,
                    pasteType = pasteType,
                    limit = limit.coerceIn(1, 100),
                )
            val total = pasteDao.getActiveCount()
            val items = results.map { it.toSummaryDto() }
            successResponse(call, PasteListResponse(items = items, total = total))
        }

        get("/paste/search") {
            val query = call.request.queryParameters["q"] ?: ""
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val typeParam = call.request.queryParameters["type"]
            val pasteType = typeParam?.let { resolveTypeFilter(it) }

            val searchTerms = query.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val results =
                pasteDao.searchPasteData(
                    searchTerms = searchTerms,
                    pasteType = pasteType,
                    limit = limit.coerceIn(1, 100),
                )
            val items = results.map { it.toSummaryDto() }
            successResponse(call, PasteListResponse(items = items, total = items.size.toLong()))
        }

        post("/clipboard/write") {
            val request = call.receive<CopyRequest>()
            val textItem =
                TextPasteItem(
                    identifiers = listOf("public.utf8-plain-text"),
                    hash = request.text.hashCode().toString(),
                    size =
                        request.text
                            .encodeToByteArray()
                            .size
                            .toLong(),
                    text = request.text,
                )
            pasteboardService.tryWritePasteboard(pasteItem = textItem, localOnly = true)
            successResponse(call)
        }

        delete("/paste/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                failResponse(call, StandardErrorCode.CLI_INVALID_REQUEST.toErrorCode(), "Invalid paste ID")
                return@delete
            }
            val paste = pasteDao.getNoDeletePasteData(id)
            if (paste == null) {
                failResponse(call, StandardErrorCode.CLI_NOT_FOUND.toErrorCode(), "Paste #$id not found")
                return@delete
            }
            pasteDao.markDeletePasteData(id)
            successResponse(call)
        }

        put("/paste/{id}/favorite") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                failResponse(call, StandardErrorCode.CLI_INVALID_REQUEST.toErrorCode(), "Invalid paste ID")
                return@put
            }
            val paste = pasteDao.getNoDeletePasteData(id)
            if (paste == null) {
                failResponse(call, StandardErrorCode.CLI_NOT_FOUND.toErrorCode(), "Paste #$id not found")
                return@put
            }
            pasteDao.setFavorite(id, !paste.favorite)
            successResponse(call)
        }

        get("/devices") {
            val devices = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
            val summaries =
                devices.map { info ->
                    DeviceSummary(
                        appInstanceId = info.appInstanceId,
                        deviceName = info.deviceName,
                        noteName = info.noteName,
                        platform = "${info.platform.name} ${info.platform.version}",
                        appVersion = info.appVersion,
                        connectState = info.connectState,
                        connectHostAddress = info.connectHostAddress,
                        port = info.port,
                        allowSend = info.allowSend,
                        allowReceive = info.allowReceive,
                    )
                }
            successResponse(call, summaries)
        }

        get("/config") {
            val config = configManager.getCurrentConfig()
            val entries = buildConfigEntries(config)
            successResponse(call, entries)
        }

        put("/config") {
            val request = call.receive<ConfigUpdateRequest>()
            if (request.key !in MUTABLE_CONFIG_KEYS) {
                failResponse(
                    call,
                    StandardErrorCode.CLI_INVALID_REQUEST.toErrorCode(),
                    "Config key '${request.key}' is not modifiable via CLI. " +
                        "Allowed keys: ${MUTABLE_CONFIG_KEYS.joinToString(", ")}",
                )
                return@put
            }
            val value = parseConfigValue(request.key, request.value)
            if (value == null) {
                failResponse(
                    call,
                    StandardErrorCode.CLI_INVALID_REQUEST.toErrorCode(),
                    "Invalid value '${request.value}' for key '${request.key}'",
                )
                return@put
            }
            configManager.updateConfig(request.key, value)
            successResponse(call)
        }

        get("/tags") {
            val tags = pasteDao.getAllTagsFlow().first()
            val summaries =
                tags.map { tag ->
                    TagSummary(id = tag.id, name = tag.name, color = tag.color)
                }
            successResponse(call, summaries)
        }

        post("/tags") {
            val request = call.receive<CreateTagRequest>()
            val maxSortOrder = pasteDao.getMaxSortOrder()
            val color = request.color ?: PasteTag.getColor(maxSortOrder + 1)
            val id = pasteDao.createPasteTag(request.name, color)
            successResponse(call, TagSummary(id = id, name = request.name, color = color))
        }

        delete("/tags/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                failResponse(call, StandardErrorCode.CLI_INVALID_REQUEST.toErrorCode(), "Invalid tag ID")
                return@delete
            }
            pasteDao.deletePasteTagBlock(id)
            successResponse(call)
        }
    }
}

private fun resolveTypeFilter(typeName: String): Int? =
    PasteType.TYPES
        .find {
            it.name.equals(typeName, ignoreCase = true)
        }?.type

private fun com.crosspaste.paste.PasteData.toSummaryDto(): PasteSummaryDto {
    val preview = getSummary("Loading...", "Unknown").take(PREVIEW_MAX_LENGTH)
    return PasteSummaryDto(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        favorite = favorite,
        createTime = createTime,
        preview = preview,
        remote = remote,
    )
}

private val MUTABLE_CONFIG_KEYS =
    setOf(
        "enablePasteboardListening",
        "enableDiscovery",
        "enableEncryptSync",
        "enableSyncText",
        "enableSyncUrl",
        "enableSyncHtml",
        "enableSyncRtf",
        "enableSyncImage",
        "enableSyncFile",
        "enableSyncColor",
        "enableExpirationCleanup",
        "enableThresholdCleanup",
        "enableSoundEffect",
        "pastePrimaryTypeOnly",
        "maxStorage",
        "maxBackupFileSize",
        "maxSyncFileSize",
    )

private val BOOLEAN_CONFIG_KEYS =
    setOf(
        "enablePasteboardListening",
        "enableDiscovery",
        "enableEncryptSync",
        "enableSyncText",
        "enableSyncUrl",
        "enableSyncHtml",
        "enableSyncRtf",
        "enableSyncImage",
        "enableSyncFile",
        "enableSyncColor",
        "enableExpirationCleanup",
        "enableThresholdCleanup",
        "enableSoundEffect",
        "pastePrimaryTypeOnly",
    )

private val LONG_CONFIG_KEYS =
    setOf(
        "maxStorage",
        "maxBackupFileSize",
        "maxSyncFileSize",
    )

private fun parseConfigValue(
    key: String,
    value: String,
): Any? =
    when (key) {
        in BOOLEAN_CONFIG_KEYS -> value.toBooleanStrictOrNull()
        in LONG_CONFIG_KEYS -> value.toLongOrNull()
        else -> null
    }

private fun buildConfigEntries(config: com.crosspaste.config.AppConfig): List<ConfigEntryDto> =
    listOf(
        ConfigEntryDto("enablePasteboardListening", config.enablePasteboardListening.toString()),
        ConfigEntryDto("enableDiscovery", config.enableDiscovery.toString()),
        ConfigEntryDto("enableEncryptSync", config.enableEncryptSync.toString()),
        ConfigEntryDto("enableSyncText", config.enableSyncText.toString()),
        ConfigEntryDto("enableSyncUrl", config.enableSyncUrl.toString()),
        ConfigEntryDto("enableSyncHtml", config.enableSyncHtml.toString()),
        ConfigEntryDto("enableSyncRtf", config.enableSyncRtf.toString()),
        ConfigEntryDto("enableSyncImage", config.enableSyncImage.toString()),
        ConfigEntryDto("enableSyncFile", config.enableSyncFile.toString()),
        ConfigEntryDto("enableSyncColor", config.enableSyncColor.toString()),
        ConfigEntryDto("enableExpirationCleanup", config.enableExpirationCleanup.toString()),
        ConfigEntryDto("enableThresholdCleanup", config.enableThresholdCleanup.toString()),
        ConfigEntryDto("enableSoundEffect", config.enableSoundEffect.toString()),
        ConfigEntryDto("pastePrimaryTypeOnly", config.pastePrimaryTypeOnly.toString()),
        ConfigEntryDto("maxStorage", config.maxStorage.toString()),
        ConfigEntryDto("maxBackupFileSize", config.maxBackupFileSize.toString()),
        ConfigEntryDto("maxSyncFileSize", config.maxSyncFileSize.toString()),
        ConfigEntryDto("port", config.port.toString()),
        ConfigEntryDto("language", config.language),
    )

private fun com.crosspaste.paste.PasteData.toDetailResponse(): PasteDetailResponse {
    val content =
        (pasteAppearItem as? PasteText)?.text
            ?: pasteAppearItem?.getSummary()
    return PasteDetailResponse(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        favorite = favorite,
        createTime = createTime,
        remote = remote,
        hash = hash,
        content = content,
    )
}
