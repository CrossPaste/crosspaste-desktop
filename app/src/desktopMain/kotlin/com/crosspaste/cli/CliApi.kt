package com.crosspaste.cli

import kotlinx.serialization.Serializable

/**
 * Version of the local /cli HTTP API served over the Unix domain socket.
 * Bump when the wire contract changes in a way the CLI must know about.
 */
const val CLI_API_VERSION = 1

const val CLI_ENDPOINT_FILE_NAME = "cli-endpoint.json"

/**
 * Wire DTOs for the /cli API.
 *
 * The CLI binary (Kotlin/Native, :cli module) defines its own mirror of these
 * classes and parses with ignoreUnknownKeys; the JSON field names below are the
 * contract. Only add fields, never change the meaning of existing ones.
 * CliDtoContractTest pins the serialized form.
 */
@Serializable
data class CliStatusDto(
    val appVersion: String,
    val appInstanceId: String,
    val apiVersion: Int,
    val port: Int,
    val pasteboardListening: Boolean,
    val deviceCount: Int,
    val pasteCount: Long,
)

@Serializable
data class CliPasteSummaryDto(
    val id: Long,
    val typeName: String,
    val source: String?,
    val size: Long,
    val tagged: Boolean,
    val createTime: Long,
    val preview: String,
    val remote: Boolean,
)

@Serializable
data class CliPasteListDto(
    val items: List<CliPasteSummaryDto>,
    val total: Long,
)

@Serializable
data class CliPasteDetailDto(
    val id: Long,
    val typeName: String,
    val source: String?,
    val size: Long,
    val tagged: Boolean,
    val createTime: Long,
    val remote: Boolean,
    val hash: String,
    val content: String?,
)

@Serializable
data class CliDeviceDto(
    val appInstanceId: String,
    val deviceName: String,
    val noteName: String?,
    val platform: String,
    val appVersion: String,
    val connectState: Int,
    val connectHostAddress: String?,
    val port: Int,
    val allowSend: Boolean,
    val allowReceive: Boolean,
)

@Serializable
data class CliTagDto(
    val id: Long,
    val name: String,
    val color: Long,
)

@Serializable
data class CliTagCreateRequest(
    val name: String,
)

@Serializable
data class CliCopyRequest(
    val text: String,
)

@Serializable
data class CliCopyResponse(
    val id: Long,
)

@Serializable
data class CliConfigSetRequest(
    val key: String,
    val value: String,
)

@Serializable
data class CliMessageDto(
    val message: String,
)
