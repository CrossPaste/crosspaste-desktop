package com.crosspaste.platform.linux.api

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import kotlin.test.Test
import kotlin.test.assertContentEquals

class WMCtrlFormat32Test {

    private fun items(vararg values: Long): Memory =
        Memory(values.size.toLong() * NativeLong.SIZE).apply {
            values.forEachIndexed { i, value -> setNativeLong(i.toLong() * NativeLong.SIZE, NativeLong(value)) }
        }

    @Test
    fun `reads format-32 items stored as C longs`() {
        assertContentEquals(longArrayOf(301, 302, 0xFFFFFFFFL), WMCtrl.format32Items(items(301, 302, 0xFFFFFFFFL), 3))
    }

    @Test
    fun `stops at the reported count`() {
        assertContentEquals(longArrayOf(301, 302), WMCtrl.format32Items(items(301, 302, 303), 2))
    }
}
