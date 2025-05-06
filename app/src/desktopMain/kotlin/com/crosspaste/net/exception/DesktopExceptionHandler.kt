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
    private fun containsPortInUseMessage(throwable: Throwable): Boolean {
        // Check if the current throwable's message indicates a "port in use" error.
        if (isExceptionMessageContainsPortInUse(throwable)) {
            return true
        }

        // Recursively check the cause of the throwable, if it exists.
        throwable.cause?.let {
            if (containsPortInUseMessage(it)) {
                return true
            }
        }

        // Recursively check all suppressed exceptions, if any exist.
        for (suppressed in throwable.suppressed) {
            if (containsPortInUseMessage(suppressed)) {
                return true
            }
        }

        // Return false if no "port in use" message is found in the hierarchy.
        return false
    }

    private fun isExceptionMessageContainsPortInUse(throwable: Throwable): Boolean {
        return throwable is java.net.BindException &&
            throwable.message?.lowercase()?.contains("already in use") == true
    }

    override fun isPortAlreadyInUse(e: Throwable): Boolean {
        return containsPortInUseMessage(e)
    }

    override fun isConnectionRefused(e: Throwable): Boolean {
        return e is ConnectException
    }
}
