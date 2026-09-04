package com.crosspaste.net.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class WsChunkedPayloadTest {

    private fun sessionOver(channel: Channel<Frame>): WebSocketSession =
        mockk<WebSocketSession> {
            coEvery { send(any()) } coAnswers { channel.send(firstArg()) }
        }

    private suspend fun drainFrames(channel: Channel<Frame>): List<Frame> {
        val frames = mutableListOf<Frame>()
        while (true) {
            frames.add(channel.tryReceive().getOrNull() ?: break)
        }
        return frames
    }

    @Test
    fun chunkCapablePeer_largePayload_roundTripsAcrossChunks() =
        runTest {
            val channel = Channel<Frame>(Channel.UNLIMITED)
            val wsSession = WsSession(sessionOver(channel), "peer", peerSupportsChunkedPayload = true)
            val payload = ByteArray(WS_PAYLOAD_CHUNK_SIZE * 2 + 123) { (it % 251).toByte() }

            wsSession.sendEnvelope(WsEnvelope(type = WsMessageType.PASTE_PUSH, payload = payload))
            channel.close()

            val received = receiveWsEnvelope(channel)

            assertNotNull(received)
            assertEquals(3, received.header.payloadChunkCount)
            assertContentEquals(payload, received.envelope.payload)
        }

    @Test
    fun chunkCapablePeer_smallPayload_staysSingleFrame() =
        runTest {
            val channel = Channel<Frame>(Channel.UNLIMITED)
            val wsSession = WsSession(sessionOver(channel), "peer", peerSupportsChunkedPayload = true)
            val payload = ByteArray(1024) { it.toByte() }

            wsSession.sendEnvelope(WsEnvelope(type = WsMessageType.PASTE_PUSH, payload = payload))
            channel.close()

            val frames = drainFrames(channel)

            assertEquals(2, frames.size)
            val binary = frames[1]
            assertContentEquals(payload, (binary as Frame.Binary).readBytes())
        }

    @Test
    fun legacyPeer_largePayload_staysSingleFrame() =
        runTest {
            val channel = Channel<Frame>(Channel.UNLIMITED)
            val wsSession = WsSession(sessionOver(channel), "peer", peerSupportsChunkedPayload = false)
            val payload = ByteArray(WS_PAYLOAD_CHUNK_SIZE + 1)

            wsSession.sendEnvelope(WsEnvelope(type = WsMessageType.PASTE_PUSH, payload = payload))
            channel.close()

            val frames = drainFrames(channel)

            assertEquals(2, frames.size)
            assertEquals(payload.size, (frames[1] as Frame.Binary).readBytes().size)
        }

    @Test
    fun legacyHeaderWithoutChunkCount_decodesAsSingleChunk() =
        runTest {
            val channel = Channel<Frame>(Channel.UNLIMITED)
            channel.send(Frame.Text("""{"type":"paste_push","hasPayload":true}"""))
            channel.send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
            channel.close()

            val received = receiveWsEnvelope(channel)

            assertNotNull(received)
            assertEquals(1, received.header.payloadChunkCount)
            assertContentEquals(byteArrayOf(1, 2, 3), received.envelope.payload)
        }

    @Test
    fun zeroChunkCount_isRejected() =
        runTest {
            val channel = Channel<Frame>(Channel.UNLIMITED)
            channel.send(Frame.Text("""{"type":"paste_push","hasPayload":true,"payloadChunkCount":0}"""))
            channel.close()

            assertFailsWith<IllegalArgumentException> { receiveWsEnvelope(channel) }
        }

    @Test
    fun excessiveChunkCount_isRejected() =
        runTest {
            val channel = Channel<Frame>(Channel.UNLIMITED)
            val count = WS_MAX_PAYLOAD_CHUNK_COUNT + 1
            channel.send(Frame.Text("""{"type":"paste_push","hasPayload":true,"payloadChunkCount":$count}"""))
            channel.close()

            assertFailsWith<IllegalArgumentException> { receiveWsEnvelope(channel) }
        }

    @Test
    fun truncatedChunkSequence_isRejected() =
        runTest {
            val channel = Channel<Frame>(Channel.UNLIMITED)
            channel.send(Frame.Text("""{"type":"paste_push","hasPayload":true,"payloadChunkCount":2}"""))
            channel.send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
            channel.close()

            assertFailsWith<IllegalArgumentException> { receiveWsEnvelope(channel) }
        }

    @Test
    fun legacyAuthDtosWithoutPairingVersion_decodeAsNull() =
        runTest {
            val json =
                com.crosspaste.utils
                    .getJsonUtils()
                    .JSON

            val challenge =
                json.decodeFromString<WsAuthChallenge>(
                    """{"sessionId":"abc","nonce":"AAECAw=="}""",
                )
            val proof = json.decodeFromString<WsAuthProof>("""{"authenticationCode":"AAECAw=="}""")

            assertEquals(null, challenge.pairingVersion)
            assertEquals(null, proof.pairingVersion)
            assertEquals(emptySet(), challenge.capabilities)
            assertEquals(emptySet(), proof.capabilities)
        }
}
