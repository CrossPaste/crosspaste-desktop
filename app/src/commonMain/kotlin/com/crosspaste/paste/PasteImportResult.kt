package com.crosspaste.paste

sealed interface PasteImportResult {

    /**
     * The archive was read to the end. [successCount] of [totalCount] records
     * were stored; the rest were skipped because they could not be parsed or saved.
     */
    data class Completed(
        val successCount: Long,
        val totalCount: Long,
    ) : PasteImportResult

    /** The archive could not be opened or read at all. */
    data object Failed : PasteImportResult
}
