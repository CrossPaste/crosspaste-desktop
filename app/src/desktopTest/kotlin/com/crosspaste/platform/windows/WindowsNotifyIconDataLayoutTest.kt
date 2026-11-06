package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Shell32
import com.sun.jna.Native
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertEquals

// JNA maps char[] to wchar_t, which is 4 bytes off Windows, so the layout only holds there
@EnabledOnOs(OS.WINDOWS)
class WindowsNotifyIconDataLayoutTest {
    @Test
    fun `cbSize matches the Vista+ NOTIFYICONDATAW layout`() {
        assertEquals(if (Native.POINTER_SIZE == 8) 976 else 956, Shell32.NotifyIconData().cbSize)
    }
}
