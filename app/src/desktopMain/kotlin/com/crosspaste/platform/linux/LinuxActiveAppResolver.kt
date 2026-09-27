package com.crosspaste.platform.linux

import com.crosspaste.platform.linux.api.WMCtrl
import com.crosspaste.platform.linux.api.X11Api
import com.sun.jna.platform.unix.X11.Window
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * The active application resolved for a clipboard change.
 *
 * [x11Window] is non-null only when the app was resolved through X11, where the
 * window carries the `_NET_WM_ICON` used for icon capture; Wayland-resolved
 * apps fall back to desktop-entry icon lookup instead.
 */
data class LinuxActiveApp(
    val appName: String,
    val x11Window: Window?,
)

/**
 * Resolves which application currently has focus.
 *
 * Wayland deliberately offers no universal "who is focused" API, so the answer
 * is compositor-specific: Hyprland and Sway expose it over their IPC sockets,
 * while everything else falls back to X11's `_NET_ACTIVE_WINDOW` — correct for
 * native X11 sessions and for XWayland apps, but blind to native Wayland
 * windows (see [X11ActiveAppResolver] for how stale answers are suppressed).
 */
interface LinuxActiveAppResolver {

    fun getActiveApp(): LinuxActiveApp?

    companion object {

        private val logger = KotlinLogging.logger {}

        /**
         * Picks the best resolver for the current session. The [env] parameter
         * exists for tests; production callers use the real environment.
         */
        fun isWaylandSession(env: (String) -> String? = System::getenv): Boolean =
            env("XDG_SESSION_TYPE")?.equals("wayland", ignoreCase = true) == true ||
                env("WAYLAND_DISPLAY") != null

        fun detect(env: (String) -> String? = System::getenv): LinuxActiveAppResolver {
            if (!isWaylandSession(env)) {
                logger.info { "Active app resolver: X11 (X11 session)" }
                return X11ActiveAppResolver(guardAgainstStaleFocus = false)
            }
            HyprlandActiveAppResolver.detect(env)?.let {
                logger.info { "Active app resolver: Hyprland IPC" }
                return it
            }
            SwayActiveAppResolver.detect(env)?.let {
                logger.info { "Active app resolver: Sway IPC" }
                return it
            }
            logger.info {
                "Active app resolver: X11 via XWayland (focus-guarded); " +
                    "native Wayland windows cannot be identified on this compositor"
            }
            return X11ActiveAppResolver(guardAgainstStaleFocus = true)
        }
    }
}

/**
 * Resolves the active app from X11's `_NET_ACTIVE_WINDOW`.
 *
 * In a Wayland session this property only tracks XWayland windows: when focus
 * moves to a native Wayland window it goes stale, pointing at whichever X app
 * was focused last. A stale source is worse than none (it mislabels history
 * and can wrongly trigger source exclusion), so with [guardAgainstStaleFocus]
 * the resolver first checks `XGetInputFocus` — compositors withdraw the
 * XWayland input focus (None/PointerRoot) while a Wayland surface is focused,
 * and in that state we report no source instead of a stale one. Best-effort:
 * compositors that park focus on a dummy X window defeat the guard, which then
 * behaves like today's unguarded read.
 */
class X11ActiveAppResolver(
    private val guardAgainstStaleFocus: Boolean,
) : LinuxActiveAppResolver {

    private val logger = KotlinLogging.logger {}

    override fun getActiveApp(): LinuxActiveApp? =
        runCatching {
            val x11 = X11Api.INSTANCE
            val display = x11.XOpenDisplay(null) ?: return null
            try {
                if (guardAgainstStaleFocus && !X11Api.hasXInputFocus(display)) {
                    logger.debug { "X input focus withdrawn (Wayland window focused), no source" }
                    null
                } else {
                    WMCtrl.getActiveWindow(display)?.let { window ->
                        WMCtrl.getWindowClass(display, window)?.let { windowClass ->
                            LinuxActiveApp(windowClass.second, window)
                        }
                    }
                }
            } finally {
                x11.XCloseDisplay(display)
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to resolve active app via X11" }
            null
        }
}
