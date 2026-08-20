package com.crosspaste.mouse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseIpcProtocolTest {
    private val json = MouseIpcProtocol.json

    @Test
    fun `encode Start command with v2 peer`() {
        val cmd: IpcCommand =
            IpcCommand.Start(
                port = 4243,
                peers =
                    listOf(
                        IpcPeer(
                            name = "Desktop",
                            address = "192.168.1.10:4243",
                            position = Position(1920, 0),
                            deviceId = "uuid-a",
                            // fingerprint INTENTIONALLY omitted — product decision
                        ),
                    ),
            )
        val out = json.encodeToString(IpcCommand.serializer(), cmd)
        assertTrue(out.contains(""""cmd":"start""""))
        assertTrue(out.contains(""""port":4243"""))
        assertTrue(out.contains(""""device_id":"uuid-a""""))
        assertTrue(!out.contains("fingerprint"))
    }

    @Test
    fun `decode Ready event from v1 daemon`() {
        val line = """{"event":"ready","screens":[],"port":4243}"""
        val ev = json.decodeFromString(IpcEvent.serializer(), line)
        assertTrue(ev is IpcEvent.Ready)
        assertEquals(4243, ev.port)
    }

    @Test
    fun `decode Initialized event from v2 daemon`() {
        val line = """{"event":"initialized","screens":[],"protocol_version":2}"""
        val ev = json.decodeFromString(IpcEvent.serializer(), line)
        assertTrue(ev is IpcEvent.Initialized)
        assertEquals(2, ev.protocolVersion)
    }

    @Test
    fun `decode PeerScreensLearned with screens`() {
        val line =
            """{"event":"peer_screens_learned","device_id":"d1","screens":[""" +
                """{"id":1,"width":2560,"height":1440,"x":0,"y":0,"scale_factor":2.0,"is_primary":true}]}"""
        val ev = json.decodeFromString(IpcEvent.serializer(), line)
        assertTrue(ev is IpcEvent.PeerScreensLearned)
        assertEquals("d1", ev.deviceId)
        assertEquals(1, ev.screens.size)
        assertEquals(2560, ev.screens[0].width)
    }

    @Test
    fun `decode Warning event preserves code`() {
        val line = """{"event":"warning","code":"macos_accessibility","message":"grant access"}"""
        val ev = json.decodeFromString(IpcEvent.serializer(), line)
        assertTrue(ev is IpcEvent.Warning)
        assertEquals("macos_accessibility", ev.code)
    }

    @Test
    fun `unknown event variant fails loudly`() {
        val line = """{"event":"made_up_event"}"""
        runCatching { json.decodeFromString(IpcEvent.serializer(), line) }
            .onSuccess { error("should have failed, got $it") }
    }
}
