package com.crosspaste.platform.linux.api

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// org.freedesktop.portal.RemoteDesktop, the only portal that can put keystrokes
// in front of a native Wayland window: XTest stops at the XWayland border.
// https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.RemoteDesktop.html

@DBusInterfaceName("org.freedesktop.portal.RemoteDesktop")
interface RemoteDesktop : DBusInterface {
    @DBusMemberName("CreateSession")
    fun createSession(options: Map<String, Variant<*>>): DBusPath

    @DBusMemberName("SelectDevices")
    fun selectDevices(
        sessionHandle: DBusPath,
        options: Map<String, Variant<*>>,
    ): DBusPath

    @DBusMemberName("Start")
    fun start(
        sessionHandle: DBusPath,
        parentWindow: String,
        options: Map<String, Variant<*>>,
    ): DBusPath

    @DBusMemberName("NotifyKeyboardKeycode")
    fun notifyKeyboardKeycode(
        sessionHandle: DBusPath,
        options: Map<String, Variant<*>>,
        keycode: Int,
        state: UInt32,
    )
}

@DBusInterfaceName("org.freedesktop.portal.Request")
interface PortalRequest : DBusInterface {
    @DBusMemberName("Close")
    fun close()

    class Response(
        path: String,
        val response: UInt32,
        val results: Map<String, Variant<*>>,
    ) : DBusSignal(path, response, results)
}

@DBusInterfaceName("org.freedesktop.portal.Session")
interface PortalSession : DBusInterface {
    @DBusMemberName("Close")
    fun close()

    class Closed(
        path: String,
        val details: Map<String, Variant<*>>,
    ) : DBusSignal(path, details)
}

object PortalHandles {
    const val PORTAL_BUS_NAME = "org.freedesktop.portal.Desktop"
    const val PORTAL_OBJECT_PATH = "/org/freedesktop/portal/desktop"

    /** The caller's unique bus name as the portal embeds it in Request and Session paths. */
    fun sender(uniqueName: String): String = uniqueName.removePrefix(":").replace('.', '_')

    fun requestPath(
        uniqueName: String,
        token: String,
    ): String = "$PORTAL_OBJECT_PATH/request/${sender(uniqueName)}/$token"

    fun sessionPath(
        uniqueName: String,
        token: String,
    ): String = "$PORTAL_OBJECT_PATH/session/${sender(uniqueName)}/$token"

    /** X11 keycodes are the kernel's evdev codes offset by 8; the portal wants evdev. */
    fun evdevKeycode(x11Keycode: Int): Int = (x11Keycode - 8).coerceAtLeast(0)
}

/**
 * Presses a key chord through the RemoteDesktop portal.
 *
 * The first use asks the compositor for a keyboard session, which the desktop
 * confirms with the user through a dialog. With persist mode 2 the portal hands
 * back a restore token that lets later sessions — including after a restart —
 * skip the dialog, so the token is kept in the app config. Every session ends
 * with a fresh single-use token, saved as soon as the portal issues it.
 *
 * Any failure resolves to false so the caller can fall back to its manual-paste
 * hint. A denial or a missing portal is remembered for the rest of the process,
 * since asking again on every paste would only nag; a dialog the user did not
 * get to in time is closed and simply comes back with the next paste; a failure
 * while a restore token was in play drops the token and tries afresh next time.
 */
