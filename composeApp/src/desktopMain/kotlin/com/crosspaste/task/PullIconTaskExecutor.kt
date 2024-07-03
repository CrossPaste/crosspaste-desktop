package com.crosspaste.task

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.task.ClipTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.path.PathProvider
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.TaskUtils.createFailureClipTaskResult
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists

class PullIconTaskExecutor(
    private val clipDao: ClipDao,
    private val pathProvider: PathProvider,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    companion object PullIconTaskExecutor {
        private val logger = KotlinLogging.logger {}

        private val fileUtils: FileUtils = getFileUtils()
    }

    override val taskType: Int = TaskType.PULL_ICON_TASK

    private val locks: MutableMap<String, Mutex> = ConcurrentHashMap<String, Mutex>()

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val baseExtraInfo: BaseExtraInfo = TaskUtils.getExtraInfo(clipTask, BaseExtraInfo::class)

        clipDao.getClipData(clipTask.clipDataId!!)?.let { clipData ->
            clipData.source?.let { source ->
                locks.getOrPut(source) { Mutex() }.withLock {
                    try {
                        val appInstanceId = clipData.appInstanceId

                        val iconPath = pathProvider.resolve("$source.png", AppFileType.ICON)
                        if (!iconPath.exists()) {
                            syncManager.getSyncHandlers()[appInstanceId]?.let {
                                val port = it.syncRuntimeInfo.port
                                it.getConnectHostAddress()?.let { host ->
                                    return pullIcon(source, iconPath, host, port, baseExtraInfo)
                                } ?: run {
                                    return createFailureClipTaskResult(
                                        logger = logger,
                                        retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                                        startTime = clipTask.modifyTime,
                                        fails =
                                            listOf(
                                                createFailureResult(
                                                    StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                                                    "Failed to get connect host address by $appInstanceId",
                                                ),
                                            ),
                                        extraInfo = baseExtraInfo,
                                    )
                                }
                            } ?: run {
                                return createFailureClipTaskResult(
                                    logger = logger,
                                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                                    startTime = clipTask.modifyTime,
                                    fails =
                                        listOf(
                                            createFailureResult(
                                                StandardErrorCode.PULL_ICON_TASK_FAIL,
                                                "Failed to sync clip to $appInstanceId",
                                            ),
                                        ),
                                    extraInfo = baseExtraInfo,
                                )
                            }
                        }
                    } finally {
                        locks.remove(source)
                    }
                }
            }
        }
        return SuccessClipTaskResult()
    }

    private suspend fun pullIcon(
        source: String,
        iconPath: Path,
        host: String,
        port: Int,
        baseExtraInfo: BaseExtraInfo,
    ): ClipTaskResult {
        val toUrl: URLBuilder.(URLBuilder) -> Unit = { urlBuilder: URLBuilder ->
            buildUrl(urlBuilder, host, port)
        }

        val result = pullClientApi.pullIcon(source, toUrl)
        if (result is SuccessResult) {
            val byteReadChannel = result.getResult<ByteReadChannel>()
            fileUtils.writeFile(iconPath, byteReadChannel)

            logger.info { "Success to pull icon" }

            return SuccessClipTaskResult()
        } else {
            return createFailureClipTaskResult(
                logger = logger,
                retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                startTime = System.currentTimeMillis(),
                fails =
                    listOf(
                        createFailureResult(
                            StandardErrorCode.PULL_ICON_TASK_FAIL,
                            "Failed to pull icon $source",
                        ),
                    ),
                extraInfo = baseExtraInfo,
            )
        }
    }
}
