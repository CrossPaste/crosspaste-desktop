package com.crosspaste.paste

sealed interface PasteImportResult {

    /**
     * The archive was read to the end. [successCount] of [totalCount] records
     * were stored; the rest were skipped because they could not be parsed or saved.
     */
    data class Completed(
        val successCount: Long,
        val totalCount: Long,
    ) : PasteImportResult {
        val isEmpty: Boolean get() = totalCount == 0L
        val isAllSuccess: Boolean get() = totalCount > 0L && successCount == totalCount
        val isPartial: Boolean get() = successCount in 1 until totalCount
        val isAllFailed: Boolean get() = totalCount > 0L && successCount == 0L
    }

    /** The archive could not be opened or read at all. */
    data object Failed : PasteImportResult
}

/**
 * Outcome of an import run, associating the imported file name with its result.
 */
data class ImportOutcome(
    val fileName: String,
    val result: PasteImportResult,
)
