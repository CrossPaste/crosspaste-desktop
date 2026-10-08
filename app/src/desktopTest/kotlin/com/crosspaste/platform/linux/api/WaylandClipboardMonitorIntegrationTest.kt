package com.crosspaste.platform.linux.api

import com.crosspaste.test.IntegrationTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [WaylandClipboardMonitor] against a real compositor: a headless
 * `sway` (wlroots implements both data-control variants) started on a private
 * socket, with `wl-copy` from wl-clipboard acting as the copying application.
 * Skipped wherever `sway` or `wl-copy` is not installed.
 */
@IntegrationTest
class WaylandClipboardMonitorIntegrationTest {

    private lateinit var runtimeDir: File
    private var sway: Process? = null
    private lateinit var socketPath: String
    private lateinit var displayName: String

    private val selections = LinkedBlockingQueue<WaylandSelection>()
    private var monitor: WaylandClipboardMonitor? = null

    @BeforeTest
    fun startCompositor() {
        assumeTrue(System.getProperty("os.name").lowercase().contains("linux"), "Linux only")
        assumeTrue(onPath("sway") && onPath("wl-copy"), "needs sway and wl-clipboard on PATH")

        // Unix socket paths are limited to 107 bytes, so stay in /tmp rather than the
        // build dir. createTempDirectory makes it 0700, as XDG_RUNTIME_DIR must be.
        runtimeDir = Files.createTempDirectory(File("/tmp").toPath(), "cp-wl").toFile()

        sway =
            ProcessBuilder("sway", "-c", "/dev/null", "--unsupported-gpu")
                .apply {
                    environment()["XDG_RUNTIME_DIR"] = runtimeDir.absolutePath
                    environment()["WLR_BACKENDS"] = "headless"
                    environment()["WLR_LIBINPUT_NO_DEVICES"] = "1"
                    environment()["WLR_RENDERER"] = "pixman"
                    environment().remove("WAYLAND_DISPLAY")
                    environment().remove("DISPLAY")
                }.redirectErrorStream(true)
                .redirectOutput(File(runtimeDir, "sway.log"))
                .start()

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var socket: File? = null
        while (socket == null && System.nanoTime() < deadline) {
            socket = runtimeDir.listFiles()?.firstOrNull { it.name.matches(Regex("wayland-\\d+")) }
            if (socket == null) Thread.sleep(100)
        }
        assumeTrue(socket != null, "sway did not create a socket: ${File(runtimeDir, "sway.log").readText()}")
        socketPath = socket!!.absolutePath
        displayName = socket.name
    }

    @AfterTest
    fun stopCompositor() {
        monitor?.stop()
        sway?.let {
            it.destroy()
            it.waitFor(5, TimeUnit.SECONDS)
            it.destroyForcibly()
        }
        if (::runtimeDir.isInitialized) runtimeDir.deleteRecursively()
    }

    private fun onPath(binary: String): Boolean =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .any { File(it, binary).canExecute() }

    private fun wlCopy(
        mimeType: String?,
        payload: ByteArray,
    ) {
        val command = mutableListOf("wl-copy")
        if (mimeType != null) command += listOf("-t", mimeType)
        // wl-copy forks a daemon that keeps serving the selection and inherits
        // stdout, so its output goes to a file rather than a pipe we would block on.
        val log = File(runtimeDir, "wl-copy.log")
        val process =
            ProcessBuilder(command)
                .apply {
                    environment()["XDG_RUNTIME_DIR"] = runtimeDir.absolutePath
                    environment()["WAYLAND_DISPLAY"] = displayName
                }.redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        process.outputStream.use { it.write(payload) }
        // The parent exits once the daemon owns the selection.
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "wl-copy did not return")
        assertEquals(0, process.exitValue(), log.readText())
    }

    private fun startMonitor(): WaylandClipboardMonitor {
        val monitor = WaylandClipboardMonitor(displayName = socketPath) { selections.add(it) }
        assertTrue(monitor.start(), "monitor should start against sway")
        this.monitor = monitor
        return monitor
    }

    private fun nextSelection(): WaylandSelection =
        assertNotNull(selections.poll(10, TimeUnit.SECONDS), "no selection event")

    @Test
    fun `reports the pre-launch selection as initial and later copies as changes`() {
        wlCopy("text/plain;charset=utf-8", "before".encodeToByteArray())
        val monitor = startMonitor()

        val initial = nextSelection()
        assertTrue(initial.initial)
        assertTrue("text/plain;charset=utf-8" in initial.mimeTypes, initial.toString())
        assertContentEquals("before".encodeToByteArray(), monitor.read(initial, "text/plain;charset=utf-8"))

        wlCopy("text/plain;charset=utf-8", "after".encodeToByteArray())
        val next = nextSelection()
        assertFalse(next.initial)
        assertContentEquals("after".encodeToByteArray(), monitor.read(next, "text/plain;charset=utf-8"))

        // The earlier selection is gone: reading it must not touch a destroyed offer.
        assertFalse(monitor.isCurrent(initial))
        assertNull(monitor.read(initial, "text/plain;charset=utf-8"))
    }

    @Test
    fun `reads a binary image payload copied by a native wayland client`() {
        val monitor = startMonitor()
        selections.poll(5, TimeUnit.SECONDS) // the initial (possibly empty) selection

        val png = ByteArray(300_000) { (it * 31).toByte() }.also { it[0] = 0x89.toByte() }
        wlCopy("image/png", png)

        val selection = nextSelection()
        assertEquals(listOf("image/png"), selection.mimeTypes)
        assertContentEquals(png, monitor.read(selection, "image/png"))
    }

    @Test
    fun `stop tears down and later reads refuse`() {
        val monitor = startMonitor()
        selections.poll(5, TimeUnit.SECONDS)
        wlCopy(null, "x".encodeToByteArray())
        val selection = nextSelection()

        monitor.stop()
        monitor.stop() // idempotent

        assertFalse(monitor.isCurrent(selection))
        assertNull(monitor.read(selection, "text/plain"))
    }
}
