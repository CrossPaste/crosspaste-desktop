package com.crosspaste.ui.paste.side.preview

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TitleEditCommitterTest {

    @Test
    fun enterThenImmediateFocusLossCommitsOnlyOnce() =
        runTest {
            val gate = CompletableDeferred<Result<Unit>>()
            var updateCalls = 0
            var updatedName: String? = null
            val committer =
                TitleEditCommitter(backgroundScope) { name ->
                    updateCalls++
                    updatedName = name
                    gate.await()
                }

            var savedName: String? = null
            var reverts = 0
            var failures = 0
            val commit = { text: String ->
                committer.commit(
                    text = text,
                    onRevert = { reverts++ },
                    onSaved = { savedName = it },
                    onFailure = { failures++ },
                )
            }

            // Enter starts the save; the update is still in flight...
            commit("edited name")
            runCurrent()
            // ...when focus loss requests a second commit (user typed more meanwhile)
            commit("edited name typed further")
            runCurrent()

            assertEquals(1, updateCalls)
            assertEquals("edited name", updatedName)
            assertNull(savedName)

            gate.complete(Result.success(Unit))
            runCurrent()

            // The saved name is the snapshot from the first commit, not the later text
            assertEquals("edited name", savedName)
            assertEquals(0, reverts)
            assertEquals(0, failures)
        }

    @Test
    fun blankTextRevertsOnceAndBlocksFollowUpCommit() =
        runTest {
            var updateCalls = 0
            val committer =
                TitleEditCommitter(backgroundScope) {
                    updateCalls++
                    Result.success(Unit)
                }

            var reverts = 0
            val commit = {
                committer.commit(
                    text = "   ",
                    onRevert = { reverts++ },
                    onSaved = {},
                    onFailure = {},
                )
            }

            commit()
            // Disposal of the edit field fires a focus-loss commit right after
            commit()
            runCurrent()

            assertEquals(1, reverts)
            assertEquals(0, updateCalls)
        }

    @Test
    fun thrownExceptionReportsFailureAndUnlocksSessionForRetry() =
        runTest {
            var updateCalls = 0
            var shouldThrow = true
            val committer =
                TitleEditCommitter(backgroundScope) {
                    updateCalls++
                    if (shouldThrow) {
                        throw IllegalStateException("db exploded")
                    }
                    Result.success(Unit)
                }

            var savedName: String? = null
            var failures = 0
            val commit = {
                committer.commit(
                    text = "name",
                    onRevert = {},
                    onSaved = { savedName = it },
                    onFailure = { failures++ },
                )
            }

            commit()
            runCurrent()
            assertEquals(1, failures)
            assertNull(savedName)

            shouldThrow = false
            commit()
            runCurrent()
            assertEquals(2, updateCalls)
            assertEquals("name", savedName)
        }

    @Test
    fun failureUnlocksSessionForRetry() =
        runTest {
            var updateCalls = 0
            var result: Result<Unit> = Result.failure(RuntimeException("boom"))
            val committer =
                TitleEditCommitter(backgroundScope) {
                    updateCalls++
                    result
                }

            var savedName: String? = null
            var failures = 0
            val commit = {
                committer.commit(
                    text = "name",
                    onRevert = {},
                    onSaved = { savedName = it },
                    onFailure = { failures++ },
                )
            }

            commit()
            runCurrent()
            assertEquals(1, failures)
            assertNull(savedName)

            result = Result.success(Unit)
            commit()
            runCurrent()
            assertEquals(2, updateCalls)
            assertEquals("name", savedName)
        }
}
