package com.crosspaste.mouse

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MousePeerMapperTest {
    private fun sri(
        appInstanceId: String,
        deviceId: String,
        deviceName: String,
        host: String?,
        port: Int = 4243,
    ) = SyncRuntimeInfo(
        appInstanceId = appInstanceId,
        appVersion = "x",
        userName = "u",
        deviceId = deviceId,
        deviceName = deviceName,
        platform = Platform(name = "Mac", arch = "arm64", bitMode = 64, version = "14"),
        hostInfoList =
            host?.let {
                listOf(HostInfo(hostAddress = it, networkPrefixLength = 24.toShort()))
            } ?: emptyList(),
        port = port,
        connectHostAddress = host,
    )

    @Test
    fun `maps paired device with known address to IpcPeer without fingerprint`() {
        val layout = mapOf("inst-1" to Position(1920, 0))
        val peers =
            MousePeerMapper.map(
                syncs = listOf(sri("inst-1", "dev-1", "Desktop", "192.168.1.10", 4243)),
                layout = layout,
            )
        assertEquals(1, peers.size)
        val p = peers[0]
        assertEquals("Desktop", p.name)
        assertEquals("192.168.1.10:4243", p.address)
        assertEquals(Position(1920, 0), p.position)
        assertEquals("inst-1", p.deviceId)
        // Fingerprint must stay null per product decision.
        assertNull(runCatching { IpcPeer::class.java.getDeclaredField("fingerprint") }.getOrNull())
    }

    @Test
    fun `drops devices without a connect host address`() {
        val peers =
            MousePeerMapper.map(
                syncs = listOf(sri("inst-1", "dev-1", "Desktop", host = null)),
                layout = mapOf("inst-1" to Position(0, 0)),
            )
        assertTrue(peers.isEmpty())
    }

    @Test
    fun `drops devices missing layout entry (not configured yet)`() {
        val peers =
            MousePeerMapper.map(
                syncs = listOf(sri("inst-1", "dev-1", "Desktop", "192.168.1.10")),
                layout = emptyMap(),
            )
        assertTrue(peers.isEmpty())
    }

    @Test
    fun `key is appInstanceId — matches what desktop uses elsewhere`() {
        val peers =
            MousePeerMapper.map(
                syncs = listOf(sri("inst-abc", "dev-xyz", "Desktop", "192.168.1.10")),
                layout = mapOf("inst-abc" to Position(0, 1080)),
            )
        assertEquals("inst-abc", peers.single().deviceId)
    }
}
