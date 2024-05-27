package com.clipevery.clip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clipevery.dao.task.ClipTaskDao
import com.clipevery.task.extra.PullExtraInfo
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.ioDispatcher
import com.clipevery.utils.mainDispatcher
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId

class DesktopClipSyncProcessManager(private val clipTaskDao: ClipTaskDao) : ClipSyncProcessManager<ObjectId> {

    private val ioScope = CoroutineScope(ioDispatcher)

    override val processMap: MutableMap<ObjectId, ClipSingleProcess> = ConcurrentMap()

    override fun getProcess(key: ObjectId): ClipSingleProcess {
        return processMap.computeIfAbsent(key) {
            ClipSingleProcessImpl(ioScope, key, clipTaskDao)
        }
    }
}

class ClipSingleProcessImpl(
    ioScope: CoroutineScope,
    private val clipDataId: ObjectId,
    private val clipTaskDao: ClipTaskDao,
) : ClipSingleProcess {

    override var process: Float by mutableStateOf(0.0f)

    override val job: Job =
        ioScope.launch {
            val pullFileTask = clipTaskDao.getTask(clipDataId)

            if (pullFileTask.isEmpty()) {
                process = 1.0f
                return@launch
            }

            val pullFileTaskFlow = pullFileTask.asFlow()

            pullFileTaskFlow.collect { changes ->
                if (changes.list.isEmpty()) {
                    withContext(mainDispatcher) {
                        process = 1.0f
                    }
                    this@launch.cancel()
                }
                val clipTask = changes.list.first()
                val pullExtraInfo: PullExtraInfo = TaskUtils.getExtraInfo(clipTask, PullExtraInfo::class)
                val size = pullExtraInfo.pullChunks.size
                val count = pullExtraInfo.pullChunks.count { it }
                withContext(mainDispatcher) {
                    process = count / size.toFloat()
                }
            }
        }
}