class RemoteDesktopPortalKeyboard(
    private val loadRestoreToken: () -> String,
    private val saveRestoreToken: (String) -> Unit,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    private val mutex = Mutex()

    @Volatile
    private var connection: DBusConnection? = null

    @Volatile
    private var sessionHandle: DBusPath? = null
    private var sessionClosedHandler: AutoCloseable? = null

    @Volatile
    private var givenUp = false

    private val tokenCounter = AtomicInteger()

    private class PortalTimeout(
        message: String,
    ) : RuntimeException(message)

    /** The desktop asked the user and the user said no. */
    private class PortalDenied(
        message: String,
    ) : RuntimeException(message)

    suspend fun pressAndRelease(evdevKeycodes: List<Int>): Boolean =
        mutex.withLock {
            if (givenUp || evdevKeycodes.isEmpty()) return false
            val hadRestoreToken = loadRestoreToken().isNotEmpty()
            val session =
                try {
                    ensureSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PortalTimeout) {
                    logger.warn { "RemoteDesktop portal: ${e.message}" }
                    return false
                } catch (e: PortalDenied) {
                    // The user answered; asking again on every paste would only nag.
                    logger.info { "RemoteDesktop portal: ${e.message}, not asking again this run" }
                    givenUp = true
                    return false
                } catch (e: Exception) {
                    if (hadRestoreToken) {
                        // The token may be what failed; drop it and let the next paste
                        // start over with the dialog.
                        logger.warn(e) { "RemoteDesktop portal failed with a restore token, dropping it" }
                        saveRestoreToken("")
                    } else {
                        logger.warn(e) { "RemoteDesktop portal unavailable, not asking again this run" }
                        givenUp = true
                    }
                    return false
                }
            var delivered = false
            val pressed = mutableListOf<Int>()
            try {
                val remoteDesktop = remoteDesktop()
                for (keycode in evdevKeycodes) {
                    remoteDesktop.notifyKeyboardKeycode(session, emptyMap(), keycode, PRESSED)
                    pressed.add(keycode)
                }
                delay(KEY_HOLD)
                delivered = true
                logger.info { "Paste shortcut delivered through the RemoteDesktop portal" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The compositor may have revoked the session; start over next time.
                logger.warn(e) { "RemoteDesktop portal keystroke failed, dropping the session" }
                sessionHandle = null
                delivered = false
            } finally {
                withContext(NonCancellable) {
                    val remoteDesktop = runCatching { remoteDesktop() }.getOrNull()
                    if (remoteDesktop != null) {
                        for (keycode in pressed.reversed()) {
                            runCatching {
                                remoteDesktop.notifyKeyboardKeycode(session, emptyMap(), keycode, RELEASED)
                            }
                        }
                    }
                }
            }
            return delivered
        }

    override fun close() {
        sessionClosedHandler?.close()
        sessionClosedHandler = null
        val session = sessionHandle
        val conn = connection
        if (session != null && conn != null && conn.isConnected) {
            runCatching {
                conn.getRemoteObject(PortalHandles.PORTAL_BUS_NAME, session.path, PortalSession::class.java).close()
            }
        }
        sessionHandle = null
        runCatching { connection?.close() }
        connection = null
    }

    private fun remoteDesktop(): RemoteDesktop =
        connection().getRemoteObject(
            PortalHandles.PORTAL_BUS_NAME,
            PortalHandles.PORTAL_OBJECT_PATH,
            RemoteDesktop::class.java,
        )

    private fun connection(): DBusConnection {
        val current = connection
        if (current != null && current.isConnected) {
            return current
        }
        return DBusConnectionBuilder.forSessionBus().build().also { connection = it }
    }

    private suspend fun ensureSession(): DBusPath {
        sessionHandle?.let { return it }
        val conn = connection()
        val remoteDesktop = remoteDesktop()

        val sessionToken = nextToken()
        val created =
            request(conn, CREATE_TIMEOUT) { requestToken ->
                remoteDesktop.createSession(
                    mapOf(
                        "handle_token" to Variant(requestToken),
                        "session_handle_token" to Variant(sessionToken),
                    ),
                )
            }
        check(created.response.toLong() == 0L) { "CreateSession refused (${created.response})" }
        val session =
            DBusPath(
                created.results["session_handle"]?.value?.toString()
                    ?: PortalHandles.sessionPath(conn.uniqueName, sessionToken),
            )
        try {
            return startSession(conn, remoteDesktop, session)
        } catch (e: Exception) {
            // Whatever went wrong, do not leave a half-set-up session behind.
            runCatching {
                conn.getRemoteObject(PortalHandles.PORTAL_BUS_NAME, session.path, PortalSession::class.java).close()
            }
            throw e
        }
    }

    private suspend fun startSession(
        conn: DBusConnection,
        remoteDesktop: RemoteDesktop,
        session: DBusPath,
    ): DBusPath {
        val selectOptions =
            mutableMapOf<String, Variant<*>>(
                "types" to Variant(UInt32(KEYBOARD.toLong())),
                "persist_mode" to Variant(UInt32(PERSIST_UNTIL_REVOKED.toLong())),
            )
        val currentRestoreToken = loadRestoreToken()
        if (currentRestoreToken.isNotEmpty()) {
            selectOptions["restore_token"] = Variant(currentRestoreToken)
        }
        val selected =
            request(conn, CREATE_TIMEOUT) { requestToken ->
                remoteDesktop.selectDevices(session, selectOptions + ("handle_token" to Variant(requestToken)))
            }
        check(selected.response.toLong() == 0L) { "SelectDevices refused (${selected.response})" }

        // Start is where the desktop asks the user, unless the restore token spares it.
        val started =
            request(conn, START_TIMEOUT) { requestToken ->
                remoteDesktop.start(session, "", mapOf("handle_token" to Variant(requestToken)))
            }
        if (started.response.toLong() != 0L) {
            // Whatever token got us here is spent; a later run asks cleanly.
            if (currentRestoreToken.isNotEmpty()) {
                saveRestoreToken("")
            }
            throw PortalDenied("session not granted (${started.response})")
        }
        val newRestoreToken =
            started.results["restore_token"]
                ?.value
                ?.toString()
                .orEmpty()
        saveRestoreToken(newRestoreToken)

        sessionClosedHandler?.close()
        sessionClosedHandler =
            conn.addSigHandler(PortalSession.Closed::class.java) { closed ->
                if (closed.path == session.path) {
                    logger.info { "RemoteDesktop portal session closed by the compositor" }
                    sessionHandle = null
                }
            }
        sessionHandle = session
        return session
    }

    /**
     * Runs a portal call and waits for the Response signal of the Request it
     * returns. The handler is armed before the call so a fast reply cannot be
     * missed, and matched on the path the portal is documented to use.
     */
    private suspend fun request(
        conn: DBusConnection,
        timeout: Duration,
        call: (requestToken: String) -> DBusPath,
    ): PortalRequest.Response {
        val token = nextToken()
        val expectedPath = PortalHandles.requestPath(conn.uniqueName, token)
        val actualPath = AtomicReference<String?>()
        val reply = CompletableDeferred<PortalRequest.Response>()
        val handler =
            conn.addSigHandler(PortalRequest.Response::class.java) { response ->
                val actual = actualPath.get()
                if (response.path == expectedPath || (actual != null && response.path == actual)) {
                    reply.complete(response)
                }
            }
        var handle: DBusPath? = null
        try {
            handle = call(token)
            actualPath.set(handle.path)
            if (handle.path != expectedPath) {
                logger.warn { "Portal request handle ${handle.path} differs from expected $expectedPath" }
            }
            return withTimeoutOrNull(timeout) { reply.await() }
                ?: run {
                    // Take the dialog down with us, or a late answer would land nowhere.
                    runCatching {
                        conn
                            .getRemoteObject(
                                PortalHandles.PORTAL_BUS_NAME,
                                handle.path,
                                PortalRequest::class.java,
                            ).close()
                    }
                    throw PortalTimeout("no response within $timeout")
                }
        } catch (e: CancellationException) {
            handle?.let { h ->
                runCatching {
                    conn
                        .getRemoteObject(
                            PortalHandles.PORTAL_BUS_NAME,
                            h.path,
                            PortalRequest::class.java,
                        ).close()
                }
            }
            throw e
        } finally {
            handler.close()
        }
    }

    private fun nextToken(): String = "crosspaste_${tokenCounter.incrementAndGet()}"

    companion object {
        private const val KEYBOARD = 1
        private const val PERSIST_UNTIL_REVOKED = 2
        private val PRESSED = UInt32(1)
        private val RELEASED = UInt32(0)

        private val KEY_HOLD = 100.milliseconds
        private val CREATE_TIMEOUT = 10.seconds

        // Start may sit behind a consent dialog the user has to read first.
        private val START_TIMEOUT = 60.seconds
    }
}
