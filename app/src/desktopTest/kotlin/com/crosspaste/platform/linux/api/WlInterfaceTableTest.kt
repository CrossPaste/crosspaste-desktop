package com.crosspaste.platform.linux.api

import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Checks the hand-written `wl_interface` / `wl_message` layout against the C
 * ABI libwayland reads: pure memory inspection, no library needed.
 */
class WlInterfaceTableTest {

    private fun Pointer.cString(offset: Long): String? = getPointer(offset)?.getString(0)

    @Test
    fun `interface header matches struct wl_interface`() {
        val table = WaylandDataControlProtocol.EXT.device.pointer

        assertEquals("ext_data_control_device_v1", table.cString(0))
        assertEquals(1, table.getInt(8)) // version
        assertEquals(3, table.getInt(12)) // method_count
        assertNotNull(table.getPointer(16)) // methods
        assertEquals(4, table.getInt(24)) // event_count
        assertNotNull(table.getPointer(32)) // events
    }

    @Test
    fun `messages carry name signature and typed argument tables in opcode order`() {
        val protocol = WaylandDataControlProtocol.WLR
        val events = protocol.device.pointer.getPointer(32)

        // data_offer(id: new_id offer): libwayland creates the proxy from types[0].
        val dataOffer = events.share(WaylandDataControlProtocol.DEVICE_EVENT_DATA_OFFER * 24L)
        assertEquals("data_offer", dataOffer.cString(0))
        assertEquals("n", dataOffer.cString(8))
        assertEquals(protocol.offer.pointer, dataOffer.getPointer(16).getPointer(0))

        val selection = events.share(WaylandDataControlProtocol.DEVICE_EVENT_SELECTION * 24L)
        assertEquals("selection", selection.cString(0))
        assertEquals("?o", selection.cString(8))

        // wlr added primary selection in v2; ext has it from v1.
        val primary = events.share(WaylandDataControlProtocol.DEVICE_EVENT_PRIMARY_SELECTION * 24L)
        assertEquals("2?o", primary.cString(8))
        val extPrimary =
            WaylandDataControlProtocol.EXT.device.pointer
                .getPointer(32)
                .share(WaylandDataControlProtocol.DEVICE_EVENT_PRIMARY_SELECTION * 24L)
        assertEquals("?o", extPrimary.cString(8))

        val methods = protocol.offer.pointer.getPointer(16)
        val receive = methods.share(WaylandDataControlProtocol.OFFER_RECEIVE * 24L)
        assertEquals("receive", receive.cString(0))
        assertEquals("sh", receive.cString(8))
        // Non-object arguments have null type entries but the array itself exists.
        assertNull(receive.getPointer(16).getPointer(0))
        assertNull(receive.getPointer(16).getPointer(8))
    }

    @Test
    fun `get_data_device binds the device interface for its new_id`() {
        val protocol = WaylandDataControlProtocol.EXT
        val methods = protocol.manager.pointer.getPointer(16)
        val getDevice = methods.share(WaylandDataControlProtocol.MANAGER_GET_DATA_DEVICE * 24L)
        assertEquals("get_data_device", getDevice.cString(0))
        assertEquals("no", getDevice.cString(8))
        assertEquals(protocol.device.pointer, getDevice.getPointer(16).getPointer(0))
    }

    @Test
    fun `manager names identify the protocol variant`() {
        assertEquals(
            WaylandDataControlProtocol.EXT,
            WaylandDataControlProtocol.byManagerName("ext_data_control_manager_v1"),
        )
        assertEquals(
            WaylandDataControlProtocol.WLR,
            WaylandDataControlProtocol.byManagerName("zwlr_data_control_manager_v1"),
        )
        assertNull(WaylandDataControlProtocol.byManagerName("wl_data_device_manager"))
    }

    @Test
    fun `argument count must match the signature`() {
        assertFailsWith<IllegalArgumentException> {
            WlInterfaceTable.Message("bad", "su", listOf(null))
        }
        // Since-version digits and nullable markers are not arguments.
        WlInterfaceTable.Message("ok", "3?o", listOf(null))
    }

    @Test
    fun `marshal arguments lay out eight bytes per argument`() {
        val args = WaylandClientLib.arguments(7, "text/plain", 42, null)
        val pointer = assertNotNull(args.pointer)
        assertEquals(7, pointer.getInt(0))
        assertEquals("text/plain", pointer.getPointer(8).getString(0))
        assertEquals(42, pointer.getInt(16))
        assertNull(pointer.getPointer(24))
        assertNull(WaylandClientLib.arguments().pointer)
    }
}
