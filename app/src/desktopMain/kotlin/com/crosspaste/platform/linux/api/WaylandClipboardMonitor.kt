package com.crosspaste.platform.linux.api

import com.crosspaste.platform.linux.api.WaylandClientLib.Companion.arguments
import com.crosspaste.platform.linux.api.WaylandDataControlProtocol.Companion.BIND_VERSION
import com.crosspaste.platform.linux.api.WaylandDataControlProtocol.Companion.DEVICE_DESTROY
import com.crosspaste.platform.linux.api.WaylandDataControlProtocol.Companion.MANAGER_DESTROY
import com.crosspaste.platform.linux.api.WaylandDataControlProtocol.Companion.MANAGER_GET_DATA_DEVICE
import com.crosspaste.platform.linux.api.WaylandDataControlProtocol.Companion.OFFER_DESTROY
import com.crosspaste.platform.linux.api.WaylandDataControlProtocol.Companion.OFFER_RECEIVE
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One clipboard selection as the compositor announced it: the MIME types the
 * owning client offers, in the order offered. Its data is read through
 * [WaylandClipboardMonitor.read], which refuses once a newer selection has
 * replaced this one.
 */
class WaylandSelection internal constructor(
    internal val serial: Long,
    val mimeTypes: List<String>,
    /** True for the selection the compositor reported when the device was created (pre-launch content). */
    val initial: Boolean,
) {
    override fun toString(): String = "WaylandSelection(serial=$serial, initial=$initial, mimeTypes=$mimeTypes)"
}

/**
 * Watches the Wayland clipboard natively through the data-control protocol
 * (`ext-data-control-v1`, or wlroots' `wlr-data-control-unstable-v1`), the
 * same path `wl-paste --watch` uses.
 *
 * Why this exists: in a Wayland session the X11 `CLIPBOARD` selection that
 * AWT and XFixes see is only a mirror the compositor maintains for XWayland
 * clients, and that mirror is partial — KWin, for one, never bridges images
 * copied by native Wayland apps, so XFixes never fires (#5167). Data-control
 * is the compositor's own clipboard, so every copy from every client shows up.
 *
 * Lifecycle: [start] connects, binds the manager and a seat, creates the data
 * device and processes the initial selection synchronously; it returns false
 * (with nothing left running) wherever the protocol is unavailable — no
 * Wayland socket, no `libwayland-client`, or a compositor without data-control
 * (GNOME's Mutter) — so the caller can fall back to the X11 path. Afterwards a
 * dedicated thread dispatches events and [onSelection] is invoked on it for
 * each new selection; the callback must return quickly and read the data
 * elsewhere via [read]. [stop] wakes the thread and tears everything down.
 *
 * Threading: [read] marshals its request from the caller's thread while the
 * event thread sleeps in `poll`, which libwayland supports (it serialises on
 * the display mutex). [lock] guards our own proxy bookkeeping so a selection
 * that arrives mid-read cannot destroy the offer being read.
 */
