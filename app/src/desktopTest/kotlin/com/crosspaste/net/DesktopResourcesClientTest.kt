package com.crosspaste.net

import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.extension.ProxyType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopResourcesClientTest {

    @Test
    fun `manual SOCKS client performs a SOCKS handshake`() {
        FakeSocksServer().use { proxy ->
            val client =
                DesktopResourcesClient.createClient(
                    KotlinLogging.logger {},
                    Proxy(ProxyType.SOCKS, "127.0.0.1", proxy.port),
                )
            try {
                val body =
                    runBlocking {
                        client.get("http://crosspaste-socks-test.invalid/resource").bodyAsText()
                    }

                assertEquals("ok", body)
                assertEquals("crosspaste-socks-test.invalid", proxy.awaitRequestedHost())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `manual HTTP client sends the absolute request target to the proxy`() {
        FakeHttpProxyServer().use { proxy ->
            val client =
                DesktopResourcesClient.createClient(
                    KotlinLogging.logger {},
                    Proxy(ProxyType.HTTP, "127.0.0.1", proxy.port),
                )
            try {
                val body =
                    runBlocking {
                        client.get("http://crosspaste-http-proxy-test.invalid/resource").bodyAsText()
                    }

                assertEquals("ok", body)
                assertEquals(
                    "GET http://crosspaste-http-proxy-test.invalid/resource HTTP/1.1",
                    proxy.awaitRequestLine(),
                )
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `concurrent first requests create one client per proxy`() =
        runBlocking {
            val configManager = mockk<DesktopConfigManager>()
            every { configManager.getCurrentConfig() } returns
                DesktopAppConfig(
                    language = "en",
                    useManualProxy = true,
                    proxyType = ProxyType.HTTP,
                    proxyHost = "proxy.test",
                    proxyPort = "8080",
                )

            val createCount = AtomicInteger()
            val client =
                DesktopResourcesClient(
                    configManager = configManager,
                    userDataPathProvider = mockk(relaxed = true),
                    clientFactory = { _, _ ->
                        createCount.incrementAndGet()
                        repeat(100_000) { Thread.onSpinWait() }
                        mockk(relaxed = true)
                    },
                )

            val clients =
                List(64) {
                    async(Dispatchers.Default) {
                        client.getHttpClient()
                    }
                }.awaitAll()

            assertEquals(2, createCount.get(), "one base client and one proxy client should be created")
            assertEquals(1, clients.toSet().size)
            client.close()
        }

    private class FakeHttpProxyServer : AutoCloseable {

        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val requestLine = AtomicReference<String>()
        private val failure = AtomicReference<Throwable>()

        val port: Int = server.localPort

        private val worker =
            thread(name = "fake-http-proxy-server", isDaemon = true) {
                try {
                    server.accept().use { socket ->
                        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                        val output = BufferedOutputStream(socket.getOutputStream())
                        val headers = readHttpHeaders(input)
                        requestLine.set(headers.lineSequence().first())
                        output.write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        output.flush()
                    }
                } catch (e: SocketException) {
                    if (!server.isClosed) failure.set(e)
                } catch (e: Throwable) {
                    failure.set(e)
                }
            }

        fun awaitRequestLine(): String {
            failure.get()?.let { throw AssertionError("Fake HTTP proxy server failed", it) }
            return assertNotNull(requestLine.get())
        }

        override fun close() {
            server.close()
            worker.join(5_000)
            failure.get()?.let { throw AssertionError("Fake HTTP proxy server failed", it) }
        }
    }

    private class FakeSocksServer : AutoCloseable {

        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val requestedHost = AtomicReference<String>()
        private val failure = AtomicReference<Throwable>()

        val port: Int = server.localPort

        private val worker =
            thread(name = "fake-socks-server", isDaemon = true) {
                try {
                    server.accept().use { socket ->
                        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                        val output = BufferedOutputStream(socket.getOutputStream())

                        assertEquals(5, input.readUnsignedByte())
                        val methodCount = input.readUnsignedByte()
                        repeat(methodCount) { input.readUnsignedByte() }
                        output.write(byteArrayOf(5, 0))
                        output.flush()

                        assertEquals(5, input.readUnsignedByte())
                        assertEquals(1, input.readUnsignedByte())
                        assertEquals(0, input.readUnsignedByte())
                        val host = readAddress(input)
                        input.readUnsignedShort()
                        requestedHost.set(host)

                        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
                        output.flush()
                        readHttpHeaders(input)
                        output.write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        output.flush()
                    }
                } catch (e: SocketException) {
                    if (!server.isClosed) failure.set(e)
                } catch (e: Throwable) {
                    failure.set(e)
                }
            }

        fun awaitRequestedHost(): String {
            failure.get()?.let { throw AssertionError("Fake SOCKS server failed", it) }
            return assertNotNull(requestedHost.get())
        }

        private fun readAddress(input: DataInputStream): String =
            when (val addressType = input.readUnsignedByte()) {
                1 -> InetAddress.getByAddress(ByteArray(4).also(input::readFully)).hostAddress
                3 -> {
                    val length = input.readUnsignedByte()
                    String(ByteArray(length).also(input::readFully), StandardCharsets.US_ASCII)
                }
                4 -> InetAddress.getByAddress(ByteArray(16).also(input::readFully)).hostAddress
                else -> error("Unsupported SOCKS address type: $addressType")
            }

        override fun close() {
            server.close()
            worker.join(5_000)
            failure.get()?.let { throw AssertionError("Fake SOCKS server failed", it) }
        }
    }

    private companion object {

        fun readHttpHeaders(input: DataInputStream): String {
            val bytes = java.io.ByteArrayOutputStream()
            var matched = 0
            val terminator = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            while (matched < terminator.size) {
                check(bytes.size() < 16 * 1024) { "HTTP headers exceed test limit" }
                val byte = input.readByte()
                bytes.write(byte.toInt())
                matched = if (byte == terminator[matched]) matched + 1 else 0
            }
            return bytes.toString(StandardCharsets.US_ASCII)
        }
    }
}
