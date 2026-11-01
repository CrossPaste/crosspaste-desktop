package com.crosspaste.platform.linux.api

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WMCtrlAtomListTest {

    private fun atoms(vararg values: Long): Memory =
        Memory(values.size.toLong() * NativeLong.SIZE).apply {
            values.forEachIndexed { i, value -> setNativeLong(i.toLong() * NativeLong.SIZE, NativeLong(value)) }
        }

    @Test
    fun `finds the atom anywhere in a format-32 list`() {
        assertTrue(WMCtrl.containsAtom(atoms(301, 302, 303), 3, 303))
    }

    @Test
    fun `missing atom and items past the reported count are not found`() {
        val items = atoms(301, 302, 303)
        assertFalse(WMCtrl.containsAtom(items, 3, 304))
        assertFalse(WMCtrl.containsAtom(items, 2, 303))
    }
}
