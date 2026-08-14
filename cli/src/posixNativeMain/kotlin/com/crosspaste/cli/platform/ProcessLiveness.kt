package com.crosspaste.cli.platform

import platform.posix.ESRCH
import platform.posix.errno
import platform.posix.kill

/**
 * Signal 0 performs error checking only, without delivering a signal.
 * EPERM (not allowed to signal) still proves the process exists; only
 * ESRCH means the pid is gone.
 */
internal fun isProcessAlive(pid: Long): Boolean {
    if (pid <= 0) return false
    if (kill(pid.toInt(), 0) == 0) return true
    return errno != ESRCH
}
