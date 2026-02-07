package com.crosspaste.net.exception

import java.net.ConnectException

class DesktopExceptionHandler : ExceptionHandler() {

    /**
     * Checks if the given throwable or any throwable in its exception hierarchy
     * (via cause or suppressed exceptions) contains a "port in use" message.
     *
     * This function uses recursion to traverse the exception hierarchy:
     * - First, it checks the current throwable's message.
     * - Then, it recursively checks the throwable's cause (if present).
     * - Finally, it recursively checks all suppressed exceptions (if any).
     *
     * @param throwable The throwable to check.
     * @return True if a "port in use" message is found, false otherwise.
     */
    private fun containsPortInUseMessage(
        throwable: Throwable,
        visited: MutableSet<Throwable> = mutableSetOf(),
    ): Boolean {
        if (!visited.add(throwable)) return false

        if (isExceptionMessageContainsPortInUse(throwable)) {
            return true
        }

        throwable.cause?.let {
            if (containsPortInUseMessage(it, visited)) {
                return true
            }
        }

        for (suppressed in throwable.suppressed) {
            if (containsPortInUseMessage(suppressed, visited)) {
                return true
            }
        }

        return false
    }

    private fun isExceptionMessageContainsPortInUse(throwable: Throwable): Boolean =
        throwable is java.net.BindException &&
            throwable.message?.lowercase()?.contains("already in use") == true

    override fun isPortAlreadyInUse(e: Throwable): Boolean = containsPortInUseMessage(e)

    override fun isConnectionRefused(e: Throwable): Boolean = e is ConnectException
}
