package com.crosspaste.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the JSON wire contract of the /cli API (design D3, option b).
 *
 * The CLI binary (:cli, Kotlin/Native) defines its own mirror DTOs and cannot
 * share these classes, so the serialized field names ARE the contract. If one
 * of these tests fails, the CLI-side DTOs must be updated in the same change —
 * only add fields, never rename or change the meaning of existing ones.
 */
class CliDtoContractTest {

    private val json = Json { encodeDefaults = true }

    private fun fieldsOf(encoded: String): Set<String> = json.parseToJsonElement(encoded).jsonObject.keys

    @Test
    fun `status dto keeps its wire fields`() {
        val encoded =
            json.encodeToString(
                CliStatusDto.serializer(),
                CliStatusDto(
                    appVersion = "2.1.7",
                    appInstanceId = "instance",
                    apiVersion = CLI_API_VERSION,
                    port = 13129,
                    pasteboardListening = true,
                    deviceCount = 1,
                    pasteCount = 2L,
                ),
            )
        assertEquals(
            setOf(
                "appVersion",
                "appInstanceId",
                "apiVersion",
                "port",
                "pasteboardListening",
                "deviceCount",
                "pasteCount",
            ),
            fieldsOf(encoded),
        )
    }

    @Test
    fun `paste summary and list dto keep their wire fields`() {
        val summary =
            CliPasteSummaryDto(
                id = 1L,
                typeName = "text",
                source = "app",
                size = 5L,
                tagged = false,
                createTime = 123L,
                preview = "hello",
                remote = false,
            )
        assertEquals(
            setOf("id", "typeName", "source", "size", "tagged", "createTime", "preview", "remote"),
            fieldsOf(json.encodeToString(CliPasteSummaryDto.serializer(), summary)),
        )
        assertEquals(
            setOf("items", "total"),
            fieldsOf(
                json.encodeToString(
                    CliPasteListDto.serializer(),
                    CliPasteListDto(items = listOf(summary), total = 1L),
                ),
            ),
        )
    }

    @Test
    fun `paste detail dto keeps its wire fields`() {
        val encoded =
            json.encodeToString(
                CliPasteDetailDto.serializer(),
                CliPasteDetailDto(
                    id = 1L,
                    typeName = "text",
                    source = null,
                    size = 5L,
                    tagged = true,
                    createTime = 123L,
                    remote = false,
                    hash = "abc",
                    content = "hello",
                    rawContent = "<b>hello</b>",
                    filePaths = listOf("/tmp/images/shot.png"),
                ),
            )
        assertEquals(
            setOf(
                "id",
                "typeName",
                "source",
                "size",
                "tagged",
                "createTime",
                "remote",
                "hash",
                "content",
                "rawContent",
                "filePaths",
            ),
            fieldsOf(encoded),
        )
    }

    @Test
    fun `device dto keeps its wire fields`() {
        val encoded =
            json.encodeToString(
                CliDeviceDto.serializer(),
                CliDeviceDto(
                    appInstanceId = "instance",
                    deviceName = "device",
                    noteName = null,
                    platform = "Macos",
                    appVersion = "2.1.7",
                    connectState = 0,
                    connectHostAddress = "192.168.1.2",
                    port = 13129,
                    allowSend = true,
                    allowReceive = true,
                ),
            )
        assertEquals(
            setOf(
                "appInstanceId",
                "deviceName",
                "noteName",
                "platform",
                "appVersion",
                "connectState",
                "connectHostAddress",
                "port",
                "allowSend",
                "allowReceive",
            ),
            fieldsOf(encoded),
        )
    }

    @Test
    fun `tag copy config and message dtos keep their wire fields`() {
        assertEquals(
            setOf("id", "name", "color"),
            fieldsOf(json.encodeToString(CliTagDto.serializer(), CliTagDto(1L, "Favorite", 0xFF0000L))),
        )
        assertEquals(
            setOf("name"),
            fieldsOf(json.encodeToString(CliTagCreateRequest.serializer(), CliTagCreateRequest("Work"))),
        )
        assertEquals(
            setOf("text"),
            fieldsOf(json.encodeToString(CliCopyRequest.serializer(), CliCopyRequest("hello"))),
        )
        assertEquals(
            setOf("id"),
            fieldsOf(json.encodeToString(CliCopyResponse.serializer(), CliCopyResponse(1L))),
        )
        assertEquals(
            setOf("key", "value"),
            fieldsOf(json.encodeToString(CliConfigSetRequest.serializer(), CliConfigSetRequest("port", "13130"))),
        )
        assertEquals(
            setOf("message"),
            fieldsOf(json.encodeToString(CliMessageDto.serializer(), CliMessageDto("ok"))),
        )
    }

    @Test
    fun `pair dtos keep their wire fields`() {
        assertEquals(
            setOf("appInstanceId", "deviceName", "platform", "appVersion", "pairingVersion", "credentialType"),
            fieldsOf(
                json.encodeToString(
                    CliNearbyDeviceDto.serializer(),
                    CliNearbyDeviceDto(
                        appInstanceId = "instance",
                        deviceName = "device",
                        platform = "Macos",
                        appVersion = "2.1.7",
                        pairingVersion = 2,
                        credentialType = "SAS_CODE",
                    ),
                ),
            ),
        )
        assertEquals(
            setOf("appInstanceId"),
            fieldsOf(
                json.encodeToString(
                    CliPairInitiateRequest.serializer(),
                    CliPairInitiateRequest("instance"),
                ),
            ),
        )
        assertEquals(
            setOf("sessionId", "appInstanceId", "deviceName", "credentialType", "peerFingerprint", "pinExpiresAt"),
            fieldsOf(
                json.encodeToString(
                    CliPairSessionDto.serializer(),
                    CliPairSessionDto(
                        sessionId = "session",
                        appInstanceId = "instance",
                        deviceName = "device",
                        credentialType = "V3_PIN",
                        peerFingerprint = "AB:CD",
                        pinExpiresAt = 123L,
                    ),
                ),
            ),
        )
        assertEquals(
            setOf("sessionId", "code"),
            fieldsOf(
                json.encodeToString(
                    CliPairSubmitRequest.serializer(),
                    CliPairSubmitRequest("session", "123456"),
                ),
            ),
        )
        assertEquals(
            setOf("paired", "retryable", "message"),
            fieldsOf(
                json.encodeToString(
                    CliPairSubmitResultDto.serializer(),
                    CliPairSubmitResultDto(paired = true, retryable = false, message = "ok"),
                ),
            ),
        )
        assertEquals(
            setOf("sessionId"),
            fieldsOf(
                json.encodeToString(
                    CliPairCancelRequest.serializer(),
                    CliPairCancelRequest("session"),
                ),
            ),
        )
    }

    @Test
    fun `endpoint file keeps its wire fields`() {
        val encoded =
            json.encodeToString(
                CliEndpoint.serializer(),
                CliEndpoint(
                    pid = 42L,
                    socketPath = "/tmp/crosspaste-user/cli.sock",
                    apiVersion = CLI_API_VERSION,
                    appInstanceId = "instance",
                ),
            )
        assertEquals(
            setOf("pid", "socketPath", "apiVersion", "appInstanceId"),
            fieldsOf(encoded),
        )
    }
}
