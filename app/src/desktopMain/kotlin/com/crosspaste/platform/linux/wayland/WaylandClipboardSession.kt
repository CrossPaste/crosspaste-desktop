package com.crosspaste.platform.linux.wayland

import com.sun.jna.Callback
import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.concurrent.thread

/**
 * Live connection to the Wayland compositor providing clipboard-manager reads
 * via the `zwlr_data_control_unstable_v1` protocol.
 *
 * Lifecycle:
 *  - [connect] returns null if the compositor doesn't expose
 *    `zwlr_data_control_manager_v1` (e.g. GNOME/Mutter) — caller should fall
 *    back to the X11 path in that case.
 *  - [start] launches a daemon thread that drives `wl_display_dispatch`; the
 *    thread invokes [onSelection] from the dispatch context whenever the
 *    compositor announces a new clipboard owner.
 *  - [close] cancels the loop and disconnects.
 */
class WaylandClipboardSession private constructor(
    private val display: Pointer,
    private val registry: Pointer,
    private val seat: Pointer,
    private val manager: Pointer,
    private val device: Pointer,
) {

    @Volatile
    private var onSelection: ((Map<String, ByteArray>) -> Unit)? = null

    // Per-offer accumulated MIME list. Built up as `offer.offer` events arrive,
    // consumed when `device.selection` fires for the matching offer pointer.
    private val offerMimes: MutableMap<Long, MutableList<String>> = mutableMapOf()

    // The offer announced as the current selection. Captured in the callback,
    // read on the dispatch thread before next iteration.
    @Volatile
    private var pendingOffer: Pointer? = null

    @Volatile
    private var running = false

    private var dispatchThread: Thread? = null

    // Listener Memory + Callback objects must remain GC-rooted while attached.
    private val rootedCallbacks: MutableList<Callback> = mutableListOf()
    private val rootedMemory: MutableList<Memory> = mutableListOf()

    fun onSelection(callback: (Map<String, ByteArray>) -> Unit) {
        onSelection = callback
    }

    fun start() {
        if (running) return
        attachDeviceListener()
        running = true
        dispatchThread =
            thread(name = "WaylandClipboardDispatch", isDaemon = true) {
                logger.info { "Wayland dispatch loop started" }
                while (running) {
                    val rc = WaylandClient.INSTANCE.wl_display_dispatch(display)
                    if (rc < 0) {
                        logger.warn { "wl_display_dispatch returned $rc — exiting loop" }
                        break
                    }
                    consumePendingSelection()
                }
                logger.info { "Wayland dispatch loop exited" }
            }
    }

    fun close() {
        running = false
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(device) }
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(manager) }
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(seat) }
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(registry) }
        runCatching { WaylandClient.INSTANCE.wl_display_disconnect(display) }
        dispatchThread = null
    }

    private fun consumePendingSelection() {
        val offer = pendingOffer ?: return
        pendingOffer = null
        val mimes = offerMimes.remove(Pointer.nativeValue(offer))?.toList().orEmpty()
        val payload = readOffer(offer, mimes)
        runCatching { WaylandClient.INSTANCE.wl_proxy_destroy(offer) }
        onSelection?.invoke(payload)
    }

    private fun readOffer(
        offer: Pointer,
        mimes: List<String>,
    ): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for (mime in mimes) {
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
            // The compositor inherits its own copy; we must close ours so the
            // source sees EOF after writing.
            WaylandLibC.INSTANCE.close(writeFd)
        }

        return try {
            drainFd(readFd)
        } finally {
            WaylandLibC.INSTANCE.close(readFd)
        }
    }

    private fun drainFd(fd: Int): ByteArray {
        val buf = Memory(READ_CHUNK_BYTES)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val n = WaylandLibC.INSTANCE.read(fd, buf, READ_CHUNK_BYTES)
            if (n <= 0L) break
            out.write(buf.getByteArray(0L, n.toInt()))
        }
        return out.toByteArray()
    }

    private fun attachDeviceListener() {
        // `data_offer` fires first; we then receive a series of `offer.offer`
        // events on the new offer to enumerate MIME types, and finally
        // `selection` which adopts that offer (or null to clear).
        val dataOfferCb =
            ZwlrDataControlDeviceDataOffer { _, _, offer ->
                offerMimes[Pointer.nativeValue(offer)] = mutableListOf()
                attachOfferListener(offer)
            }
        val selectionCb =
            ZwlrDataControlDeviceSelection { _, _, offer ->
                if (offer == null) return@ZwlrDataControlDeviceSelection
                pendingOffer = offer
            }
        val finishedCb = ZwlrDataControlDeviceFinished { _, _ -> running = false }
        val primaryCb =
            ZwlrDataControlDevicePrimarySelection { _, _, offer ->
                // Primary selection (middle-click on X11) isn't part of the
                // clipboard pipeline today; destroy to release compositor refs.
                offer?.let { WaylandClient.INSTANCE.wl_proxy_destroy(it) }
            }

        val callbacks = listOf<Callback>(dataOfferCb, selectionCb, finishedCb, primaryCb)
        rootedCallbacks += callbacks
        val mem = WaylandListeners.packListener(callbacks)
        rootedMemory += mem
        WaylandClient.INSTANCE.wl_proxy_add_listener(device, mem, null)
    }

    private fun attachOfferListener(offer: Pointer) {
        val offerCb =
            ZwlrDataControlOfferOffer { _, source, mime ->
                offerMimes[Pointer.nativeValue(source)]?.add(mime)
            }
        rootedCallbacks += offerCb
        val mem = WaylandListeners.packListener(listOf(offerCb))
        rootedMemory += mem
        WaylandClient.INSTANCE.wl_proxy_add_listener(offer, mem, null)
    }

    companion object {

        private val logger = KotlinLogging.logger {}

        private const val READ_CHUNK_BYTES = 8192L

        // Opcodes from the protocol XML (zwlr-data-control-unstable-v1).
        private const val DISPLAY_GET_REGISTRY_OPCODE = 1
        private const val REGISTRY_BIND_OPCODE = 0
        private const val MANAGER_GET_DATA_DEVICE_OPCODE = 1
        private const val OFFER_RECEIVE_OPCODE = 0

        /**
         * Establish a Wayland connection and bind the seat + data-control
         * manager. Returns null when the connection or required globals are
         * unavailable — typically because the compositor doesn't implement
         * wlr-data-control.
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

            val globals = enumerateGlobals(display, registry)
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
                    // new_id placeholder
                    null,
                    seat,
                ) ?: return abort(display, registry, "get_data_device returned null")

            // Force a round-trip so the device's data_offer/selection events
            // for the existing clipboard arrive before we hand control over.
            WaylandClient.INSTANCE.wl_display_roundtrip(display)

            return WaylandClipboardSession(display, registry, seat, manager, device)
        }

        private const val MAX_SEAT_VERSION = 7
        private const val MAX_MANAGER_VERSION = 2

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
                // new_id placeholder
                null,
            )

        private data class Global(
            val name: Int,
            val interfaceName: String,
            val version: Int,
        )

        private fun enumerateGlobals(
            display: Pointer,
            registry: Pointer,
        ): List<Global> {
            val out = mutableListOf<Global>()
            val globalCb =
                WlRegistryGlobal { _, _, name, iface, version ->
                    out += Global(name, iface, version)
                }
            val removeCb = WlRegistryGlobalRemove { _, _, _ -> }
            val listenerMem = WaylandListeners.packListener(listOf(globalCb, removeCb))

            WaylandClient.INSTANCE.wl_proxy_add_listener(registry, listenerMem, null)
            WaylandClient.INSTANCE.wl_display_roundtrip(display)
            // Wayland has no listener-detach call, and `global` events can fire
            // again whenever new globals appear later. Pin so the function
            // pointers stay valid.
            WaylandListeners.pin(listOf(globalCb, removeCb, listenerMem))
            return out
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
                // new_id placeholder
                null,
            )
    }
}
