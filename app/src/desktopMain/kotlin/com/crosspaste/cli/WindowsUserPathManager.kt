package com.crosspaste.cli

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Registry view of the Windows PATH, backing the one-click "Add to PATH"
 * action for installs whose terminal command is not wired by the installer
 * (portable zip, dev runs). Abstracted from [CliSymlinkService] so the state
 * machine is testable off-Windows.
 */
interface WindowsUserPathManager {

    /** Whether [dir] is already on the user or machine PATH as the registry defines them. */
    fun isOnPath(dir: String): Boolean

    /**
     * Appends [dir] to the user PATH (HKCU\Environment) and broadcasts the
     * change so newly started shells pick it up. True when [dir] ends up on
     * the PATH — including when it already was.
     */
    fun addToUserPath(dir: String): Boolean

    /**
     * Moves [dir] to the front of the user PATH (removing any existing
     * entries equal to it), so it wins over later user-PATH entries — e.g.
     * the WindowsApps alias directory of a parallel MSIX install. Machine
     * PATH entries still come first in a process's PATH; a shadow there
     * cannot be fixed from here.
     */
    fun promoteToUserPathFront(dir: String): Boolean

    /**
     * The PATH a freshly launched process receives (machine then user,
     * expanded), or null when it cannot be read. Lets availability probes see
     * a just-performed install without restarting this app, whose own
     * environment is frozen at launch.
     */
    fun newProcessPath(): String?
}

/**
 * cmd.exe reads at most 8191 chars of a variable; a longer PATH silently
 * truncates there and can break unrelated tools. The combined machine+user
 * value a process would receive must stay under this after any edit.
 */
internal const val MAX_COMBINED_PATH_CHARS = 8191

/** Canonical form for PATH-entry comparison: trimmed, unquoted, no trailing separator, lowercase. */
internal fun canonicalPathEntry(entry: String): String =
    entry
        .trim()
        .removeSurrounding("\"")
        .trimEnd('\\', '/')
        .lowercase()

/**
 * Whether the semicolon-separated [pathValue] contains [dir]. Entries may be
 * quoted and REG_EXPAND_SZ values may hold %VAR% references, so each entry is
 * also compared after [expand].
 */
internal fun pathValueContainsDir(
    pathValue: String?,
    dir: String,
    expand: (String) -> String = { it },
): Boolean {
    if (pathValue == null) return false
    val target = canonicalPathEntry(dir)
    if (target.isEmpty()) return false
    return pathValue.split(';').any { entry ->
        val canonical = canonicalPathEntry(entry)
        canonical.isNotEmpty() &&
            (canonical == target || canonicalPathEntry(expand(entry)) == target)
    }
}

/** Appends [dir] to a semicolon-separated [pathValue], tolerating a trailing separator. */
internal fun appendToPathValue(
    pathValue: String?,
    dir: String,
): String =
    when {
        pathValue.isNullOrBlank() -> dir
        pathValue.endsWith(";") -> pathValue + dir
        else -> "$pathValue;$dir"
    }

/**
 * Puts [dir] first in [pathValue], dropping entries equal to it (directly or
 * through [expand]); every other entry keeps its text and order.
 */
internal fun promoteInPathValue(
    pathValue: String?,
    dir: String,
    expand: (String) -> String = { it },
): String {
    val target = canonicalPathEntry(dir)
    val kept =
        pathValue
            .orEmpty()
            .split(';')
            .filter { entry ->
                val canonical = canonicalPathEntry(entry)
                canonical.isNotEmpty() &&
                    canonical != target &&
                    canonicalPathEntry(expand(entry)) != target
            }
    return (listOf(dir) + kept).joinToString(";")
}

/**
 * The real registry implementation. Design notes:
 * - Direct JNA registry access, not `setx` (which truncates PATH at 1024
 *   chars) and not generated PowerShell (see the cmd/PowerShell interop
 *   pitfalls in CLAUDE.md).
 * - The user Path value is usually REG_EXPAND_SZ holding %VAR% references;
 *   it is read unexpanded and written back with its original type so those
 *   references survive the edit. JNA's registryGetStringValue returns
 *   REG_EXPAND_SZ values raw (RegQueryValueEx, no expansion), so expansion
 *   is always done explicitly via ExpandEnvironmentStrings.
 */
class JnaWindowsUserPathManager : WindowsUserPathManager {

    companion object {
        private const val USER_ENV_KEY = "Environment"
        private const val MACHINE_ENV_KEY =
            "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"
        private const val PATH_VALUE = "Path"

        private const val WM_SETTINGCHANGE = 0x001A
        private const val SMTO_ABORTIFHUNG = 0x0002

        /** Per recipient window, not global — HWND_BROADCAST visits every top-level window. */
        private const val BROADCAST_PER_WINDOW_TIMEOUT_MS = 1000
    }

    private val logger = KotlinLogging.logger {}

    private val expand: (String) -> String = { value ->
        runCatching { Kernel32Util.expandEnvironmentStrings(value) }.getOrDefault(value)
    }

