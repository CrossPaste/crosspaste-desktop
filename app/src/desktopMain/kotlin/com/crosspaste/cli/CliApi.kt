package com.crosspaste.cli

import kotlinx.serialization.Serializable

/**
 * Version of the local /cli HTTP API served over the Unix domain socket.
 * Bump when the wire contract changes in a way the CLI must know about.
 * 2: GET /cli/paste/{id}/image raw-RGBA transcode endpoint (sixel support).
 */
const val CLI_API_VERSION = 2

/** Response headers carrying the exact dimensions of a transcoded image. */
const val CLI_IMAGE_WIDTH_HEADER = "X-Image-Width"
const val CLI_IMAGE_HEIGHT_HEADER = "X-Image-Height"

const val CLI_ENDPOINT_FILE_NAME = "cli-endpoint.json"

/**
 * Secondary discovery pointer a DEVELOPMENT app (`./gradlew app:run`) writes
 * into the installed app's default user-data directory, so a PATH-installed
 * CLI can find the dev instance whose real endpoint file lives in the repo's
 * dev user dir. Production and test apps never write it.
 */
const val CLI_DEV_ENDPOINT_FILE_NAME = "cli-endpoint.dev.json"

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
    /** Human-readable summary (HTML/RTF converted to plain text). */
    val content: String?,
    /** Content exactly as stored (HTML/RTF keep their source markup). */
    val rawContent: String?,
    /**
     * Absolute paths of the stored payload files (image/file pastes only,
     * empty otherwise). The CLI runs on the same machine, so it reads the
     * bytes directly instead of streaming them over the socket.
     */
    val filePaths: List<String> = emptyList(),
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
data class CliPasteUpdateRequest(
    val content: String,
    /** Hash of the paste as the editor read it; the update is CAS'd on it. */
    val expectedHash: String,
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

@Serializable
data class CliNearbyDeviceDto(
    val appInstanceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val pairingVersion: Int?,
    /** [com.crosspaste.sync.PairingCredentialType] name the local peer would negotiate. */
    val credentialType: String,
)

@Serializable
data class CliPairInitiateRequest(
    val appInstanceId: String,
)

@Serializable
data class CliPairSessionDto(
    val sessionId: String,
    val appInstanceId: String,
    val deviceName: String,
    /** "SAS_CODE" or "V3_PIN"; QR-only peers are refused at initiate. */
    val credentialType: String,
    /** v3 only: acceptor identity-key fingerprint for out-of-band comparison. */
    val peerFingerprint: String?,
    /** v3 only: epoch millis after which the displayed PIN rotates. */
    val pinExpiresAt: Long?,
)

@Serializable
data class CliPairSubmitRequest(
    val sessionId: String,
    val code: String,
)

@Serializable
data class CliPairSubmitResultDto(
    val paired: Boolean,
    /** When false the session is closed; a new `initiate` is required. */
    val retryable: Boolean,
    val message: String,
)

@Serializable
data class CliPairCancelRequest(
    val sessionId: String,
)
