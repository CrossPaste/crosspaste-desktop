package com.crosspaste.platform.linux

object LinuxSession {

    fun isWayland(): Boolean =
        !System.getenv("WAYLAND_DISPLAY").isNullOrBlank() ||
            System.getenv("XDG_SESSION_TYPE")?.equals("wayland", ignoreCase = true) == true
}
