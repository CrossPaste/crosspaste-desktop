package com.crosspaste.headless

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Black-box tests for the headless daemon lifecycle: each test boots the real
 * application (`CrossPaste --headless`) as a subprocess in DEVELOPMENT mode
 * with an isolated working directory, so all state (`.user` data dir, lock,
 * pid file, CLI endpoint) stays inside a temp dir. The socket directory is
 * isolated per test via `java.io.tmpdir` so a developer's live instance is
 * never touched.
 *
 * POSIX-only: `Process.destroy()` must deliver SIGTERM so that JVM shutdown
 * hooks run; on Windows it terminates the process without running hooks.
 */
class HeadlessDaemonProcessTest {

    private fun isPosix(): Boolean =
        FileSystems
            .getDefault()
            .supportedFileAttributeViews()
            .contains("posix")

    private fun newWorkDir(): Path = Files.createTempDirectory("crosspaste-daemon-test")

    /** Short base for AF_UNIX sockets: must keep the socket path under 100 bytes. */
    private fun newSocketTmpDir(): Path {
        val dir = Path.of("/tmp", "cs-dt-${Random.nextInt(100000, 999999)}")
        Files.createDirectories(dir)
        return dir
    }

    private fun launchDaemon(
        workDir: Path,
        socketTmpDir: Path,
    ): Process {
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val command =
            listOf(
                javaBin,
                "-DappEnv=DEVELOPMENT",
                "-Djava.net.preferIPv4Stack=true",
                "-Djava.io.tmpdir=$socketTmpDir",
                "-cp",
                System.getProperty("java.class.path"),
                "com.crosspaste.CrossPaste",
                "--headless",
            )
        return ProcessBuilder(command)
            .directory(workDir.toFile())
            .redirectOutput(workDir.resolve("stdout.log").toFile())
            .redirectError(workDir.resolve("stderr.log").toFile())
            .start()
    }

    private fun awaitFile(
        path: Path,
        timeoutMs: Long = 120_000,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (path.exists()) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun endpointFile(workDir: Path): Path = workDir.resolve(".user").resolve("cli-endpoint.json")

    private fun pidFile(workDir: Path): Path = workDir.resolve(".user").resolve("crosspaste.pid")

    private fun stderr(workDir: Path): String =
        workDir
            .resolve("stderr.log")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()

    @Test
    fun `daemon publishes its control plane and SIGTERM cleans everything up`() {
        if (!isPosix()) return
        val workDir = newWorkDir()
        val socketTmpDir = newSocketTmpDir()
        val daemon = launchDaemon(workDir, socketTmpDir)
        try {
            assertTrue(
                awaitFile(endpointFile(workDir)),
                "daemon must publish the CLI endpoint file; stderr: ${stderr(workDir)}",
            )
            assertTrue(awaitFile(pidFile(workDir)), "daemon must write its pid file")

            val socketPath =
                Regex("\"socketPath\"\\s*:\\s*\"([^\"]+)\"")
                    .find(endpointFile(workDir).readText())
                    ?.groupValues
                    ?.get(1)
            assertTrue(!socketPath.isNullOrEmpty(), "endpoint file must contain the socket path")
            assertTrue(Path.of(socketPath).exists(), "unix socket must exist while the daemon runs")

            daemon.destroy() // SIGTERM
            assertTrue(daemon.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "daemon must exit on SIGTERM")

            assertFalse(pidFile(workDir).exists(), "pid file must be removed on SIGTERM")
            assertFalse(endpointFile(workDir).exists(), "endpoint file must be removed on SIGTERM")
            assertFalse(Path.of(socketPath).exists(), "socket file must be removed on SIGTERM")
        } finally {
            daemon.destroyForcibly()
            socketTmpDir.toFile().deleteRecursively()
            workDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `second headless instance fails loudly with exit code 1`() {
        if (!isPosix()) return
        val workDir = newWorkDir()
        val socketTmpDir = newSocketTmpDir()
        val first = launchDaemon(workDir, socketTmpDir)
        try {
            assertTrue(
                awaitFile(endpointFile(workDir)),
                "first daemon must start; stderr: ${stderr(workDir)}",
            )

            val secondDir = Files.createDirectory(workDir.resolve("second-logs"))
            val second =
                ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-DappEnv=DEVELOPMENT",
                    "-Djava.net.preferIPv4Stack=true",
                    "-Djava.io.tmpdir=$socketTmpDir",
                    "-cp",
                    System.getProperty("java.class.path"),
                    "com.crosspaste.CrossPaste",
                    "--headless",
                ).directory(workDir.toFile()) // same data dir -> same app.lock
                    .redirectOutput(secondDir.resolve("stdout.log").toFile())
                    .redirectError(secondDir.resolve("stderr.log").toFile())
                    .start()
            try {
                assertTrue(
                    second.waitFor(120, java.util.concurrent.TimeUnit.SECONDS),
                    "second instance must exit",
                )
                assertEquals(1, second.exitValue(), "second headless instance must exit with code 1")
                assertTrue(
                    secondDir.resolve("stderr.log").readText().contains("already running"),
                    "second instance must report the conflict on stderr",
                )
                // The second instance must not have destroyed the first one's files.
                assertTrue(endpointFile(workDir).exists(), "running daemon's endpoint file must survive")
                assertTrue(pidFile(workDir).exists(), "running daemon's pid file must survive")
            } finally {
                second.destroyForcibly()
            }
        } finally {
            first.destroy()
            first.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            first.destroyForcibly()
            socketTmpDir.toFile().deleteRecursively()
            workDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `daemon exits with code 1 when the CLI control plane cannot start`() {
        if (!isPosix()) return
        val workDir = newWorkDir()
        // A tmpdir long enough that the socket path exceeds the 100-byte guard
        val longTmpDir = workDir.resolve("x".repeat(130))
        Files.createDirectories(longTmpDir)
        val daemon = launchDaemon(workDir, longTmpDir)
        try {
            assertTrue(
                daemon.waitFor(120, java.util.concurrent.TimeUnit.SECONDS),
                "daemon must exit when the CLI server cannot start",
            )
            assertEquals(1, daemon.exitValue(), "CLI control-plane failure must be a daemon startup failure")
            assertTrue(
                stderr(workDir).contains("failed to start in headless mode"),
                "daemon must report the startup failure on stderr; stderr: ${stderr(workDir)}",
            )
            assertFalse(endpointFile(workDir).exists(), "no endpoint file may be left behind")
        } finally {
            daemon.destroyForcibly()
            workDir.toFile().deleteRecursively()
        }
    }
}
