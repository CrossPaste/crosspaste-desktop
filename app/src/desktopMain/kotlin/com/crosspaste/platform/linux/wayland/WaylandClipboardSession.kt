package com.crosspaste.platform.linux.wayland

import com.sun.jna.Callback
import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Single-use Wayland connection providing clipboard-manager reads via
 * `zwlr_data_control_unstable_v1`.
 *
 * Lifecycle:
 *  - [connect] opens a Wayland connection, binds the seat + data-control
 *    manager, and creates the data device. Returns null if the compositor
 *    doesn't expose `zwlr_data_control_manager_v1` (notably GNOME/Mutter) so
 *    the caller can fall back to the X11 path.
 *  - [start] attaches the device listener and launches a daemon dispatch
 *    thread driven by `poll(2)` on the Wayland socket + a self-pipe. Calling
 *    twice is a no-op.
 *  - [close] writes a wake byte to the self-pipe, joins the dispatch thread,
 *    then the dispatch thread itself tears down all native resources — so
 *    `wl_display_disconnect` only runs after the thread has actually exited
 *    its `poll`/`wl_display_*` calls. Calling twice is a no-op.
 *
 * A session is single-use. After [close] it cannot be restarted; create a
 * fresh one via [connect].
 */
class WaylandClipboardSession private constructor(
    private val display: Pointer,
    private val registry: Pointer,
    private val seat: Pointer,
    private val manager: Pointer,
    private val device: Pointer,
    private val wakeReadFd: Int,
    private val wakeWriteFd: Int,
    // GC-pin for the registry listener (Wayland has no listener-detach call;
    // the registry stays alive for the session so we hold these for the same
    // lifetime).
    @Suppress("unused") private val registryRoots: List<Any>,
) {

    @Volatile
    private var onSelectionCallback: ((Map<String, ByteArray>) -> Unit)? = null

    // Per-offer accumulated state. Each `data_offer` event creates an entry,
    // each `offer.offer` event appends a MIME, the eventual `selection` /
    // `primary_selection` event consumes (and destroys) it.
    //
    // Written by main thread during [connect]'s post-construction roundtrip
    // (events fire into the listener attached in init), then handed off to the
    // dispatch thread by `Thread.start()`'s happens-before. No further
    // cross-thread access after that.
    private val offers: MutableMap<Long, OfferState> = mutableMapOf()

    // Selection events queued for [consumePendingSelection]. A single
    // dispatch_pending call can deliver multiple selection events (e.g. when
    // initial roundtrip catches up with several rapid clipboard changes); a
    // queue preserves each as a distinct snapshot instead of dropping all but
    // the last.
    private val pendingOffers: ArrayDeque<Pointer> = ArrayDeque()

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var shouldStop = false

    private var dispatchThread: Thread? = null

    // Shared `offer.offer` listener — all offer proxies use the same function
    // pointer; it discriminates by the source-proxy argument.
    private val offerEventCallback: ZwlrDataControlOfferOffer =
        ZwlrDataControlOfferOffer { _, source, mime ->
            offers[Pointer.nativeValue(source)]?.mimes?.add(mime)
        }
    private val offerListenerMem: Memory = WaylandListeners.packListener(listOf(offerEventCallback))

    // Device listener callbacks — kept reachable for the device proxy's
    // lifetime so the function pointers libwayland holds remain valid.
    private val deviceCallbacks: List<Callback>
    private val deviceListenerMem: Memory

    init {
        // Build + attach the device listener immediately so the initial
        // `wl_display_roundtrip` in [connect] can deliver pre-existing
        // clipboard events (`data_offer` → `offer.offer` → `selection`) into
        // our handlers. Attaching only at [start] time lost the first
        // selection event.
        val dataOfferCb =
            ZwlrDataControlDeviceDataOffer { _, _, offer ->
                offers[Pointer.nativeValue(offer)] = OfferState()
                WaylandClient.INSTANCE.wl_proxy_add_listener(offer, offerListenerMem, null)
            }
        val selectionCb =
            ZwlrDataControlDeviceSelection { _, _, offer ->
                if (offer == null) {
                    // Clipboard cleared: discard any queued (now stale) selections.
                    while (pendingOffers.isNotEmpty()) {
                        destroyOffer(pendingOffers.removeFirst())
                    }
                } else {
                    pendingOffers.addLast(offer)
                }
            }
        val finishedCb =
            ZwlrDataControlDeviceFinished { _, _ ->
                // The compositor has destroyed the manager (e.g. its security
                // policy revoked our access, or wlroots was restarted). The
                // session can't recover on its own — clipboard monitoring will
                // be silent until the user toggles the service off/on (which
                // triggers a fresh WaylandClipboardSession.connect()) or
                // restarts the app.
                logger.warn {
                    "Compositor revoked data-control device; clipboard monitoring stopped until restart/toggle"
                }
                shouldStop = true
            }
        val primaryCb =
            ZwlrDataControlDevicePrimarySelection { _, _, offer ->
                // Primary selection (middle-click on X11) isn't part of the
                // clipboard pipeline; release compositor refs immediately.
                offer?.let { destroyOffer(it) }
            }

        deviceCallbacks = listOf(dataOfferCb, selectionCb, finishedCb, primaryCb)
        deviceListenerMem = WaylandListeners.packListener(deviceCallbacks)
        WaylandClient.INSTANCE.wl_proxy_add_listener(device, deviceListenerMem, null)
    }

    fun onSelection(callback: (Map<String, ByteArray>) -> Unit) {
        onSelectionCallback = callback
    }

    @Synchronized
    fun start() {
        check(!closed.get()) { "WaylandClipboardSession is closed" }
        if (!started.compareAndSet(false, true)) return
        // Device listener was attached in init; any events queued by
        // [connect]'s roundtrip are already buffered in libwayland and will
        // be drained on the dispatch thread's first iteration.
        dispatchThread =
            thread(name = "WaylandClipboardDispatch", isDaemon = true) {
                logger.info { "Wayland dispatch loop started" }
                try {
                    dispatchLoop()
                } finally {
                    cleanupResources()
                    logger.info { "Wayland dispatch loop exited" }
                }
            }
    }

    @Synchronized
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        shouldStop = true
        val thread = dispatchThread
        if (thread == null) {
            // Never started — main thread is the only one touching display, so
            // it's safe to tear down here.
            cleanupResources()
        } else {
            // Wake the dispatch thread out of poll(); it owns teardown.
            wakeDispatchThread()
            thread.join()
        }
    }

    private fun wakeDispatchThread() {
        runCatching {
            val one = Memory(1L)
            one.setByte(0L, 1)
            WaylandLibC.INSTANCE.write(wakeWriteFd, one, 1L)
        }
        // The write end is no longer needed; closing it also closes the door
        // on duplicate wake bytes from any later code path.
        runCatching { WaylandLibC.INSTANCE.close(wakeWriteFd) }
    }

    private fun cleanupResources() {
        // Destroy any offers still in flight. Queue entries point at proxies
        // that are also in `offers`, so the offers loop covers them; clear
        // the queue to drop stale references first.
        pendingOffers.clear()
        for (offerPtr in offers.keys.toList()) {
            runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(Pointer(offerPtr)) }
        }
        offers.clear()

        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(device) }
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(manager) }
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(seat) }
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(registry) }
        runCatching { WaylandLibC.INSTANCE.close(wakeReadFd) }
        // wakeWriteFd is closed by [wakeDispatchThread] before signaling the
        // dispatch thread; if start() was never called it's still open here.
        runCatching { WaylandLibC.INSTANCE.close(wakeWriteFd) }
        runCatching { WaylandClient.INSTANCE.wl_display_disconnect(display) }
    }

    // ─── Dispatch loop ───────────────────────────────────────────────────────

    private fun dispatchLoop() {
        val waylandFd = WaylandClient.INSTANCE.wl_display_get_fd(display)
        // 2 × sizeof(struct pollfd) = 2 × (4 + 2 + 2) = 16 bytes
        val pollFds = Memory(16L)

        while (!shouldStop) {
            // Standard libwayland external-event-loop pattern:
            //   prepare_read → flush → poll → (read_events or cancel_read) → dispatch_pending
            while (WaylandClient.INSTANCE.wl_display_prepare_read(display) != 0) {
                if (WaylandClient.INSTANCE.wl_display_dispatch_pending(display) < 0) return
            }
            WaylandClient.INSTANCE.wl_display_flush(display)

            pollFds.setInt(0L, waylandFd)
            pollFds.setShort(4L, WaylandLibC.POLLIN)
            pollFds.setShort(6L, 0)
            pollFds.setInt(8L, wakeReadFd)
            pollFds.setShort(12L, WaylandLibC.POLLIN)
            pollFds.setShort(14L, 0)

            val n = WaylandLibC.INSTANCE.poll(pollFds, NativeLong(2), -1)
            if (n < 0) {
                // EINTR or error — drop the prepared-read lock and retry.
                WaylandClient.INSTANCE.wl_display_cancel_read(display)
                continue
            }

            val pollIn = WaylandLibC.POLLIN.toInt()
            val waylandReady = (pollFds.getShort(6L).toInt() and pollIn) != 0
            val wakeReady = (pollFds.getShort(14L).toInt() and pollIn) != 0

            if (waylandReady) {
                if (WaylandClient.INSTANCE.wl_display_read_events(display) < 0) return
            } else {
                WaylandClient.INSTANCE.wl_display_cancel_read(display)
            }

            if (WaylandClient.INSTANCE.wl_display_dispatch_pending(display) < 0) return

            consumePendingSelection()

            if (wakeReady) return
        }
    }

    // ─── Selection / offer handling ──────────────────────────────────────────

    private fun consumePendingSelection() {
        while (pendingOffers.isNotEmpty()) {
            val offer = pendingOffers.removeFirst()
            if (shouldStop) {
                destroyOffer(offer)
                continue
            }
            val state = offers[Pointer.nativeValue(offer)]
            val mimes = state?.mimes?.toList().orEmpty()
            val payload = readOffer(offer, mimes)
            destroyOffer(offer)
            onSelectionCallback?.invoke(payload)
        }
    }

    private fun readOffer(
        offer: Pointer,
        mimes: List<String>,
    ): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for (mime in mimes) {
            if (shouldStop) break
            runCatching { readMime(offer, mime) }
                .onSuccess { out[mime] = it }
                .onFailure { e -> logger.warn(e) { "Failed to read mime $mime" } }
        }
        return out
    }

    private fun readMime(
        offer: Pointer,
        mime: String,
    ): ByteArray {
        val fds = IntArray(2)
        val rc = WaylandLibC.INSTANCE.pipe(fds)
        if (rc != 0) error("pipe(2) failed: rc=$rc")
        val readFd = fds[0]
        val writeFd = fds[1]
        try {
            // offer.receive(mime, fd) — opcode 0, signature "sh"
            WaylandClient.marshalFlags(
                proxy = offer,
                opcode = OFFER_RECEIVE_OPCODE,
                interfaceStruct = null,
                version = WaylandClient.INSTANCE.wl_proxy_get_version(offer),
                flags = 0,
                mime,
                writeFd,
            )
            WaylandClient.INSTANCE.wl_display_flush(display)
        } finally {
            // The compositor inherits its own copy; close ours so the source
            // sees EOF after writing.
            WaylandLibC.INSTANCE.close(writeFd)
        }

        return try {
            drainFd(readFd)
        } finally {
            WaylandLibC.INSTANCE.close(readFd)
        }
    }

    /**
     * Drain the offer pipe into a byte array, bounded by a no-progress timeout
     * so a misbehaving source app (opens fd but never writes/closes) can't
     * pin the dispatch thread forever. Each [READ_STALL_TIMEOUT_MS] window
     * resets on a successful read, so legitimately large payloads (images)
     * still get through.
     *
     * Known limitation: the payload buffers entirely into a [ByteArray] and
     * eventually a `Map<String, ByteArray>` per mime; a 50 MB image briefly
     * doubles in heap residency before downstream plugins persist it. The
     * X11 path has the same behavior — the abstraction is byte-array-shaped
     * end-to-end. Streaming into the persistence layer is a separate, larger
     * refactor.
     */
    private fun drainFd(fd: Int): ByteArray {
        val buf = Memory(READ_CHUNK_BYTES)
        val pollOne = Memory(8L) // sizeof(struct pollfd) = 4 + 2 + 2
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            if (shouldStop) break
            pollOne.setInt(0L, fd)
            pollOne.setShort(4L, WaylandLibC.POLLIN)
            pollOne.setShort(6L, 0)
            val pollRc = WaylandLibC.INSTANCE.poll(pollOne, NativeLong(1), READ_STALL_TIMEOUT_MS)
            if (pollRc < 0) continue // EINTR — retry
            if (pollRc == 0) {
                logger.warn { "Clipboard read stalled (${out.size()} bytes so far); abandoning fd" }
                break
            }
            val n = WaylandLibC.INSTANCE.read(fd, buf, READ_CHUNK_BYTES)
            if (n <= 0L) break // EOF or error
            out.write(buf.getByteArray(0L, n.toInt()))
        }
        return out.toByteArray()
    }

    private fun destroyOffer(offer: Pointer) {
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(offer) }
        offers.remove(Pointer.nativeValue(offer))
    }

    private class OfferState(
        val mimes: MutableList<String> = mutableListOf(),
    )

    companion object {

        private val logger = KotlinLogging.logger {}

        private const val READ_CHUNK_BYTES = 8192L

        /**
         * Max time (ms) we'll wait for new bytes on an offer pipe without any
         * progress before giving up. Resets after every successful read, so
         * large payloads stream through; only true stalls trigger the bail.
         */
        private const val READ_STALL_TIMEOUT_MS = 2000

        // Opcodes from the protocol XML (zwlr-data-control-unstable-v1).
        private const val DISPLAY_GET_REGISTRY_OPCODE = 1
        private const val REGISTRY_BIND_OPCODE = 0
        private const val MANAGER_GET_DATA_DEVICE_OPCODE = 1
        private const val OFFER_RECEIVE_OPCODE = 0

        private const val MAX_SEAT_VERSION = 7
        private const val MAX_MANAGER_VERSION = 2

        /**
         * Cheap probe: try to connect and confirm `zwlr_data_control_manager_v1`
         * exists. Used by the factory to decide between the Wayland and X11
         * services. The probe session is closed before returning.
         */
        fun isAvailable(): Boolean {
            val session = connect() ?: return false
            session.close()
            return true
        }

        /**
         * Establish a Wayland connection, bind the seat + data-control manager,
         * and create the data device. Returns null when the connection or any
         * required global is unavailable (typically because the compositor
         * doesn't implement wlr-data-control).
         */
        fun connect(): WaylandClipboardSession? {
            val display =
                WaylandClient.INSTANCE.wl_display_connect(null) ?: run {
                    logger.info { "wl_display_connect returned null" }
                    return null
                }

            val registry =
                createRegistry(display) ?: run {
                    WaylandClient.INSTANCE.wl_display_disconnect(display)
                    return null
                }

            val globalsResult = enumerateGlobals(display, registry)
            val globals = globalsResult.globals
            val seatGlobal = globals.firstOrNull { it.interfaceName == "wl_seat" }
            val managerGlobal = globals.firstOrNull { it.interfaceName == "zwlr_data_control_manager_v1" }

            if (seatGlobal == null || managerGlobal == null) {
                logger.info {
                    "Required Wayland globals missing — seat=${seatGlobal != null}, " +
                        "wlrDataControl=${managerGlobal != null}"
                }
                WaylandClient.INSTANCE.wl_proxy_destroy(registry)
                WaylandClient.INSTANCE.wl_display_disconnect(display)
                return null
            }

            val seatVersion = minOf(seatGlobal.version, MAX_SEAT_VERSION)
            val managerVersion = minOf(managerGlobal.version, MAX_MANAGER_VERSION)
            val seat =
                bindGlobalWithVersion(
                    registry,
                    seatGlobal.name,
                    WaylandInterfaces.wlSeatInterface,
                    "wl_seat",
                    seatVersion,
                ) ?: return abort(display, registry, "seat bind returned null")
            val manager =
                bindGlobalWithVersion(
                    registry,
                    managerGlobal.name,
                    WaylandInterfaces.zwlrDataControlManagerV1,
                    "zwlr_data_control_manager_v1",
                    managerVersion,
                ) ?: return abort(display, registry, "manager bind returned null")

            val device =
                WaylandClient.marshalFlags(
                    proxy = manager,
                    opcode = MANAGER_GET_DATA_DEVICE_OPCODE,
                    interfaceStruct = WaylandInterfaces.zwlrDataControlDeviceV1,
                    version = WaylandClient.INSTANCE.wl_proxy_get_version(manager),
                    flags = 0,
                    null,
                    seat,
                ) ?: return abort(display, registry, "get_data_device returned null")

            // Force a round-trip so existing data_offer/selection events arrive
            // before the caller hands control to start().
            WaylandClient.INSTANCE.wl_display_roundtrip(display)

            val wakePipe = IntArray(2)
            val pipeRc = WaylandLibC.INSTANCE.pipe(wakePipe)
            if (pipeRc != 0) {
                runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(device) }
                return abort(display, registry, "self-pipe creation failed rc=$pipeRc")
            }

            return WaylandClipboardSession(
                display = display,
                registry = registry,
                seat = seat,
                manager = manager,
                device = device,
                wakeReadFd = wakePipe[0],
                wakeWriteFd = wakePipe[1],
                registryRoots = globalsResult.roots,
            )
        }

        private fun abort(
            display: Pointer,
            registry: Pointer,
            reason: String,
        ): WaylandClipboardSession? {
            logger.warn { "Wayland clipboard init failed: $reason" }
            runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(registry) }
            runCatching { WaylandClient.INSTANCE.wl_display_disconnect(display) }
            return null
        }

        private fun createRegistry(display: Pointer): Pointer? =
            WaylandClient.marshalFlags(
                proxy = display,
                opcode = DISPLAY_GET_REGISTRY_OPCODE,
                interfaceStruct = WaylandInterfaces.wlRegistryInterface,
                version = WaylandClient.INSTANCE.wl_proxy_get_version(display),
                flags = 0,
                null,
            )

        private data class Global(
            val name: Int,
            val interfaceName: String,
            val version: Int,
        )

        private data class GlobalsResult(
            val globals: List<Global>,
            /** Callbacks + Memory that must outlive this call (registry stays alive for the session). */
            val roots: List<Any>,
        )

        private fun enumerateGlobals(
            display: Pointer,
            registry: Pointer,
        ): GlobalsResult {
            val out = mutableListOf<Global>()
            val globalCb =
                WlRegistryGlobal { _, _, name, iface, version ->
                    out += Global(name, iface, version)
                }
            val removeCb = WlRegistryGlobalRemove { _, _, _ -> }
            val listenerMem = WaylandListeners.packListener(listOf(globalCb, removeCb))

            WaylandClient.INSTANCE.wl_proxy_add_listener(registry, listenerMem, null)
            WaylandClient.INSTANCE.wl_display_roundtrip(display)
            return GlobalsResult(out.toList(), listOf(globalCb, removeCb, listenerMem))
        }

        private fun bindGlobalWithVersion(
            registry: Pointer,
            globalName: Int,
            interfaceStruct: Pointer,
            interfaceName: String,
            version: Int,
        ): Pointer? =
            WaylandClient.marshalFlags(
                proxy = registry,
                opcode = REGISTRY_BIND_OPCODE,
                interfaceStruct = interfaceStruct,
                version = version,
                flags = 0,
                globalName,
                interfaceName,
                version,
                null,
            )
    }
}
