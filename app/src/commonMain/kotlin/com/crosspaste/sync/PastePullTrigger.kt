package com.crosspaste.sync

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Catches up on a peer's pastes whenever it becomes CONNECTED (including the
 * first time after startup), through [PastePullService.pullDevice]. Pushes only
 * reach connected peers, so this is what fills in what was missed meanwhile.
 *
 * Each peer has one worker fed by a conflated trigger: triggers arriving while a
 * pull runs collapse into a single follow-up pull, never an overlapping one.
 */
class PastePullTrigger(
    private val configManager: CommonConfigManager,
    private val pastePullService: PastePullService,
    private val syncManager: SyncManager,
    private val scope: CoroutineScope = namedScope(ioDispatcher, "PastePullTrigger"),
) {

    private val logger = KotlinLogging.logger {}

    private val started = atomic(false)

    private val workers: MutableMap<String, Worker> = ConcurrentMap()

    private class Worker(
        val trigger: Channel<Unit>,
        val job: Job,
    )

    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return

        scope.launch {
            var pullable = emptySet<String>()
            combine(
                syncManager.realTimeSyncRuntimeInfos,
                configManager.config.map { it.enableAutoPastePull }.distinctUntilChanged(),
            ) { syncRuntimeInfos, enabled -> syncRuntimeInfos to enabled }
                .collect { (syncRuntimeInfos, enabled) ->
                    val present = syncRuntimeInfos.map { it.appInstanceId }.toSet()
                    (workers.keys - present).forEach { stopWorker(it) }

                    if (!enabled) {
                        workers.keys.toList().forEach { stopWorker(it) }
                        // Re-enabling then counts every connected peer as newly connected
                        pullable = emptySet()
                        return@collect
                    }

                    val nowPullable = syncRuntimeInfos.pullableIds()
                    (nowPullable - pullable).forEach { requestPull(it) }
                    pullable = nowPullable
                }
        }
    }

    fun stop() {
        if (!started.compareAndSet(expect = true, update = false)) return
        scope.cancel()
        workers.clear()
    }

    // Only called from the single collector coroutine, so the get-or-create is not racy
    private fun requestPull(appInstanceId: String) {
        val worker =
            workers.getOrPut(appInstanceId) {
                val trigger = Channel<Unit>(Channel.CONFLATED)
                Worker(trigger, scope.launch { runWorker(appInstanceId, trigger) })
            }
        worker.trigger.trySend(Unit)
    }

    private suspend fun runWorker(
        appInstanceId: String,
        trigger: Channel<Unit>,
    ) {
        for (signal in trigger) {
            try {
                pastePullService.pullDevice(appInstanceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Auto pull from $appInstanceId failed" }
            }
        }
    }

    private fun stopWorker(appInstanceId: String) {
        workers.remove(appInstanceId)?.let { worker ->
            worker.trigger.close()
            worker.job.cancel()
        }
    }

    private fun List<SyncRuntimeInfo>.pullableIds(): Set<String> =
        filter { it.connectState == SyncState.CONNECTED && it.allowReceive }.map { it.appInstanceId }.toSet()
}