    /** Raw (unexpanded) Path at [root]\[keyPath] and whether it is REG_EXPAND_SZ; absent reads as (null, true). */
    private fun readRawPath(
        root: WinReg.HKEY,
        keyPath: String,
    ): Pair<String?, Boolean> {
        if (!Advapi32Util.registryValueExists(root, keyPath, PATH_VALUE)) {
            return null to true
        }
        runCatching {
            return Advapi32Util.registryGetExpandableStringValue(root, keyPath, PATH_VALUE) to true
        }
        // Not REG_EXPAND_SZ; fall back to a plain REG_SZ read
        return Advapi32Util.registryGetStringValue(root, keyPath, PATH_VALUE) to false
    }

    /** Fully expanded Path at [root]\[keyPath], or null when absent/unreadable. */
    private fun readExpandedPath(
        root: WinReg.HKEY,
        keyPath: String,
    ): String? = runCatching { readRawPath(root, keyPath).first?.let(expand) }.getOrNull()

    override fun isOnPath(dir: String): Boolean =
        runCatching {
            pathValueContainsDir(readRawPath(WinReg.HKEY_CURRENT_USER, USER_ENV_KEY).first, dir, expand) ||
                pathValueContainsDir(
                    readExpandedPath(WinReg.HKEY_LOCAL_MACHINE, MACHINE_ENV_KEY),
                    dir,
                    expand,
                )
        }.getOrElse { e ->
            logger.warn(e) { "Failed to read PATH from the registry" }
            false
        }

    override fun addToUserPath(dir: String): Boolean =
        editUserPath(dir) { raw ->
            if (pathValueContainsDir(raw, dir, expand)) raw else appendToPathValue(raw, dir)
        }

    override fun promoteToUserPathFront(dir: String): Boolean =
        editUserPath(dir) { raw -> promoteInPathValue(raw, dir, expand) }

    /**
     * Applies [transform] to the raw user Path and writes it back with its
     * original registry type. Refuses (keeping the original value) when the
     * combined machine+user PATH a process would receive exceeds
     * [MAX_COMBINED_PATH_CHARS] — a too-long PATH silently truncates in
     * cmd.exe and can break unrelated tools.
     */
    private fun editUserPath(
        dir: String,
        transform: (String?) -> String?,
    ): Boolean =
        runCatching {
            val (raw, expandable) = readRawPath(WinReg.HKEY_CURRENT_USER, USER_ENV_KEY)
            val updated = transform(raw)
            if (updated != null && updated != raw) {
                val machine = readExpandedPath(WinReg.HKEY_LOCAL_MACHINE, MACHINE_ENV_KEY)
                val combinedLength = appendToPathValue(machine, expand(updated)).length
                if (combinedLength > MAX_COMBINED_PATH_CHARS) {
                    logger.warn {
                        "Refusing to grow PATH to $combinedLength chars (limit $MAX_COMBINED_PATH_CHARS); $dir not added"
                    }
                    return false
                }
                if (expandable) {
                    Advapi32Util.registrySetExpandableStringValue(
                        WinReg.HKEY_CURRENT_USER,
                        USER_ENV_KEY,
                        PATH_VALUE,
                        updated,
                    )
                } else {
                    Advapi32Util.registrySetStringValue(
                        WinReg.HKEY_CURRENT_USER,
                        USER_ENV_KEY,
                        PATH_VALUE,
                        updated,
                    )
                }
            }
            broadcastEnvironmentChange()
            true
        }.getOrElse { e ->
            logger.warn(e) { "Failed to update the user PATH for $dir" }
            false
        }

    override fun newProcessPath(): String? {
        val joined =
            listOfNotNull(
                readExpandedPath(WinReg.HKEY_LOCAL_MACHINE, MACHINE_ENV_KEY),
                readExpandedPath(WinReg.HKEY_CURRENT_USER, USER_ENV_KEY),
            ).joinToString(";")
        return joined.ifEmpty { null }
    }

    /**
     * Tells running Explorer/shells the environment changed; without it, only
     * a re-login would surface the new PATH to newly opened terminals. Best
     * effort: the registry write already succeeded, a zero return (timeout or
     * failure) is only logged, and the success notification always mentions
     * re-login as the fallback.
     */
    private fun broadcastEnvironmentChange() {
        runCatching {
            val param = Memory((("Environment".length + 1) * 2).toLong())
            param.setWideString(0, "Environment")
            val result =
                User32.INSTANCE.SendMessageTimeout(
                    WinUser.HWND_BROADCAST,
                    WM_SETTINGCHANGE,
                    WinDef.WPARAM(0),
                    WinDef.LPARAM(Pointer.nativeValue(param)),
                    SMTO_ABORTIFHUNG,
                    BROADCAST_PER_WINDOW_TIMEOUT_MS,
                    WinDef.DWORDByReference(),
                )
            if (result.toLong() == 0L) {
                logger.warn { "WM_SETTINGCHANGE broadcast timed out or failed; open terminals may need a re-login" }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to broadcast the environment change" }
        }
    }
}
