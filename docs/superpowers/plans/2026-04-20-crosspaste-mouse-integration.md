# crosspaste-mouse Plugin Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the `crosspaste-mouse` Rust daemon as a plugin subprocess of crosspaste-desktop, so users can share one keyboard/mouse across paired devices with a visual drag-to-arrange UI.

**Architecture:** crosspaste-desktop spawns `crosspaste-mouse plugin` as a child process, speaks the documented JSON Lines IPC protocol (see `~/crosspaste-mouse/ai/docs/PLUGIN_PROTOCOL.md`) over stdin/stdout. Peers are derived from `SyncRuntimeInfo` (already-paired devices) — no cert fingerprint is sent; trust boundary is "desktop told us this peer address is valid." A Compose Canvas lets the user drag remote device screens in a virtual layout, producing per-device `Position(x, y)` offsets that are pushed down via `update_layout` (or `stop`+`start` fallback while daemon ticket #9 is pending).

**Tech Stack:**
- **Desktop side:** Kotlin (`desktopMain`), `kotlinx.serialization` for JSON Lines, Compose Multiplatform, Koin DI, kotlinx.coroutines, MockK for tests.
- **Daemon side:** existing Rust binary at `~/crosspaste-mouse/target/release/crosspaste-mouse` (built externally for MVP; bundling is out of scope).
- **Test runner:** `./gradlew app:desktopTest` (NOT `app:test` — see `CLAUDE.md`/memory).

**Scope:**
- In: spawning daemon, command/event plumbing, peer mapping from `SyncRuntimeInfo`, settings UI with arrangement canvas, permission-warning UX, persisting per-device `Position`.
- Out: bundling/building the Rust binary into the installer (dev-mode users point at their cargo build via env var; production bundling is a follow-up plan), daemon-side tickets #8/#9/#10-#13 (we work around them), mobile platforms (desktop-only feature).

**Non-negotiable decisions (from product owner):**
- **No cert fingerprint.** `IpcPeer.fingerprint` is never set. The `SkipServerVerification` that the daemon already does is acceptable because crosspaste-desktop has already vetted the peer address via its own pairing flow.
- Code lives in `desktopMain`, not `commonMain` — mobile will never run this daemon.
- Conform to `AppUISize` (no inline dp literals) and prefer `io.ktor.util.collections` over JDK concurrent collections (`CLAUDE.md`).

---

## File Structure

**New files (desktopMain):**
```
app/src/desktopMain/kotlin/com/crosspaste/mouse/
  MouseDaemonBinary.kt            — binary discovery (system prop → env → bundled → PATH)
  MouseIpcProtocol.kt             — sealed IpcCommand / IpcEvent + data classes
  MouseDaemonProcess.kt           — Process wrapper: stdin writes, stdout line → event Flow
  MouseDaemonClient.kt            — high-level API: start/stop/updateLayout/getStatus + capabilities
  MouseLayoutStore.kt             — persist Map<deviceId, Position> in DesktopAppConfig
  MousePeerMapper.kt              — SyncRuntimeInfo → IpcPeer (no fingerprint)
  MouseDaemonManager.kt           — Koin lifecycle owner; watches config + paired devices

app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/
  MouseSettingsScreen.kt          — top-level Compose screen (toggle + peer list + canvas)
  ScreenArrangementViewModel.kt   — VM backing the canvas
  ScreenCanvas.kt                 — drag-to-arrange Compose Canvas
  MousePermissionDialog.kt        — reacts to IpcEvent.Warning
```

**New tests (desktopTest):**
```
app/src/desktopTest/kotlin/com/crosspaste/mouse/
  MouseIpcProtocolTest.kt
  MouseDaemonProcessTest.kt       — uses injected streams (no real process)
  MouseDaemonClientTest.kt        — uses fake MouseDaemonProcess
  MousePeerMapperTest.kt
  MouseLayoutStoreTest.kt
  MouseDaemonManagerTest.kt
```

**Modified files:**
- `app/src/desktopMain/kotlin/com/crosspaste/DesktopAppModule.kt` — register `mouseModule()`
- `app/src/desktopMain/kotlin/com/crosspaste/config/DesktopAppConfig.kt` — add `mouseEnabled: Boolean` + `mouseListenPort: Int`
- `app/src/commonMain/kotlin/com/crosspaste/ui/Route.kt` — add `MouseSettings` route
- `app/src/commonMain/kotlin/com/crosspaste/ui/settings/Settings.kt` — add nav entry
- `app/src/desktopMain/resources/i18n/en.properties` and `zh.properties` (others via `i18n_batch_update.sh`)

---

## Phase 1 — IPC Protocol Types

Goal: pure data layer — command/event sealed hierarchies + JSON Lines round-trip. No process, no IO.

### Task 1: Command / event / shared types

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseIpcProtocol.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseIpcProtocolTest.kt`

- [ ] **Step 1: Write failing serialization tests**

```kotlin
// MouseIpcProtocolTest.kt
package com.crosspaste.mouse

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseIpcProtocolTest {
    private val json = MouseIpcProtocol.json

    @Test
    fun `encode Start command with v2 peer`() {
        val cmd: IpcCommand = IpcCommand.Start(
            port = 4243,
            peers = listOf(
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
```

- [ ] **Step 2: Run tests — expect compile errors (types don't exist yet)**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseIpcProtocolTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement the protocol types**

```kotlin
// MouseIpcProtocol.kt
package com.crosspaste.mouse

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

object MouseIpcProtocol {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "__unused__" // overridden per hierarchy via @JsonClassDiscriminator
        }
}

@Serializable
data class Position(
    val x: Int,
    val y: Int,
)

@Serializable
data class ScreenInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    @SerialName("scale_factor") val scaleFactor: Double,
    @SerialName("is_primary") val isPrimary: Boolean,
)

@Serializable
data class IpcPeer(
    val name: String,
    val address: String,
    val position: Position,
    // v2 additions — all optional for back-compat
    @SerialName("device_id") val deviceId: String? = null,
    // fingerprint intentionally NOT exposed — desktop never sends it.
    // (Remote-cached screens aren't useful to us; omitted too.)
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("cmd")
sealed class IpcCommand {

    @Serializable
    @SerialName("start")
    data class Start(
        val port: Int,
        val peers: List<IpcPeer>,
    ) : IpcCommand()

    @Serializable
    @SerialName("stop")
    object Stop : IpcCommand()

    @Serializable
    @SerialName("update_layout")
    data class UpdateLayout(
        val peers: List<IpcPeer>,
    ) : IpcCommand()

    @Serializable
    @SerialName("get_status")
    object GetStatus : IpcCommand()

    @Serializable
    @SerialName("enumerate_local_screens")
    object EnumerateLocalScreens : IpcCommand()

    @Serializable
    @SerialName("get_capabilities")
    object GetCapabilities : IpcCommand()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("event")
sealed class IpcEvent {

    @Serializable
    @SerialName("initialized")
    data class Initialized(
        val screens: List<ScreenInfo>,
        @SerialName("protocol_version") @EncodeDefault val protocolVersion: Int,
    ) : IpcEvent()

    @Serializable
    @SerialName("ready")
    data class Ready(
        val screens: List<ScreenInfo>,
        val port: Int,
    ) : IpcEvent()

    @Serializable
    @SerialName("session_started")
    data class SessionStarted(
        val port: Int,
        @SerialName("local_device_id") val localDeviceId: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("peer_connected")
    data class PeerConnected(
        val name: String,
        @SerialName("device_id") val deviceId: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("peer_screens_learned")
    data class PeerScreensLearned(
        @SerialName("device_id") val deviceId: String,
        val screens: List<ScreenInfo>,
    ) : IpcEvent()

    @Serializable
    @SerialName("peer_disconnected")
    data class PeerDisconnected(
        val name: String,
        val reason: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("mode_changed")
    data class ModeChanged(
        val mode: String,
        val target: String? = null,
    ) : IpcEvent()

    @Serializable
    @SerialName("status")
    data class Status(
        val running: Boolean,
        val mode: String,
        @SerialName("connected_peers") val connectedPeers: List<String>,
    ) : IpcEvent()

    @Serializable
    @SerialName("local_screens")
    data class LocalScreens(
        val screens: List<ScreenInfo>,
    ) : IpcEvent()

    @Serializable
    @SerialName("capabilities")
    data class Capabilities(
        @SerialName("protocol_version") val protocolVersion: Int,
        val features: List<String>,
    ) : IpcEvent()

    @Serializable
    @SerialName("warning")
    data class Warning(
        val code: String,
        val message: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("license_status")
    data class LicenseStatus(
        val status: String,
        val message: String,
        @SerialName("trial_remaining_secs") val trialRemainingSecs: Long? = null,
    ) : IpcEvent()

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("stopped")
    object Stopped : IpcEvent()
}
```

- [ ] **Step 4: Run tests — expect all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseIpcProtocolTest"`
Expected: 5 passing tests. If `Json.classDiscriminator` interaction warns, the per-hierarchy `@JsonClassDiscriminator` annotation takes precedence — the root-level `classDiscriminator` is a fallback for non-annotated hierarchies.

- [ ] **Step 5: Format + commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseIpcProtocol.kt \
        app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseIpcProtocolTest.kt
git commit -m ":sparkles: add MouseIpcProtocol types for crosspaste-mouse plugin IPC"
```

---

## Phase 2 — Process + Stream Layer

Goal: spawn the daemon binary, expose `events: Flow<IpcEvent>` and `suspend fun send(command)`. Tests drive `InputStream`/`OutputStream` directly — no real subprocess.

### Task 2: Binary discovery

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonBinary.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonBinaryTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// MouseDaemonBinaryTest.kt
package com.crosspaste.mouse

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MouseDaemonBinaryTest {
    private val propKey = "crosspaste.mouse.binary"
    private val savedProp = System.getProperty(propKey)

    @AfterTest
    fun restore() {
        if (savedProp != null) System.setProperty(propKey, savedProp) else System.clearProperty(propKey)
    }

    @Test
    fun `system property wins when set and file exists`() {
        val file = Files.createTempFile("fake-daemon", "").toFile().apply { deleteOnExit() }
        System.setProperty(propKey, file.absolutePath)
        assertEquals(file.absolutePath, MouseDaemonBinary.resolve(envLookup = { null })?.absolutePath)
    }

    @Test
    fun `env var used when system property absent`() {
        System.clearProperty(propKey)
        val file = Files.createTempFile("fake-daemon", "").toFile().apply { deleteOnExit() }
        val path = MouseDaemonBinary.resolve(envLookup = { if (it == "CROSSPASTE_MOUSE_BIN") file.absolutePath else null })
        assertEquals(file.absolutePath, path?.absolutePath)
    }

    @Test
    fun `returns null when nothing resolves`() {
        System.clearProperty(propKey)
        assertNull(MouseDaemonBinary.resolve(envLookup = { null }, candidatePaths = emptyList()))
    }

    @Test
    fun `system property pointing at missing file is ignored`() {
        System.setProperty(propKey, "/definitely/does/not/exist/mouse")
        assertNull(MouseDaemonBinary.resolve(envLookup = { null }, candidatePaths = emptyList()))
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonBinaryTest"`

- [ ] **Step 3: Implement binary discovery**

```kotlin
// MouseDaemonBinary.kt
package com.crosspaste.mouse

import java.io.File

object MouseDaemonBinary {
    private const val SYSTEM_PROPERTY = "crosspaste.mouse.binary"
    private const val ENV_VAR = "CROSSPASTE_MOUSE_BIN"

    /**
     * Resolution order:
     *   1. -Dcrosspaste.mouse.binary=<path>
     *   2. $CROSSPASTE_MOUSE_BIN
     *   3. Any path in `candidatePaths` (e.g. bundled in resources/bin/...)
     * Returns null if nothing points at an existing regular file.
     */
    fun resolve(
        envLookup: (String) -> String? = System::getenv,
        candidatePaths: List<String> = defaultCandidatePaths(),
    ): File? {
        System.getProperty(SYSTEM_PROPERTY)?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
        envLookup(ENV_VAR)?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
        return candidatePaths.asSequence()
            .map(::File)
            .firstOrNull { it.isFile }
    }

    private fun defaultCandidatePaths(): List<String> {
        // Production bundling is a follow-up plan; for now the only
        // candidate is a dev-mode symlink next to the app jar.
        val home = System.getProperty("user.home") ?: return emptyList()
        return listOf("$home/crosspaste-mouse/target/release/crosspaste-mouse")
    }
}
```

- [ ] **Step 4: Run tests — expect all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonBinaryTest"`

- [ ] **Step 5: Format + commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonBinary.kt \
        app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonBinaryTest.kt
git commit -m ":sparkles: add MouseDaemonBinary resolver with env/sysprop/bundled fallback"
```

### Task 3: Daemon process with injected streams

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonProcess.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonProcessTest.kt`

Design: `MouseDaemonProcess` is constructed from already-opened input/output streams (so tests inject `PipedInputStream`/`PipedOutputStream`). The subprocess bootstrapping is a separate factory function `MouseDaemonProcess.spawn(binary)`.

- [ ] **Step 1: Write failing tests**

```kotlin
// MouseDaemonProcessTest.kt
package com.crosspaste.mouse

import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

class MouseDaemonProcessTest {

    @Test
    fun `reads one event per stdout line and parses it`() = runTest {
        val daemonStdoutSink = PipedOutputStream()
        val daemonStdoutSource = PipedInputStream(daemonStdoutSink)
        val daemonStdinSink = PipedOutputStream()
        // Host never reads stdin (the daemon does); we only need to see what was written.

        val process = MouseDaemonProcess(
            stdout = daemonStdoutSource,
            stdin = daemonStdinSink,
            onClose = {},
        )

        // Write one line as if daemon emitted it
        daemonStdoutSink.write("""{"event":"ready","screens":[],"port":4243}""".toByteArray())
        daemonStdoutSink.write('\n'.code)
        daemonStdoutSink.flush()

        val ev = withTimeout(2000) { process.events.first() }
        assertTrue(ev is IpcEvent.Ready)
        assertEquals(4243, ev.port)

        process.close()
    }

    @Test
    fun `send writes a line-terminated JSON command to stdin`() = runTest {
        val daemonStdinSink = java.io.ByteArrayOutputStream()
        val process = MouseDaemonProcess(
            stdout = java.io.ByteArrayInputStream(ByteArray(0)),
            stdin = daemonStdinSink,
            onClose = {},
        )

        process.send(IpcCommand.GetStatus)
        val written = daemonStdinSink.toString(Charsets.UTF_8)
        assertTrue(written.endsWith("\n"), "expected newline terminator, got: $written")
        assertTrue(written.contains(""""cmd":"get_status""""))

        process.close()
    }

    @Test
    fun `malformed line emits Error event but keeps stream alive`() = runTest {
        val sink = PipedOutputStream()
        val source = PipedInputStream(sink)
        val process = MouseDaemonProcess(
            stdout = source,
            stdin = java.io.ByteArrayOutputStream(),
            onClose = {},
        )
        sink.write("not json\n".toByteArray())
        sink.write("""{"event":"stopped"}""".toByteArray())
        sink.write('\n'.code)
        sink.flush()

        val collected = mutableListOf<IpcEvent>()
        withTimeout(2000) {
            process.events.collect {
                collected.add(it)
                if (it is IpcEvent.Stopped) return@collect
            }
        }
        assertTrue(collected.any { it is IpcEvent.Error })
        assertTrue(collected.any { it is IpcEvent.Stopped })
        process.close()
    }
}
```

- [ ] **Step 2: Run tests — compile failure expected**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonProcessTest"`

- [ ] **Step 3: Implement the process wrapper**

```kotlin
// MouseDaemonProcess.kt
package com.crosspaste.mouse

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class MouseDaemonProcess internal constructor(
    stdout: InputStream,
    stdin: OutputStream,
    private val onClose: () -> Unit,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer: BufferedWriter =
        BufferedWriter(OutputStreamWriter(stdin, StandardCharsets.UTF_8))
    private val writeLock = Mutex()

    private val _events =
        MutableSharedFlow<IpcEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<IpcEvent> = _events.asSharedFlow()

    private val readerJob: Job =
        scope.launch {
            BufferedReader(InputStreamReader(stdout, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val event =
                        runCatching {
                            MouseIpcProtocol.json.decodeFromString(IpcEvent.serializer(), line)
                        }.getOrElse {
                            IpcEvent.Error("malformed daemon output: ${it.message}")
                        }
                    _events.emit(event)
                    if (event is IpcEvent.Stopped) {
                        // keep reading — daemon may restart session; only exit on EOF.
                    }
                }
            }
        }

    suspend fun send(command: IpcCommand) {
        val line = MouseIpcProtocol.json.encodeToString(IpcCommand.serializer(), command)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    override fun close() {
        scope.cancel()
        runCatching { writer.close() }
        onClose()
    }

    companion object {
        /**
         * Spawns the daemon binary in plugin mode. Throws [IllegalStateException]
         * if the binary cannot be located.
         */
        fun spawn(binary: File): MouseDaemonProcess {
            val process =
                ProcessBuilder(binary.absolutePath, "plugin")
                    .redirectErrorStream(false) // keep stderr separate (daemon tracing logs go there)
                    .start()
            return MouseDaemonProcess(
                stdout = process.inputStream,
                stdin = process.outputStream,
                onClose = {
                    runCatching { process.destroy() }
                    runCatching { process.waitFor() }
                },
            )
        }
    }
}
```

- [ ] **Step 4: Run tests — expect all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonProcessTest"`

- [ ] **Step 5: Format + commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonProcess.kt \
        app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonProcessTest.kt
git commit -m ":sparkles: add MouseDaemonProcess wrapper with stdin/stdout JSON Lines"
```

---

## Phase 3 — High-level Client + Layout Persistence + Peer Mapping

### Task 4: Peer mapping (SyncRuntimeInfo → IpcPeer)

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/mouse/MousePeerMapper.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/mouse/MousePeerMapperTest.kt`

Trust model (non-negotiable): fingerprint is NEVER set. The daemon runs with `SkipServerVerification`; desktop is the trust authority via its existing pairing flow.

- [ ] **Step 1: Write failing tests**

```kotlin
// MousePeerMapperTest.kt
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
        hostInfoList = host?.let { listOf(HostInfo(hostAddress = it, networkPrefixLength = 24)) } ?: emptyList(),
        port = port,
        connectHostAddress = host,
    )

    @Test
    fun `maps paired device with known address to IpcPeer without fingerprint`() {
        val layout = mapOf("inst-1" to Position(1920, 0))
        val peers = MousePeerMapper.map(
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
        val peers = MousePeerMapper.map(
            syncs = listOf(sri("inst-1", "dev-1", "Desktop", host = null)),
            layout = mapOf("inst-1" to Position(0, 0)),
        )
        assertTrue(peers.isEmpty())
    }

    @Test
    fun `drops devices missing layout entry (not configured yet)`() {
        val peers = MousePeerMapper.map(
            syncs = listOf(sri("inst-1", "dev-1", "Desktop", "192.168.1.10")),
            layout = emptyMap(),
        )
        assertTrue(peers.isEmpty())
    }

    @Test
    fun `key is appInstanceId — matches what desktop uses elsewhere`() {
        val peers = MousePeerMapper.map(
            syncs = listOf(sri("inst-abc", "dev-xyz", "Desktop", "192.168.1.10")),
            layout = mapOf("inst-abc" to Position(0, 1080)),
        )
        assertEquals("inst-abc", peers.single().deviceId)
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MousePeerMapperTest"`

- [ ] **Step 3: Implement the mapper**

```kotlin
// MousePeerMapper.kt
package com.crosspaste.mouse

import com.crosspaste.db.sync.SyncRuntimeInfo

object MousePeerMapper {

    /**
     * Convert paired-device rows into daemon peers.
     * - Drops devices with no reachable host address (still sync-negotiating).
     * - Drops devices the user has not yet placed in the arrangement canvas.
     * - Uses [SyncRuntimeInfo.appInstanceId] as the peer key (daemon's `device_id`),
     *   matching what MouseLayoutStore uses.
     * - Never sets fingerprint (product decision: desktop is the trust authority).
     */
    fun map(
        syncs: List<SyncRuntimeInfo>,
        layout: Map<String, Position>,
    ): List<IpcPeer> {
        return syncs.mapNotNull { sri ->
            val host = sri.connectHostAddress ?: return@mapNotNull null
            val position = layout[sri.appInstanceId] ?: return@mapNotNull null
            IpcPeer(
                name = sri.deviceName.ifBlank { sri.appInstanceId },
                address = "$host:${sri.port}",
                position = position,
                deviceId = sri.appInstanceId,
            )
        }
    }
}
```

- [ ] **Step 4: Run tests — expect all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MousePeerMapperTest"`

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/mouse/MousePeerMapper.kt \
        app/src/desktopTest/kotlin/com/crosspaste/mouse/MousePeerMapperTest.kt
git commit -m ":sparkles: map paired SyncRuntimeInfo rows to mouse daemon peers"
```

### Task 5: Layout store (persist `Map<deviceId, Position>`)

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseLayoutStore.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseLayoutStoreTest.kt`
- Modify: `app/src/desktopMain/kotlin/com/crosspaste/config/DesktopAppConfig.kt` (add `mouseLayout: Map<String, Position>` + `mouseEnabled: Boolean` + `mouseListenPort: Int` fields with sensible defaults).

Implementation uses `DesktopConfigManager` the same way other desktop-scoped config does — check existing `DesktopAppConfig` for the pattern and follow it exactly. If `Position` can't be stored directly in that config (e.g. because it relies on `@Serializable` set up differently), wrap it in a config-local `SerializedPosition(xLocal: Int, yLocal: Int)` and convert at the store boundary.

- [ ] **Step 1: Read existing config pattern**

Read `app/src/desktopMain/kotlin/com/crosspaste/config/DesktopAppConfig.kt` and `DesktopConfigManager.kt` to understand:
- how fields are added (check at least 2 existing boolean + numeric + collection fields)
- how `@Serializable` is set up on the config class
- whether mutation goes through `update { ... }` or direct assignment + save

Record the observed pattern — Tasks 5 and 10 both rely on it.

- [ ] **Step 2: Write failing tests for the store**

```kotlin
// MouseLayoutStoreTest.kt
package com.crosspaste.mouse

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class MouseLayoutStoreTest {

    @Test
    fun `returns empty map when config has no entries`() {
        val cfg = FakeConfig(mutableMapOf())
        val store = MouseLayoutStore(cfg)
        assertEquals(emptyMap(), store.all())
    }

    @Test
    fun `upsert writes a single device position`() {
        val cfg = FakeConfig(mutableMapOf())
        val store = MouseLayoutStore(cfg)
        store.upsert("inst-1", Position(1920, 0))
        assertEquals(Position(1920, 0), store.get("inst-1"))
    }

    @Test
    fun `remove deletes an entry`() {
        val cfg = FakeConfig(mutableMapOf("inst-1" to Position(0, 0)))
        val store = MouseLayoutStore(cfg)
        store.remove("inst-1")
        assertEquals(emptyMap(), store.all())
    }

    @Test
    fun `flow emits after upsert`() = kotlinx.coroutines.test.runTest {
        val cfg = FakeConfig(mutableMapOf())
        val store = MouseLayoutStore(cfg)
        val updates = mutableListOf<Map<String, Position>>()
        val job = kotlinx.coroutines.launch { store.flow().collect { updates.add(it) } }
        kotlinx.coroutines.yield()
        store.upsert("inst-1", Position(1920, 0))
        kotlinx.coroutines.yield()
        job.cancel()
        assertEquals(Position(1920, 0), updates.last()["inst-1"])
    }

    // Fake config stand-in that matches the store's expected interface.
    private class FakeConfig(val backing: MutableMap<String, Position>) : MouseLayoutStore.Backing {
        private val flow = kotlinx.coroutines.flow.MutableStateFlow(backing.toMap())
        override fun snapshot() = backing.toMap()
        override fun set(newMap: Map<String, Position>) { backing.clear(); backing.putAll(newMap); flow.value = snapshot() }
        override fun flow() = flow
    }
}
```

- [ ] **Step 3: Implement MouseLayoutStore with a `Backing` interface**

Keep the store decoupled from `DesktopAppConfig` through a tiny adapter interface, so tests can substitute a fake. Real binding happens in the Koin module (Task 10).

```kotlin
// MouseLayoutStore.kt
package com.crosspaste.mouse

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class MouseLayoutStore(private val backing: Backing) {

    interface Backing {
        fun snapshot(): Map<String, Position>
        fun set(newMap: Map<String, Position>)
        fun flow(): MutableStateFlow<Map<String, Position>>
    }

    fun all(): Map<String, Position> = backing.snapshot()
    fun get(deviceId: String): Position? = backing.snapshot()[deviceId]

    fun upsert(deviceId: String, position: Position) {
        val next = backing.snapshot().toMutableMap().apply { put(deviceId, position) }
        backing.set(next)
    }

    fun remove(deviceId: String) {
        val next = backing.snapshot().toMutableMap().apply { remove(deviceId) }
        backing.set(next)
    }

    fun flow(): Flow<Map<String, Position>> = backing.flow()
}
```

- [ ] **Step 4: Run tests — expect all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseLayoutStoreTest"`

- [ ] **Step 5: Wire DesktopAppConfig fields (no store-side tests yet — integration test in Task 10)**

Add to `DesktopAppConfig`:
```kotlin
val mouseEnabled: Boolean = false,
val mouseListenPort: Int = 4243,
val mouseLayout: Map<String, Position> = emptyMap(),
```

Be sure `Position` is the one from `com.crosspaste.mouse` and that its `@Serializable` annotation is present. If the existing config serializer doesn't walk imported types, add a `typealias` or re-declare locally using `@SerialName("mouse_layout")` — pattern documented in the existing module.

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseLayoutStore.kt \
        app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseLayoutStoreTest.kt \
        app/src/desktopMain/kotlin/com/crosspaste/config/DesktopAppConfig.kt
git commit -m ":sparkles: persist mouse arrangement layout in DesktopAppConfig"
```

### Task 6: MouseDaemonClient (capabilities + start/stop/updateLayout)

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonClient.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonClientTest.kt`

Design: `MouseDaemonClient` wraps a `MouseDaemonProcess`. Exposes suspend APIs that serialize a send and (where applicable) wait for a correlated response event. For `updateLayout`:
- If daemon capabilities include `"update_layout"` AND daemon returned `protocol_version >= 2` → send `UpdateLayout`.
- Otherwise → fall back to `stop` → `start`. This is the current reality (daemon's v2 still returns `Error { "update_layout not yet supported" }`).

- [ ] **Step 1: Write failing tests**

```kotlin
// MouseDaemonClientTest.kt
package com.crosspaste.mouse

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseDaemonClientTest {

    private class FakeProcess : MouseDaemonClient.DaemonHandle {
        val sent = mutableListOf<IpcCommand>()
        private val _events = MutableSharedFlow<IpcEvent>(extraBufferCapacity = 32)
        override val events: SharedFlow<IpcEvent> = _events.asSharedFlow()
        override suspend fun send(command: IpcCommand) { sent.add(command) }
        override fun close() {}
        suspend fun emit(ev: IpcEvent) { _events.emit(ev) }
    }

    @Test
    fun `negotiates capabilities on startup and records features`() = runTest {
        val proc = FakeProcess()
        val client = MouseDaemonClient(proc)
        val job = launch { client.run() }
        yield()
        proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
        yield()
        // client should have asked for capabilities
        assertTrue(proc.sent.any { it is IpcCommand.GetCapabilities })
        proc.emit(
            IpcEvent.Capabilities(
                protocolVersion = 2,
                features = listOf("get_capabilities", "enumerate_local_screens"),
            ),
        )
        yield()
        assertTrue(client.capabilities.value.protocolVersion == 2)
        assertTrue("enumerate_local_screens" in client.capabilities.value.features)
        job.cancel()
    }

    @Test
    fun `updateLayout falls back to stop+start when feature unsupported`() = runTest {
        val proc = FakeProcess()
        val client = MouseDaemonClient(proc)
        val job = launch { client.run() }
        yield()
        proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
        proc.emit(IpcEvent.Capabilities(protocolVersion = 2, features = emptyList())) // no update_layout
        yield()

        client.updateLayout(port = 4243, peers = emptyList())
        yield()

        val sentNames = proc.sent.map { it::class.simpleName }
        assertEquals(listOf("GetCapabilities", "Stop", "Start"), sentNames)
        job.cancel()
    }

    @Test
    fun `updateLayout uses native command when feature present`() = runTest {
        val proc = FakeProcess()
        val client = MouseDaemonClient(proc)
        val job = launch { client.run() }
        yield()
        proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
        proc.emit(
            IpcEvent.Capabilities(protocolVersion = 2, features = listOf("update_layout")),
        )
        yield()

        client.updateLayout(port = 4243, peers = emptyList())
        yield()

        assertTrue(proc.sent.last() is IpcCommand.UpdateLayout)
        job.cancel()
    }

    @Test
    fun `events reach consumers as a SharedFlow`() = runTest {
        val proc = FakeProcess()
        val client = MouseDaemonClient(proc)
        val job = launch { client.run() }
        yield()
        val got = launch { client.events.first { it is IpcEvent.PeerConnected } }
        proc.emit(IpcEvent.PeerConnected(name = "Desktop", deviceId = "d1"))
        got.join()
        job.cancel()
    }
}
```

- [ ] **Step 2: Run tests — compile failure**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonClientTest"`

- [ ] **Step 3: Implement the client**

```kotlin
// MouseDaemonClient.kt
package com.crosspaste.mouse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

class MouseDaemonClient(
    private val handle: DaemonHandle,
) {
    interface DaemonHandle {
        val events: SharedFlow<IpcEvent>
        suspend fun send(command: IpcCommand)
        fun close()
    }

    data class CapabilitySnapshot(
        val protocolVersion: Int = 1,
        val features: List<String> = emptyList(),
    )

    private val _capabilities = MutableStateFlow(CapabilitySnapshot())
    val capabilities: StateFlow<CapabilitySnapshot> = _capabilities.asStateFlow()

    val events: SharedFlow<IpcEvent> get() = handle.events

    /**
     * Drives the daemon: asks for capabilities once Initialized/Ready lands,
     * forwards caps into [capabilities]. Suspends forever; cancel to stop.
     */
    suspend fun run() {
        handle.events.collect { ev ->
            when (ev) {
                is IpcEvent.Initialized, is IpcEvent.Ready -> {
                    handle.send(IpcCommand.GetCapabilities)
                }
                is IpcEvent.Capabilities -> {
                    _capabilities.value =
                        CapabilitySnapshot(protocolVersion = ev.protocolVersion, features = ev.features)
                }
                else -> Unit
            }
        }
    }

    suspend fun start(port: Int, peers: List<IpcPeer>) {
        handle.send(IpcCommand.Start(port, peers))
    }

    suspend fun stop() {
        handle.send(IpcCommand.Stop)
    }

    /**
     * Applies a new layout. If the daemon advertises `update_layout`, sends it
     * natively; otherwise falls back to stop + start (current reality — daemon
     * ticket #9 still pending).
     */
    suspend fun updateLayout(port: Int, peers: List<IpcPeer>) {
        if ("update_layout" in _capabilities.value.features) {
            handle.send(IpcCommand.UpdateLayout(peers))
        } else {
            handle.send(IpcCommand.Stop)
            handle.send(IpcCommand.Start(port, peers))
        }
    }

    suspend fun getStatus() {
        handle.send(IpcCommand.GetStatus)
    }

    suspend fun enumerateLocalScreens() {
        handle.send(IpcCommand.EnumerateLocalScreens)
    }

    fun close() {
        handle.close()
    }
}
```

Also add a thin adapter so `MouseDaemonProcess` satisfies `DaemonHandle`:

```kotlin
// MouseDaemonProcess.kt — append
fun MouseDaemonProcess.asDaemonHandle(): MouseDaemonClient.DaemonHandle =
    object : MouseDaemonClient.DaemonHandle {
        override val events = this@asDaemonHandle.events
        override suspend fun send(command: IpcCommand) = this@asDaemonHandle.send(command)
        override fun close() = this@asDaemonHandle.close()
    }
```

- [ ] **Step 4: Run tests — all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonClientTest"`

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonClient.kt \
        app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonProcess.kt \
        app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonClientTest.kt
git commit -m ":sparkles: add MouseDaemonClient with capability negotiation and stop+start fallback"
```

### Task 7: MouseDaemonManager lifecycle

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonManager.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonManagerTest.kt`

Responsibilities:
- Observes: `mouseEnabled` flag, `mouseListenPort`, `MouseLayoutStore.flow()`, `syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow()`.
- When enabled: spawn daemon, connect client, send `Start { port, peers }`.
- On any of (layout / paired devices / port) change while running: call `client.updateLayout(...)`.
- When disabled: `client.stop()` + close process.
- Exposes `state: StateFlow<MouseState>` (disabled / starting / running(peers, mode) / warning(code, message) / error(msg)).

Because this is the bulk of the runtime behavior, we test with substituted `DaemonHandle` + `SyncRuntimeInfoDao` + `MouseLayoutStore`.

- [ ] **Step 1: Write failing tests**

```kotlin
// MouseDaemonManagerTest.kt
package com.crosspaste.mouse

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.platform.Platform
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseDaemonManagerTest {

    private class FakeHandle : MouseDaemonClient.DaemonHandle {
        val sent = mutableListOf<IpcCommand>()
        private val _events = MutableSharedFlow<IpcEvent>(extraBufferCapacity = 32)
        override val events: SharedFlow<IpcEvent> = _events.asSharedFlow()
        override suspend fun send(command: IpcCommand) { sent.add(command) }
        override fun close() {}
        suspend fun emit(ev: IpcEvent) { _events.emit(ev) }
    }

    private fun sri(inst: String, host: String?) = SyncRuntimeInfo(
        appInstanceId = inst,
        appVersion = "x",
        userName = "u",
        deviceId = "d-$inst",
        deviceName = "Dev-$inst",
        platform = Platform("Mac", "arm64", 64, "14"),
        port = 4243,
        connectHostAddress = host,
    )

    @Test
    fun `does nothing when disabled`() = runTest {
        val handle = FakeHandle()
        val store = inMemoryStore()
        val mgr = newManager(handle, store, enabled = MutableStateFlow(false))
        val job = launch { mgr.run() }
        yield()
        assertTrue(handle.sent.isEmpty())
        job.cancel()
    }

    @Test
    fun `sends Start with peers when enabled`() = runTest {
        val handle = FakeHandle()
        val store = inMemoryStore().apply { upsert("inst-1", Position(1920, 0)) }
        val syncs = MutableStateFlow(listOf(sri("inst-1", "192.168.1.10")))
        val mgr = newManager(handle, store, syncs = syncs, enabled = MutableStateFlow(true))
        val job = launch { mgr.run() }
        yield()
        handle.emit(IpcEvent.Initialized(emptyList(), 2))
        handle.emit(IpcEvent.Capabilities(2, emptyList()))
        yield()
        val start = handle.sent.filterIsInstance<IpcCommand.Start>().singleOrNull()
        assertTrue(start != null, "expected exactly one Start, got ${handle.sent}")
        assertEquals("192.168.1.10:4243", start.peers.single().address)
        job.cancel()
    }

    @Test
    fun `layout change triggers updateLayout`() = runTest {
        val handle = FakeHandle()
        val store = inMemoryStore().apply { upsert("inst-1", Position(1920, 0)) }
        val syncs = MutableStateFlow(listOf(sri("inst-1", "192.168.1.10")))
        val mgr = newManager(handle, store, syncs = syncs, enabled = MutableStateFlow(true))
        val job = launch { mgr.run() }
        yield()
        handle.emit(IpcEvent.Initialized(emptyList(), 2))
        handle.emit(IpcEvent.Capabilities(2, emptyList())) // no update_layout → stop+start
        yield()
        store.upsert("inst-1", Position(-1920, 0)) // user dragged peer to the left
        yield()
        // Stop+start fallback expected: [Start, Stop, Start]
        val starts = handle.sent.filterIsInstance<IpcCommand.Start>()
        assertEquals(2, starts.size)
        assertEquals(Position(-1920, 0), starts.last().peers.single().position)
        job.cancel()
    }

    @Test
    fun `disable sends Stop`() = runTest {
        val handle = FakeHandle()
        val store = inMemoryStore().apply { upsert("inst-1", Position(1920, 0)) }
        val syncs = MutableStateFlow(listOf(sri("inst-1", "192.168.1.10")))
        val enabled = MutableStateFlow(true)
        val mgr = newManager(handle, store, syncs = syncs, enabled = enabled)
        val job = launch { mgr.run() }
        yield()
        handle.emit(IpcEvent.Initialized(emptyList(), 2))
        handle.emit(IpcEvent.Capabilities(2, emptyList()))
        yield()
        enabled.value = false
        yield()
        assertTrue(handle.sent.any { it is IpcCommand.Stop })
        job.cancel()
    }

    // --- test helpers ---
    private fun inMemoryStore(): MouseLayoutStore {
        val backing = object : MouseLayoutStore.Backing {
            private val flow = MutableStateFlow<Map<String, Position>>(emptyMap())
            override fun snapshot() = flow.value
            override fun set(newMap: Map<String, Position>) { flow.value = newMap }
            override fun flow() = flow
        }
        return MouseLayoutStore(backing)
    }

    private fun newManager(
        handle: FakeHandle,
        store: MouseLayoutStore,
        syncs: MutableStateFlow<List<SyncRuntimeInfo>> = MutableStateFlow(emptyList()),
        enabled: MutableStateFlow<Boolean>,
        port: MutableStateFlow<Int> = MutableStateFlow(4243),
    ): MouseDaemonManager {
        val dao = mockk<SyncRuntimeInfoDao>()
        every { dao.getAllSyncRuntimeInfosFlow() } returns syncs
        val client = MouseDaemonClient(handle)
        return MouseDaemonManager(
            enabledFlow = enabled,
            portFlow = port,
            layoutStore = store,
            syncRuntimeInfoDao = dao,
            clientFactory = { client },
        )
    }
}
```

- [ ] **Step 2: Run — compile failure**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonManagerTest"`

- [ ] **Step 3: Implement MouseDaemonManager**

```kotlin
// MouseDaemonManager.kt
package com.crosspaste.mouse

import com.crosspaste.db.sync.SyncRuntimeInfoDao
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

sealed interface MouseState {
    object Disabled : MouseState
    object Starting : MouseState
    data class Running(val connectedPeers: List<String>) : MouseState
    data class Warning(val code: String, val message: String) : MouseState
    data class Error(val message: String) : MouseState
}

class MouseDaemonManager(
    private val enabledFlow: Flow<Boolean>,
    private val portFlow: Flow<Int>,
    private val layoutStore: MouseLayoutStore,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val clientFactory: () -> MouseDaemonClient,
) {
    private val _state = MutableStateFlow<MouseState>(MouseState.Disabled)
    val state: StateFlow<MouseState> = _state.asStateFlow()

    suspend fun run() = coroutineScope {
        var activeClient: MouseDaemonClient? = null
        var activeClientJob: kotlinx.coroutines.Job? = null
        var lastPeers: List<IpcPeer>? = null
        var lastPort: Int = -1

        combine(
            enabledFlow,
            portFlow,
            layoutStore.flow(),
            syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow(),
        ) { enabled, port, layout, syncs ->
            MergedInputs(enabled, port, MousePeerMapper.map(syncs, layout))
        }.distinctUntilChanged().collect { inputs ->
            if (!inputs.enabled) {
                activeClient?.let {
                    runCatching { it.stop() }
                    it.close()
                }
                activeClientJob?.cancel()
                activeClient = null
                activeClientJob = null
                lastPeers = null
                lastPort = -1
                _state.value = MouseState.Disabled
                return@collect
            }

            if (activeClient == null) {
                _state.value = MouseState.Starting
                val client = clientFactory()
                activeClient = client
                activeClientJob = launch { client.run() }

                // Route events into state.
                launch {
                    client.events.collect { ev ->
                        when (ev) {
                            is IpcEvent.PeerConnected ->
                                _state.value = MouseState.Running(
                                    connectedPeers = (state.value as? MouseState.Running)
                                        ?.connectedPeers.orEmpty() + ev.name,
                                )
                            is IpcEvent.PeerDisconnected ->
                                _state.value = MouseState.Running(
                                    connectedPeers = (state.value as? MouseState.Running)
                                        ?.connectedPeers.orEmpty() - ev.name,
                                )
                            is IpcEvent.Warning -> _state.value = MouseState.Warning(ev.code, ev.message)
                            is IpcEvent.Error -> _state.value = MouseState.Error(ev.message)
                            else -> Unit
                        }
                    }
                }
                client.start(inputs.port, inputs.peers)
                lastPort = inputs.port
                lastPeers = inputs.peers
                return@collect
            }

            if (lastPort != inputs.port || lastPeers != inputs.peers) {
                activeClient?.updateLayout(inputs.port, inputs.peers)
                lastPort = inputs.port
                lastPeers = inputs.peers
            }
        }
    }

    private data class MergedInputs(
        val enabled: Boolean,
        val port: Int,
        val peers: List<IpcPeer>,
    )
}
```

- [ ] **Step 4: Run tests — expect all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.mouse.MouseDaemonManagerTest"`

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/mouse/MouseDaemonManager.kt \
        app/src/desktopTest/kotlin/com/crosspaste/mouse/MouseDaemonManagerTest.kt
git commit -m ":sparkles: add MouseDaemonManager lifecycle — enables daemon driven by config and paired devices"
```

### Task 8: Koin wiring

**Files:**
- Modify: `app/src/desktopMain/kotlin/com/crosspaste/DesktopAppModule.kt`
- Modify: `app/src/desktopMain/kotlin/com/crosspaste/config/DesktopAppConfig.kt` (already touched in Task 5)

- [ ] **Step 1: Read the existing DesktopAppModule**

Read `DesktopAppModule.kt` to see how other services are registered (single vs factory, how flows are derived from config). Note the `appModule()` function signature so you can add providers next to existing ones.

- [ ] **Step 2: Add a `mouseModule()` factory**

Create `mouseModule()` in the same file (or a sibling `DesktopMouseModule.kt` if `DesktopAppModule.kt` is already large — follow the existing pattern; e.g. `DesktopNetworkModule` is sibling). Register:
- `MouseLayoutStore` — construct a `MouseLayoutStore.Backing` that reads/writes `DesktopAppConfig.mouseLayout` through `DesktopConfigManager`. Expose the config-backed `MutableStateFlow` via the existing config-observation API.
- `MouseDaemonClient` — factory: resolve binary via `MouseDaemonBinary.resolve()`, call `MouseDaemonProcess.spawn(...)`, wrap `.asDaemonHandle()` into `MouseDaemonClient(handle)`. If the binary can't be resolved, the factory should throw a descriptive `IllegalStateException` — surfaced as `MouseState.Error` by the manager.
- `MouseDaemonManager` — single, passing:
  - `enabledFlow` = `config.map { it.mouseEnabled }.distinctUntilChanged()`
  - `portFlow` = `config.map { it.mouseListenPort }.distinctUntilChanged()`
  - `layoutStore`, `syncRuntimeInfoDao` from Koin
  - `clientFactory` = a lambda that calls `get<MouseDaemonClient>()` each time a new session is needed

- [ ] **Step 3: Register in `appModule()`**

Add `mouseModule()` to whichever central list `DesktopAppModule` exposes (likely in `DesktopModule.kt`'s `appModule()` return value). Verify by:

```bash
./gradlew app:desktopTest --tests "*MouseDaemon*"
```

- [ ] **Step 4: Start the manager at app startup**

Find where other long-lived services are `launch`ed on app start (search for `launch` in `app/src/desktopMain/kotlin/com/crosspaste/CrossPaste.kt` or similar). Add:

```kotlin
val mouseManager: MouseDaemonManager = koin.get()
applicationScope.launch { mouseManager.run() }
```

Make this match the convention in `CrossPaste.kt` for other managers; do not introduce a new scope.

- [ ] **Step 5: Run full test suite and app**

```bash
./gradlew ktlintFormat
./gradlew app:desktopTest
```

Expected: all mouse tests still pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/desktopMain/kotlin/com/crosspaste/DesktopAppModule.kt \
        app/src/desktopMain/kotlin/com/crosspaste/config/DesktopAppConfig.kt \
        app/src/desktopMain/kotlin/com/crosspaste/CrossPaste.kt
git commit -m ":sparkles: wire MouseDaemonManager into Koin and start on app launch"
```

---

## Phase 4 — Configuration UI (Settings screen + arrangement canvas)

### Task 9: ScreenArrangementViewModel

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/ScreenArrangementViewModel.kt`
- Test: `app/src/desktopTest/kotlin/com/crosspaste/ui/mouse/ScreenArrangementViewModelTest.kt`

State:
- `localScreens: List<ScreenInfo>` (from `IpcEvent.Initialized` / `LocalScreens`)
- `remoteDevices: Map<String, RemoteDeviceInfo>` where `RemoteDeviceInfo(name, screens, position)`
  - screens come from `IpcEvent.PeerScreensLearned`
  - position comes from `MouseLayoutStore`
- `selectedDeviceId: String?`

Actions:
- `onDragDevice(deviceId, dx, dy)`: updates `position` in-memory
- `onDragEnd(deviceId)`: commits to `MouseLayoutStore.upsert(deviceId, position)` (triggers manager → daemon restart)

- [ ] **Step 1: Write failing tests**

```kotlin
// ScreenArrangementViewModelTest.kt
package com.crosspaste.ui.mouse

import com.crosspaste.mouse.IpcEvent
import com.crosspaste.mouse.MouseLayoutStore
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenArrangementViewModelTest {

    private fun store(initial: Map<String, Position> = emptyMap()): MouseLayoutStore {
        val backing = object : MouseLayoutStore.Backing {
            val flow = MutableStateFlow(initial)
            override fun snapshot() = flow.value
            override fun set(newMap: Map<String, Position>) { flow.value = newMap }
            override fun flow() = flow
        }
        return MouseLayoutStore(backing)
    }

    @Test
    fun `learns local and remote screens from events`() = runTest {
        val events = MutableSharedFlow<IpcEvent>(replay = 0, extraBufferCapacity = 16)
        val vm = ScreenArrangementViewModel(events.asSharedFlow(), store())
        val job = launch { vm.observe() }
        yield()
        events.emit(
            IpcEvent.Initialized(
                screens = listOf(ScreenInfo(0, 1920, 1080, 0, 0, 1.0, true)),
                protocolVersion = 2,
            ),
        )
        events.emit(
            IpcEvent.PeerScreensLearned(
                deviceId = "inst-1",
                screens = listOf(ScreenInfo(0, 2560, 1440, 0, 0, 2.0, true)),
            ),
        )
        yield()
        assertEquals(1, vm.localScreens.value.size)
        assertEquals(2560, vm.remoteDevices.value["inst-1"]!!.screens.single().width)
        job.cancel()
    }

    @Test
    fun `drag updates in-memory position without committing`() {
        val s = store(mapOf("inst-1" to Position(1920, 0)))
        val vm = ScreenArrangementViewModel(MutableSharedFlow(), s)
        vm.seedRemote("inst-1", "Desktop", listOf(ScreenInfo(0, 2560, 1440, 0, 0, 1.0, true)))
        vm.onDragDevice("inst-1", dx = 100, dy = 0)
        assertEquals(Position(2020, 0), vm.remoteDevices.value["inst-1"]!!.position)
        assertEquals(Position(1920, 0), s.get("inst-1")) // not committed
    }

    @Test
    fun `onDragEnd commits to store`() {
        val s = store(mapOf("inst-1" to Position(1920, 0)))
        val vm = ScreenArrangementViewModel(MutableSharedFlow(), s)
        vm.seedRemote("inst-1", "Desktop", listOf(ScreenInfo(0, 2560, 1440, 0, 0, 1.0, true)))
        vm.onDragDevice("inst-1", dx = -3840, dy = 0) // user put peer on far left
        vm.onDragEnd("inst-1")
        assertEquals(Position(-1920, 0), s.get("inst-1"))
    }
}
```

- [ ] **Step 2: Run — compile failure**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.ui.mouse.ScreenArrangementViewModelTest"`

- [ ] **Step 3: Implement the view model**

```kotlin
// ScreenArrangementViewModel.kt
package com.crosspaste.ui.mouse

import com.crosspaste.mouse.IpcEvent
import com.crosspaste.mouse.MouseLayoutStore
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteDeviceInfo(
    val name: String,
    val screens: List<ScreenInfo>,
    val position: Position,
)

class ScreenArrangementViewModel(
    private val events: SharedFlow<IpcEvent>,
    private val layoutStore: MouseLayoutStore,
) {
    private val _localScreens = MutableStateFlow<List<ScreenInfo>>(emptyList())
    val localScreens: StateFlow<List<ScreenInfo>> = _localScreens.asStateFlow()

    private val _remoteDevices = MutableStateFlow<Map<String, RemoteDeviceInfo>>(emptyMap())
    val remoteDevices: StateFlow<Map<String, RemoteDeviceInfo>> = _remoteDevices.asStateFlow()

    /** For tests: inject a remote without emitting an event. */
    fun seedRemote(deviceId: String, name: String, screens: List<ScreenInfo>) {
        val existing = _remoteDevices.value[deviceId]
        val position = existing?.position ?: layoutStore.get(deviceId) ?: Position(0, 0)
        _remoteDevices.value = _remoteDevices.value + (deviceId to RemoteDeviceInfo(name, screens, position))
    }

    suspend fun observe() {
        events.collect { ev ->
            when (ev) {
                is IpcEvent.Initialized -> _localScreens.value = ev.screens
                is IpcEvent.LocalScreens -> _localScreens.value = ev.screens
                is IpcEvent.PeerScreensLearned -> {
                    val existing = _remoteDevices.value[ev.deviceId]
                    _remoteDevices.value = _remoteDevices.value + (
                        ev.deviceId to RemoteDeviceInfo(
                            name = existing?.name ?: ev.deviceId,
                            screens = ev.screens,
                            position = existing?.position ?: layoutStore.get(ev.deviceId) ?: Position(0, 0),
                        )
                    )
                }
                is IpcEvent.PeerConnected -> {
                    val existing = _remoteDevices.value[ev.deviceId]
                    if (existing != null && existing.name != ev.name) {
                        _remoteDevices.value = _remoteDevices.value + (
                            ev.deviceId to existing.copy(name = ev.name)
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun onDragDevice(deviceId: String, dx: Int, dy: Int) {
        val existing = _remoteDevices.value[deviceId] ?: return
        val next = existing.copy(position = Position(existing.position.x + dx, existing.position.y + dy))
        _remoteDevices.value = _remoteDevices.value + (deviceId to next)
    }

    fun onDragEnd(deviceId: String) {
        val dev = _remoteDevices.value[deviceId] ?: return
        layoutStore.upsert(deviceId, dev.position)
    }
}
```

- [ ] **Step 4: Run tests — all PASS**

Run: `./gradlew app:desktopTest --tests "com.crosspaste.ui.mouse.ScreenArrangementViewModelTest"`

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/ScreenArrangementViewModel.kt \
        app/src/desktopTest/kotlin/com/crosspaste/ui/mouse/ScreenArrangementViewModelTest.kt
git commit -m ":sparkles: add ScreenArrangementViewModel for mouse layout UI"
```

### Task 10: ScreenCanvas composable

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/ScreenCanvas.kt`

No unit tests for this file — it's presentational; verified manually + by ViewModel tests.

- [ ] **Step 1: Write the composable**

```kotlin
// ScreenCanvas.kt
package com.crosspaste.ui.mouse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import com.crosspaste.ui.theme.AppUISize

/**
 * Canvas that lets the user drag remote devices around a virtual desktop.
 * Local screens are locked at (0, 0). Each remote device is a rigid group
 * of its own screens; dragging any screen of the group shifts the group's
 * [Position] offset.
 */
@Composable
fun ScreenCanvas(
    viewModel: ScreenArrangementViewModel,
    modifier: Modifier = Modifier,
) {
    val locals by viewModel.localScreens.collectAsState()
    val remotes by viewModel.remoteDevices.collectAsState()
    val measurer: TextMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(AppUISize.xxxxLarge * 6)) {
        val viewportPx = maxWidth.value.coerceAtLeast(400f)
        val (bounds, scale) = remember(locals, remotes, viewportPx) {
            computeBoundsAndScale(locals, remotes, viewportPx)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppUISize.xxxxLarge * 6)
                .background(Color(0xFFF2F2F2))
                .pointerInput(remotes.keys) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            val hitId = remotes.entries.firstOrNull { (_, info) ->
                                info.screens.any { screen ->
                                    val rect = toRect(info.position, screen, bounds, scale)
                                    rect.contains(change.position)
                                }
                            }?.key ?: return@detectDragGestures
                            viewModel.onDragDevice(
                                deviceId = hitId,
                                dx = (drag.x / scale).toInt(),
                                dy = (drag.y / scale).toInt(),
                            )
                        },
                        onDragEnd = {
                            remotes.keys.forEach { viewModel.onDragEnd(it) }
                        },
                    )
                },
        ) {
            // Draw local screens (blue), origin-locked
            locals.forEach { screen ->
                val rect = toRect(Position(0, 0), screen, bounds, scale)
                drawRect(color = Color(0xFF1E88E5).copy(alpha = 0.35f), topLeft = rect.topLeft, size = rect.size)
                drawRect(color = Color(0xFF1E88E5), topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 2f))
            }
            // Draw each remote device's screens (orange), as a group
            remotes.forEach { (_, info) ->
                info.screens.forEach { screen ->
                    val rect = toRect(info.position, screen, bounds, scale)
                    drawRect(color = Color(0xFFFB8C00).copy(alpha = 0.35f), topLeft = rect.topLeft, size = rect.size)
                    drawRect(color = Color(0xFFFB8C00), topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 2f))
                    drawText(
                        textMeasurer = measurer,
                        text = AnnotatedString(info.name),
                        topLeft = rect.topLeft + Offset(4f, 4f),
                    )
                }
            }
        }
    }
}

private data class WorldBounds(val minX: Int, val minY: Int)
private data class RectFloats(val topLeft: Offset, val size: Size) {
    fun contains(p: Offset) = p.x in topLeft.x..(topLeft.x + size.width) && p.y in topLeft.y..(topLeft.y + size.height)
}

private fun computeBoundsAndScale(
    locals: List<ScreenInfo>,
    remotes: Map<String, RemoteDeviceInfo>,
    viewportPx: Float,
): Pair<WorldBounds, Float> {
    val allRects = buildList {
        locals.forEach { add(IntRect(0, 0, it.width, it.height)) }
        remotes.values.forEach { info ->
            info.screens.forEach {
                add(IntRect(info.position.x, info.position.y, info.position.x + it.width, info.position.y + it.height))
            }
        }
    }
    val minX = allRects.minOfOrNull { it.left } ?: 0
    val minY = allRects.minOfOrNull { it.top } ?: 0
    val maxX = allRects.maxOfOrNull { it.right } ?: 1920
    val worldWidth = (maxX - minX).coerceAtLeast(1920)
    val scale = ((viewportPx - 40f) / worldWidth).coerceAtLeast(0.01f)
    return WorldBounds(minX, minY) to scale
}

private fun toRect(
    position: Position,
    screen: ScreenInfo,
    bounds: WorldBounds,
    scale: Float,
): RectFloats {
    val x = (position.x - bounds.minX) * scale + 20f
    val y = (position.y - bounds.minY) * scale + 20f
    return RectFloats(
        topLeft = Offset(x, y),
        size = Size(screen.width * scale, screen.height * scale),
    )
}

private data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew ktlintFormat
./gradlew app:desktopMainClasses
```

- [ ] **Step 3: Commit**

```bash
git add app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/ScreenCanvas.kt
git commit -m ":sparkles: add drag-to-arrange ScreenCanvas for mouse layout"
```

### Task 11: MouseSettingsScreen + permission dialog + route

**Files:**
- Create: `app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/MouseSettingsScreen.kt`
- Create: `app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/MousePermissionDialog.kt`
- Modify: `app/src/commonMain/kotlin/com/crosspaste/ui/Route.kt` — add `MouseSettings` object
- Modify: wherever `Settings` screen's nav list is built (find via grep for `NetworkSettings` usage as a sibling) — add entry
- Modify: i18n keys (Task 12)

- [ ] **Step 1: Add the Route**

Edit `Route.kt`:
```kotlin
@Serializable
object MouseSettings : Route {
    const val NAME: String = "mouse_settings"
    override val name: String = NAME
}
```

- [ ] **Step 2: Write the permission dialog**

```kotlin
// MousePermissionDialog.kt
package com.crosspaste.ui.mouse

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.crosspaste.mouse.IpcEvent

@Composable
fun MousePermissionDialog(
    warning: IpcEvent.Warning?,
    onDismiss: () -> Unit,
) {
    if (warning == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(warning.code) },
        text = { Text(warning.message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
```

- [ ] **Step 3: Write the settings screen**

```kotlin
// MouseSettingsScreen.kt
package com.crosspaste.ui.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.mouse.IpcEvent
import com.crosspaste.mouse.MouseDaemonManager
import com.crosspaste.mouse.MouseState
import com.crosspaste.ui.theme.AppUISize
import org.koin.compose.koinInject

@Composable
fun MouseSettingsScreen() {
    val manager: MouseDaemonManager = koinInject()
    val viewModel: ScreenArrangementViewModel = koinInject()

    val state by manager.state.collectAsState()
    var warning by remember { mutableStateOf<IpcEvent.Warning?>(null) }

    // TODO(host-wire-up): observe manager.state or a dedicated warnings flow and set `warning`.
    // Keep simple for MVP: render current state summary; dialog populated when state is Warning.
    val currentWarning = (state as? MouseState.Warning)?.let { IpcEvent.Warning(it.code, it.message) }
    MousePermissionDialog(warning = currentWarning, onDismiss = { warning = null })

    Column(modifier = Modifier.padding(AppUISize.medium), verticalArrangement = Arrangement.spacedBy(AppUISize.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Share keyboard/mouse", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            // TODO Task 12: replace with config-backed toggle from DesktopAppConfig.mouseEnabled
            Switch(checked = state !is MouseState.Disabled, onCheckedChange = { /* config update */ })
        }
        Text(
            text = when (state) {
                MouseState.Disabled -> "Off"
                MouseState.Starting -> "Starting…"
                is MouseState.Running -> "Running — ${(state as MouseState.Running).connectedPeers.size} peer(s)"
                is MouseState.Warning -> "Warning: ${(state as MouseState.Warning).code}"
                is MouseState.Error -> "Error: ${(state as MouseState.Error).message}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(AppUISize.small))
        ScreenCanvas(viewModel = viewModel, modifier = Modifier.fillMaxWidth())
    }
}
```

- [ ] **Step 4: Wire the route into the Settings nav list**

Find the existing settings list (likely `SettingsContentView` / `Settings.kt`) and add an entry for `MouseSettings`. Follow the same pattern the `NetworkSettings` entry uses — same icon style, same i18n key mechanism.

- [ ] **Step 5: Register the nav destination**

Find the central `NavHost` / route dispatcher (grep for `NetworkSettings.NAME`). Add:
```kotlin
composable(route = MouseSettings.NAME) { MouseSettingsScreen() }
```

- [ ] **Step 6: Register ScreenArrangementViewModel in Koin**

In the mouse module, add:
```kotlin
single { ScreenArrangementViewModel(events = get<MouseDaemonClient>().events, layoutStore = get()) }
```

Caveat: `MouseDaemonClient` is re-created per session. Better pattern: bridge events through `MouseDaemonManager`. If time-constrained for MVP, expose `manager.events: SharedFlow<IpcEvent>` that multiplexes across sessions. Add this as a follow-up step in Task 7 if not already there.

- [ ] **Step 7: Verify compilation + full tests**

```bash
./gradlew ktlintFormat
./gradlew app:desktopTest
./gradlew app:desktopMainClasses
```

- [ ] **Step 8: Commit**

```bash
git add app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/ \
        app/src/commonMain/kotlin/com/crosspaste/ui/Route.kt \
        app/src/commonMain/kotlin/com/crosspaste/ui/settings/ \
        app/src/desktopMain/kotlin/com/crosspaste/DesktopAppModule.kt
git commit -m ":sparkles: add MouseSettings screen with arrangement canvas and permission dialog"
```

---

## Phase 5 — Polish: i18n + manual verification

### Task 12: i18n keys

**Files:**
- Modify: `app/src/desktopMain/resources/i18n/en.properties`
- Modify: `app/src/desktopMain/resources/i18n/zh.properties`
- Run: `./i18n_batch_update.sh` to propagate to other locales (per repo convention — check the script before running).

- [ ] **Step 1: Enumerate strings used**

From Tasks 10–11, collect hardcoded UI strings:
- `Share keyboard/mouse`
- `Off`, `Starting…`
- `Running — %d peer(s)`
- `Warning: %s`
- `Error: %s`
- Settings entry label: `Mouse`
- Permission codes (don't translate the code itself, but the message comes from daemon)

- [ ] **Step 2: Add to `en.properties`**

```
mouse_settings.title=Mouse
mouse_settings.switch=Share keyboard/mouse
mouse_settings.state.disabled=Off
mouse_settings.state.starting=Starting…
mouse_settings.state.running={0} connected peer(s)
mouse_settings.state.warning=Warning: {0}
mouse_settings.state.error=Error: {0}
mouse_settings.canvas.help=Drag a device to position its screens relative to yours.
```

- [ ] **Step 3: Add Chinese translations to `zh.properties`**

```
mouse_settings.title=鼠标共享
mouse_settings.switch=共享键盘与鼠标
mouse_settings.state.disabled=已关闭
mouse_settings.state.starting=启动中…
mouse_settings.state.running=已连接 {0} 台设备
mouse_settings.state.warning=警告：{0}
mouse_settings.state.error=错误：{0}
mouse_settings.canvas.help=拖拽设备以调整它相对于本机的屏幕位置。
```

- [ ] **Step 4: Replace hardcoded strings in `MouseSettingsScreen.kt`**

Use the project's existing i18n helper (check how `NetworkSettings` reads from i18n — likely via a `LocalI18n.current.getText(...)` or a composable).

- [ ] **Step 5: Propagate to other locales**

Read `i18n_batch_update.sh` (in repo root) to understand what it does. If it calls a translation service, confirm with the user before running. If it's a mechanical copy, run it:
```bash
./i18n_batch_update.sh
```

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add app/src/desktopMain/resources/i18n/ \
        app/src/desktopMain/kotlin/com/crosspaste/ui/mouse/MouseSettingsScreen.kt
git commit -m ":memo: add i18n strings for mouse settings screen"
```

### Task 13: Manual end-to-end verification

Not a code task — a verification checklist before the PR.

- [ ] **Step 1: Build the daemon**

```bash
cd ~/crosspaste-mouse
cargo build --release
```

- [ ] **Step 2: Run desktop with the daemon path exposed**

```bash
cd ~/crosspaste-desktop
CROSSPASTE_MOUSE_BIN=~/crosspaste-mouse/target/release/crosspaste-mouse \
  ./gradlew app:run
```

- [ ] **Step 3: In the app**

1. Pair a second device via the existing device pairing flow.
2. Open Settings → Mouse.
3. Toggle on.
4. Expect: permission warning dialog on macOS (grant Accessibility if not already).
5. Expect: "Starting…" → "1 connected peer(s)".
6. In the canvas, drag the remote device rectangle to the LEFT of the local rectangle. Release.
7. Move cursor to the left edge of the local screen → should appear on the remote device's right edge.
8. Drag the remote rectangle to the RIGHT. Release.
9. Cursor at the right edge of local → appears on remote's left.

- [ ] **Step 4: Confirm restart behavior**

Between drag-end events, watch stderr (or app logs) for QUIC re-handshake — expected in the MVP since `update_layout` falls back to `stop`+`start` until daemon ticket #9 lands.

- [ ] **Step 5: Document known gaps in commit or PR**

When opening the PR, mention:
- Layout changes drop and re-establish QUIC connections (waiting on daemon ticket #9 — Hot swap).
- Daemon binary is NOT bundled into the installer yet; production users must install separately.
- No fingerprint pinning (deliberate — desktop is trust authority).

---

## Self-Review Notes

- **Spec coverage:** Daemon discovery (Task 2), protocol (Task 1), process IO (Task 3), peer mapping without fingerprint (Task 4), layout persistence (Task 5), client w/ capability negotiation (Task 6), lifecycle (Task 7), Koin wiring (Task 8), drag UI backing (Tasks 9–10), settings screen + permissions (Task 11), i18n (Task 12), verification (Task 13). The user's statement "crosspaste-desktop will tell mouse the trusted peer address+port, so no fingerprint" is enforced in Task 4 (no fingerprint field ever set) and noted at the plan level.
- **Type consistency:** `IpcPeer.deviceId` (nullable String) used consistently from Task 1 → Task 4 → Task 7. `MouseLayoutStore.Backing` contract identical across Tasks 5, 7, 9. `MouseDaemonClient.DaemonHandle` interface used by Tasks 6 and 7 tests.
- **Gap flagged:** Task 11 Step 6 notes a Koin-wiring caveat (events across session restarts). Tracked as an inline TODO rather than a separate task — if it breaks during verification, it becomes a small follow-up commit.
- **Out of scope:** Bundling the Rust binary into the installer (needs conveyor + cargo cross-compile), daemon tickets #8 (cert pinning — not needed, per decision), #9 (update_layout hot swap — worked around via stop+start), #10–13 (polish).