class WaylandClipboardMonitor(
    /**
     * Socket to connect to: null for the session's (`WAYLAND_DISPLAY`), or an
     * absolute socket path, which tests use to reach a private compositor.
     */
    private val displayName: String? = null,
    private val onClose: (() -> Unit)? = null,
    private val onSelection: (WaylandSelection) -> Unit,
) {
    private val logger = KotlinLogging.logger {}

    private val lib: WaylandClientLib by lazy { WaylandClientLib.INSTANCE }
    private val libc: WaylandLibC by lazy { WaylandLibC.INSTANCE }

    private val lock = ReentrantLock()

    private var display: Pointer? = null
    private var registry: Pointer? = null
    private var manager: Pointer? = null
    private var seat: Pointer? = null
    private var device: Pointer? = null
    private var protocol: WaylandDataControlProtocol? = null

    /** Offers announced by `data_offer`, with the MIME types collected so far, keyed by proxy address. */
    private val pendingOffers = LinkedHashMap<Long, MutableList<String>>()

    /** The offer backing the current selection, or null when the clipboard is empty. */
    private var currentOffer: Pointer? = null
    private var currentSerial = 0L
    private var initialSelectionSeen = false

    @Volatile
    private var running = false

    @Volatile
    private var closed = false

    private val closeNotified = AtomicBoolean(false)

    private var wakeReadFd = -1
    private var wakeWriteFd = -1
    private var thread: Thread? = null

    // Listener vtables and their callbacks: referenced for the monitor's
    // lifetime so JNA never collects a callback the compositor may still invoke.
    private val keepAlive = mutableListOf<Any>()

    private var registryGlobals = mutableMapOf<String, Pair<Int, Int>>()

    /**
     * Connects and starts watching. Returns false when data-control is not
     * available here; the monitor is then fully torn down.
     */
    fun start(): Boolean {
        val display =
            runCatching { lib.wl_display_connect(displayName) }.getOrElse { e ->
                logger.info { "libwayland-client unavailable, no native Wayland clipboard: ${e.message}" }
                return false
            } ?: run {
                logger.info { "No Wayland display to connect to, no native Wayland clipboard" }
                return false
            }
        this.display = display
        val ready =
            runCatching { bind(display) }.getOrElse { e ->
                logger.warn(e) { "Failed to set up the Wayland data-control clipboard" }
                false
            }
        if (!ready) {
            lock.withLock { teardown() }
            return false
        }
        running = true
        thread =
            Thread(::eventLoop, "WaylandClipboardMonitor").apply {
                isDaemon = true
                start()
            }
        return true
    }

    /** Stops the event thread and releases every proxy and the connection. Idempotent. */
    fun stop() {
        if (closed) return
        running = false
        if (wakeWriteFd >= 0) {
            libc.write(wakeWriteFd, byteArrayOf(1), 1)
        }
        val thread = this.thread
        if (thread != null && thread !== Thread.currentThread()) {
            thread.join(STOP_JOIN_TIMEOUT.inWholeMilliseconds)
        }
        lock.withLock { teardown() }
        notifyClose()
    }

    private fun notifyClose() {
        if (closeNotified.compareAndSet(false, true)) {
            runCatching { onClose?.invoke() }.onFailure { e ->
                logger.error(e) { "Wayland clipboard monitor close callback failed" }
            }
        }
    }

    /** True while [selection] is still what the clipboard holds (no later copy, monitor still running). */
    fun isCurrent(selection: WaylandSelection): Boolean = lock.withLock { !closed && selection.serial == currentSerial }

    /**
     * Reads [mimeType] of [selection] into memory. Returns null when the
     * selection is no longer current (a newer copy replaced it — read that one
     * instead), when the owner does not answer within [timeout], when the
     * payload exceeds [MAX_BYTES], or on any I/O failure.
     */
    fun read(
        selection: WaylandSelection,
        mimeType: String,
        timeout: Duration = READ_TIMEOUT,
    ): ByteArray? {
        val fds = IntArray(2)
        if (libc.pipe(fds) != 0) {
            logger.warn { "read $mimeType: pipe() failed, errno=${Native.getLastError()}" }
            return null
        }
        val readFd = fds[0]
        val writeFd = fds[1]
        try {
            val sent =
                try {
                    lock.withLock {
                        val offer = currentOffer
                        if (closed || offer == null || selection.serial != currentSerial) {
                            logger.debug { "read $mimeType: selection ${selection.serial} is no longer current" }
                            false
                        } else {
                            // libwayland dup()s the fd while marshalling, so our end can go right away.
                            val args = arguments(mimeType, writeFd)
                            lib.wl_proxy_marshal_array(offer, OFFER_RECEIVE, args.pointer)
                            flush()
                            true
                        }
                    }
                } finally {
                    libc.close(writeFd)
                }
            return if (sent) readToEnd(readFd, timeout, mimeType, selection) else null
        } finally {
            libc.close(readFd)
        }
    }

    // ---- setup ----

    private fun bind(display: Pointer): Boolean {
        val registry =
            lib.wl_proxy_marshal_array_constructor(
                display,
                WaylandClientLib.WL_DISPLAY_GET_REGISTRY,
                arguments(null).pointer,
                WaylandClientLib.exportedInterface("wl_registry_interface"),
            ) ?: return false
        this.registry = registry
        addListener(registry, registryListenerTable)
        if (lib.wl_display_roundtrip(display) < 0) return false

        val protocol =
            WaylandDataControlProtocol.ALL.firstOrNull { it.managerName in registryGlobals }
                ?: run {
                    logger.info {
                        "Compositor offers no data-control protocol " +
                            "(globals: ${registryGlobals.keys.sorted()}), falling back to X11 clipboard"
                    }
                    return false
                }
        val seatGlobal =
            registryGlobals["wl_seat"] ?: run {
                logger.info { "Compositor offers no wl_seat, falling back to X11 clipboard" }
                return false
            }
        this.protocol = protocol

        manager =
            bindGlobal(
                registry,
                protocol.managerName,
                registryGlobals.getValue(protocol.managerName).first,
                protocol.manager.pointer,
            )
                ?: return false
        seat =
            bindGlobal(registry, "wl_seat", seatGlobal.first, WaylandClientLib.exportedInterface("wl_seat_interface"))
                ?: return false

        val device =
            lib.wl_proxy_marshal_array_constructor_versioned(
                manager!!,
                MANAGER_GET_DATA_DEVICE,
                arguments(null, seat).pointer,
                protocol.device.pointer,
                BIND_VERSION,
            ) ?: return false
        this.device = device
        addListener(device, deviceListenerTable)

        val fds = IntArray(2)
        if (libc.pipe(fds) != 0) return false
        wakeReadFd = fds[0]
        wakeWriteFd = fds[1]

        // The compositor answers get_data_device with the current selection;
        // consume it here so the caller knows the pre-launch content was reported.
        if (lib.wl_display_roundtrip(display) < 0) return false
        logger.info { "Watching the Wayland clipboard via ${protocol.managerName}" }
        return true
    }

    private fun bindGlobal(
        registry: Pointer,
        interfaceName: String,
        name: Int,
        interfaceTable: Pointer,
    ): Pointer? =
        lib.wl_proxy_marshal_array_constructor_versioned(
            registry,
            WaylandClientLib.WL_REGISTRY_BIND,
            arguments(name, interfaceName, BIND_VERSION, null).pointer,
            interfaceTable,
            BIND_VERSION,
        )

    private fun addListener(
        proxy: Pointer,
        table: Pointer,
    ) {
        lib.wl_proxy_add_listener(proxy, table, null)
    }

    /** A `struct *_listener` vtable: one function pointer per event, in opcode order. */
    private fun listenerTable(callbacks: List<Callback>): Pointer {
        val table = Memory(callbacks.size * POINTER_SIZE)
        callbacks.forEachIndexed { index, callback ->
            table.setPointer(index * POINTER_SIZE, CallbackReference.getFunctionPointer(callback))
        }
        keepAlive.add(table)
        keepAlive.addAll(callbacks)
        return table
    }

    // ---- event loop ----

    private fun eventLoop() {
        val display = display ?: return
        val displayFd = lib.wl_display_get_fd(display)
        val pollFds = Memory(2 * WaylandLibC.POLLFD_SIZE)
        try {
            while (running) {
                while (lib.wl_display_prepare_read(display) != 0) {
                    if (lib.wl_display_dispatch_pending(display) < 0) {
                        logger.warn { "Wayland dispatch failed, error=${lib.wl_display_get_error(display)}" }
                        return
                    }
                }
                flush()
                WaylandLibC.setPollFd(pollFds, 0, displayFd, WaylandLibC.POLLIN)
                WaylandLibC.setPollFd(pollFds, 1, wakeReadFd, WaylandLibC.POLLIN)
                val ready = libc.poll(pollFds, 2, -1)
                if (ready < 0) {
                    lib.wl_display_cancel_read(display)
                    if (Native.getLastError() == WaylandLibC.EINTR) continue
                    logger.warn { "poll on the Wayland display failed, errno=${Native.getLastError()}" }
                    return
                }
                if (!running || WaylandLibC.revents(pollFds, 1).toInt() != 0) {
                    lib.wl_display_cancel_read(display)
                    return
                }
                val displayEvents = WaylandLibC.revents(pollFds, 0).toInt()
                if (displayEvents and WaylandLibC.POLLIN.toInt() != 0) {
                    if (lib.wl_display_read_events(display) < 0) {
                        logger.warn { "Wayland connection lost, error=${lib.wl_display_get_error(display)}" }
                        return
                    }
                } else {
                    lib.wl_display_cancel_read(display)
                    if (displayEvents and (WaylandLibC.POLLERR.toInt() or WaylandLibC.POLLHUP.toInt()) != 0) {
                        logger.warn { "Wayland display hung up" }
                        return
                    }
                }
                if (lib.wl_display_dispatch_pending(display) < 0) {
                    logger.warn { "Wayland dispatch failed, error=${lib.wl_display_get_error(display)}" }
                    return
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "Wayland clipboard event loop crashed" }
        } finally {
            running = false
            notifyClose()
        }
    }

    private fun flush() {
        val display = display ?: return
        // EAGAIN only means the socket is momentarily full; the next loop pass flushes again.
        if (lib.wl_display_flush(display) < 0 && Native.getLastError() != WaylandLibC.EAGAIN) {
            logger.debug { "wl_display_flush failed, errno=${Native.getLastError()}" }
        }
    }

    private fun readToEnd(
        fd: Int,
        timeout: Duration,
        mimeType: String,
        selection: WaylandSelection,
    ): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_CHUNK)
        val pollFd = Memory(WaylandLibC.POLLFD_SIZE)
        // Monotonic clock: a wall-clock jump must not stretch or cut the wait.
        val deadlineNanos = System.nanoTime() + timeout.inWholeNanoseconds
        while (true) {
            if (!isCurrent(selection)) {
                logger.debug { "read $mimeType: selection ${selection.serial} replaced while reading, aborting" }
                return null
            }
            val remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) {
                logger.warn { "read $mimeType: owner did not finish within $timeout, dropping ${output.size()} bytes" }
                return null
            }
            WaylandLibC.setPollFd(pollFd, 0, fd, WaylandLibC.POLLIN)
            val pollTimeout = remainingMs.coerceAtMost(POLL_SLICE_MS).toInt()
            val ready = libc.poll(pollFd, 1, pollTimeout)
            if (ready < 0) {
                if (Native.getLastError() == WaylandLibC.EINTR) continue
                logger.warn { "read $mimeType: poll failed, errno=${Native.getLastError()}" }
                return null
            }
            if (ready == 0) continue
            val count = libc.read(fd, buffer, buffer.size.toLong())
            when {
                count < 0 -> {
                    val errno = Native.getLastError()
                    if (errno == WaylandLibC.EINTR || errno == WaylandLibC.EAGAIN) continue
                    logger.warn { "read $mimeType: read failed, errno=$errno" }
                    return null
                }
                count == 0L -> return output.toByteArray()
                else -> {
                    if (output.size() + count > MAX_BYTES) {
                        logger.warn { "read $mimeType: payload exceeds $MAX_BYTES bytes, dropping" }
                        return null
                    }
                    output.write(buffer, 0, count.toInt())
                }
            }
        }
    }

    // ---- listeners (invoked on whichever thread dispatches: the event thread, or start() during roundtrips) ----

    private val registryListenerTable: Pointer by lazy {
        listenerTable(
            listOf(
                WlRegistryGlobalCallback { _, _, name, interfaceName, version ->
                    if (interfaceName != null) {
                        registryGlobals.putIfAbsent(interfaceName, name to version)
                    }
                },
                WlRegistryGlobalRemoveCallback { _, _, _ -> },
            ),
        )
    }

    private val offerListenerTable: Pointer by lazy {
        listenerTable(
            listOf(
                WlMimeTypeCallback { _, offer, mimeType ->
                    if (offer != null && mimeType != null) {
                        lock.withLock { pendingOffers[Pointer.nativeValue(offer)]?.add(mimeType) }
                    }
                },
            ),
        )
    }

    private val deviceListenerTable: Pointer by lazy {
        listenerTable(
            listOf(
                // data_offer: a new offer; its MIME types follow as offer events.
                WlOfferCallback { _, _, offer ->
                    if (offer != null) {
                        lock.withLock { pendingOffers[Pointer.nativeValue(offer)] = mutableListOf() }
                        addListener(offer, offerListenerTable)
                    }
                },
                // selection: the clipboard now holds this offer (or nothing).
                WlOfferCallback { _, _, offer -> handleSelection(offer) },
                // finished: the device is defunct (seat gone); nothing more will arrive.
                WlNoArgCallback { _, _ ->
                    logger.warn { "Wayland data-control device finished; clipboard events stop" }
                    running = false
                    if (wakeWriteFd >= 0) {
                        libc.write(wakeWriteFd, byteArrayOf(1), 1)
                    }
                },
                // primary_selection: not tracked, release the offer.
                WlOfferCallback { _, _, offer ->
                    if (offer != null) {
                        lock.withLock {
                            pendingOffers.remove(Pointer.nativeValue(offer))
                            if (offer != currentOffer) destroyOffer(offer)
                        }
                    }
                },
            ),
        )
    }

    private fun handleSelection(offer: Pointer?) {
        val selection =
            lock.withLock {
                if (closed) return
                currentOffer?.let { previous -> if (previous != offer) destroyOffer(previous) }
                currentOffer = offer
                currentSerial++
                val initial = !initialSelectionSeen
                initialSelectionSeen = true
                val mimeTypes = offer?.let { pendingOffers.remove(Pointer.nativeValue(it)) } ?: emptyList()
                WaylandSelection(currentSerial, mimeTypes.toList(), initial)
            }
        logger.debug { "Wayland selection changed: $selection" }
        if (offer == null) return
        runCatching { onSelection(selection) }.onFailure { e ->
            logger.error(e) { "Wayland selection handler failed" }
        }
    }

    // ---- teardown (under lock) ----

    private fun destroyOffer(offer: Pointer) {
        lib.wl_proxy_marshal_array(offer, OFFER_DESTROY, null)
        lib.wl_proxy_destroy(offer)
    }

    private fun teardown() {
        if (closed) return
        closed = true
        runCatching {
            currentOffer?.let(::destroyOffer)
            currentOffer = null
            pendingOffers.keys.toList().forEach { address -> destroyOffer(Pointer(address)) }
            pendingOffers.clear()
            device?.let {
                lib.wl_proxy_marshal_array(it, DEVICE_DESTROY, null)
                lib.wl_proxy_destroy(it)
            }
            device = null
            manager?.let {
                lib.wl_proxy_marshal_array(it, MANAGER_DESTROY, null)
                lib.wl_proxy_destroy(it)
            }
            manager = null
            seat?.let(lib::wl_proxy_destroy)
            seat = null
            registry?.let(lib::wl_proxy_destroy)
            registry = null
            display?.let {
                flush()
                lib.wl_display_disconnect(it)
            }
            display = null
        }.onFailure { e ->
            logger.warn(e) { "Failed to tear down the Wayland clipboard monitor cleanly" }
        }
        if (wakeReadFd >= 0) libc.close(wakeReadFd)
        if (wakeWriteFd >= 0) libc.close(wakeWriteFd)
        wakeReadFd = -1
        wakeWriteFd = -1
    }

    companion object {
        private const val POINTER_SIZE = 8L
        private const val READ_CHUNK = 64 * 1024

        /** Upper bound for one MIME payload; anything larger is not plausible clipboard content. */
        const val MAX_BYTES = 256L * 1024 * 1024

        /** How long the owning client gets to serve one MIME type end to end. */
        val READ_TIMEOUT: Duration = 10.seconds

        private val STOP_JOIN_TIMEOUT: Duration = 2.seconds

        private const val POLL_SLICE_MS = 250L
    }
}

// JNA callback signatures for the listener vtables. Kept top-level and
// non-private so JNA can resolve and invoke the single abstract method.

internal fun interface WlRegistryGlobalCallback : Callback {
    fun invoke(
        data: Pointer?,
        registry: Pointer?,
        name: Int,
        interfaceName: String?,
        version: Int,
    )
}

internal fun interface WlRegistryGlobalRemoveCallback : Callback {
    fun invoke(
        data: Pointer?,
        registry: Pointer?,
        name: Int,
    )
}

internal fun interface WlOfferCallback : Callback {
    fun invoke(
        data: Pointer?,
        proxy: Pointer?,
        offer: Pointer?,
    )
}

internal fun interface WlNoArgCallback : Callback {
    fun invoke(
        data: Pointer?,
        proxy: Pointer?,
    )
}

internal fun interface WlMimeTypeCallback : Callback {
    fun invoke(
        data: Pointer?,
        proxy: Pointer?,
        mimeType: String?,
    )
}
