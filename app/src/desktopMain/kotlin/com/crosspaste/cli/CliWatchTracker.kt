package com.crosspaste.cli

import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState

/**
 * Diffs successive snapshots of the newest-first paste window (as emitted by
 * `PasteDao.getPasteDataFlow`) into a stream of "newly arrived" rows for the
 * `/cli/watch` endpoint.
 *
 * A row counts as arrived when it becomes LOADED with a createTime the tracker
 * has not reported yet. That covers all three arrival shapes:
 * - a fresh capture (local, remote-synced, or CLI copy) entering the window,
 * - a LOADING row observed earlier finishing its release,
 * - an existing row re-copied (its createTime is bumped to now).
 *
 * Rows already LOADED in the first snapshot are the pre-subscription history
 * and are never replayed; LOADING rows in the first snapshot are captures
 * still in flight, so they are reported once they finish.
 *
 * The window is a top-N by createTime, so deleting newer rows can slide an
 * old, never-seen row back into view. Such rows carry a createTime at or
 * below the baseline floor and are silently adopted instead of reported.
 * Seen-row state is kept for the lifetime of the subscription (a few dozen
 * bytes per distinct row) — pruning would reopen that same hole.
 */
class CliWatchTracker(
    baseline: List<PasteData>,
    windowLimit: Int,
) {
    private class KnownRow(
        var createTime: Long,
        var reported: Boolean,
    )

    private val known = mutableMapOf<Long, KnownRow>()

    // With a non-full baseline window every existing row was observed, so any
    // unknown id is genuinely new regardless of its createTime
    private val baselineFloor: Long =
        if (baseline.size < windowLimit) {
            Long.MIN_VALUE
        } else {
            baseline.minOf { it.createTime }
        }

    init {
        for (row in baseline) {
            known[row.id] = KnownRow(row.createTime, reported = row.pasteState == PasteState.LOADED)
        }
    }

    /**
     * Returns the rows that newly arrived in this snapshot, oldest first
     * (the window itself is newest first).
     */
    fun onWindow(rows: List<PasteData>): List<PasteData> {
        val arrived = mutableListOf<PasteData>()
        for (row in rows.asReversed()) {
            val loaded = row.pasteState == PasteState.LOADED
            val knownRow = known[row.id]
            when {
                knownRow == null -> {
                    // Old rows sliding back into the window after deletions
                    // are pre-subscription history, not arrivals
                    val preexisting = row.createTime <= baselineFloor
                    val report = loaded && !preexisting
                    known[row.id] = KnownRow(row.createTime, reported = report || preexisting)
                    if (report) arrived += row
                }

                row.createTime > knownRow.createTime -> {
                    // A re-copy bumps createTime: that is new clipboard
                    // activity even though the row itself is old
                    knownRow.createTime = row.createTime
                    knownRow.reported = loaded
                    if (loaded) arrived += row
                }

                !knownRow.reported && loaded -> {
                    knownRow.reported = true
                    arrived += row
                }
            }
        }
        return arrived
    }
}
