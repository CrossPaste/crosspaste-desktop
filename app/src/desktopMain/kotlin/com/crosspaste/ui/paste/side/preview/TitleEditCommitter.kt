package com.crosspaste.ui.paste.side.preview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Serializes commits of a single title-edit session. Enter and focus loss can
 * both request a commit for the same edit; only the first request runs, and the
 * committed text is snapshotted at that moment. A failed save unlocks the
 * session so the user can retry.
 */
class TitleEditCommitter(
    private val scope: CoroutineScope,
    private val updateName: suspend (String) -> Result<Unit>,
) {
    private var committing = false

    fun commit(
        text: String,
        onRevert: () -> Unit,
        onSaved: (String) -> Unit,
        onFailure: () -> Unit,
    ) {
        if (committing) return
        committing = true
        if (text.isBlank()) {
            onRevert()
            return
        }
        scope.launch {
            val result =
                try {
                    updateName(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            result
                .onSuccess {
                    onSaved(text)
                }.onFailure {
                    committing = false
                    onFailure()
                }
        }
    }
}
