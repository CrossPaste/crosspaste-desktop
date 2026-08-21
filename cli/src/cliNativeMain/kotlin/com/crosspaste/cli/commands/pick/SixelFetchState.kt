package com.crosspaste.cli.commands.pick

/**
 * Identifies one sixel pixel fetch completely: WHAT is being fetched (paste
 * id plus the requested cell box) and WHICH attempt it is ([generation]).
 * The generation makes keys unique across cancel/re-request cycles, so a
 * cancelled request's reply racing back can never be mistaken for the reply
 * to a newer identical request.
 */
internal data class SixelRequestKey(
    val generation: Int,
    val pasteId: Long,
    val boxColumns: Int,
    val boxRows: Int,
)

/**
 * The pick panel's sixel fetch bookkeeping, pure so the request lifecycle is
 * unit-testable. At most one request is active; a reply only counts when its
 * key equals the active key exactly.
 *
 * The box being part of the identity is what keeps post-resize redraws
 * alive: a same-paste request for a DIFFERENT box supersedes the in-flight
 * one instead of being swallowed by an id-only guard (which would consume
 * the redraw deadline with nothing left to answer it — the old-box reply
 * gets discarded and the image never reappears).
 */
internal class SixelFetchState {
    private var generation = 0
    private var activeKey: SixelRequestKey? = null

    /**
     * Registers the request to launch and returns its key, or null when an
     * identical request (same paste AND same box) is already in flight —
     * its reply will complete the draw, nothing new to launch. A non-null
     * return supersedes any active request; the caller must cancel that
     * request's job.
     */
    fun nextRequest(
        pasteId: Long,
        boxColumns: Int,
        boxRows: Int,
    ): SixelRequestKey? {
        val active = activeKey
        if (active != null &&
            active.pasteId == pasteId &&
            active.boxColumns == boxColumns &&
            active.boxRows == boxRows
        ) {
            return null
        }
        return SixelRequestKey(++generation, pasteId, boxColumns, boxRows)
            .also { activeKey = it }
    }

    /**
     * Forgets the active request (the caller cancels its job). Mandatory
     * alongside the job cancel: a cancelled fetch never posts its reply, and
     * a stale active key would block re-fetching the same paste and box when
     * the user selects it again.
     */
    fun cancel() {
        activeKey = null
    }

    /**
     * True when [key] is exactly the reply this state is waiting on — the
     * active request is then cleared (a second identical reply returns
     * false). False for anything stale: a superseded box or paste, or a
     * cancelled generation racing back; the active request, if any, stays
     * armed and untouched.
     */
    fun onReply(key: SixelRequestKey): Boolean {
        if (key != activeKey) return false
        activeKey = null
        return true
    }
}
