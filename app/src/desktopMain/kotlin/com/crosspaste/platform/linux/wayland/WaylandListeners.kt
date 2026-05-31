package com.crosspaste.platform.linux.wayland

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * JNA [Callback] declarations for the Wayland events we listen on, plus helpers
 * to pack a set of callbacks into the contiguous function-pointer array that
 * `wl_proxy_add_listener` expects.
 *
 * Order inside [packListener] must match the event order declared in the
 * protocol XML (which is the same order we register in [WaylandInterfaces]).
 */
internal object WaylandListeners {

    private val PTR_SIZE = Native.POINTER_SIZE.toLong()

    // Callbacks attached via `wl_proxy_add_listener` must remain reachable for
    // the proxy's lifetime — Wayland never detaches a listener. Anything that
    // outlives the call-site Kotlin scope gets pinned here.
    private val rooted: MutableList<Any> = mutableListOf()

    @Synchronized
    fun pin(refs: List<Any>) {
        rooted.addAll(refs)
    }

    /** Build a contiguous block of C function pointers, in declaration order. */
    fun packListener(callbacks: List<Callback>): Memory {
        val block = Memory(PTR_SIZE * callbacks.size)
        callbacks.forEachIndexed { i, cb ->
            block.setPointer(PTR_SIZE * i, CallbackReference.getFunctionPointer(cb))
        }
        return block
    }
}

// ─── wl_registry ─────────────────────────────────────────────────────────────

internal fun interface WlRegistryGlobal : Callback {
    fun invoke(
        data: Pointer?,
        registry: Pointer,
        name: Int,
        iface: String,
        version: Int,
    )
}

internal fun interface WlRegistryGlobalRemove : Callback {
    fun invoke(
        data: Pointer?,
        registry: Pointer,
        name: Int,
    )
}

// ─── zwlr_data_control_device_v1 ─────────────────────────────────────────────

internal fun interface ZwlrDataControlDeviceDataOffer : Callback {
    fun invoke(
        data: Pointer?,
        device: Pointer,
        offer: Pointer,
    )
}

internal fun interface ZwlrDataControlDeviceSelection : Callback {
    fun invoke(
        data: Pointer?,
        device: Pointer,
        offer: Pointer?,
    )
}

internal fun interface ZwlrDataControlDeviceFinished : Callback {
    fun invoke(
        data: Pointer?,
        device: Pointer,
    )
}

internal fun interface ZwlrDataControlDevicePrimarySelection : Callback {
    fun invoke(
        data: Pointer?,
        device: Pointer,
        offer: Pointer?,
    )
}

// ─── zwlr_data_control_offer_v1 ──────────────────────────────────────────────

internal fun interface ZwlrDataControlOfferOffer : Callback {
    fun invoke(
        data: Pointer?,
        offer: Pointer,
        mimeType: String,
    )
}
