package com.crosspaste.task

import com.crosspaste.app.AppFileType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.realm.task.TaskType
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import okio.Path

class PullIconTaskExecutor(
    private val pasteRealm: PasteRealm,
    private val userDataPathProvider: UserDataPathProvider,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    companion object PullIconTaskExecutor {
        private val logger = KotlinLogging.logger {}

        private val fileUtils: FileUtils = getFileUtils()
    }

    override val taskType: Int = TaskType.PULL_ICON_TASK

    private val locks: MutableMap<String, Mutex> = ConcurrentMap()

    override suspend fun doExecuteTask(pasteTask: com.crosspaste.realm.task.PasteTask): PasteTaskResult {
        val baseExtraInfo: BaseExtraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)

        pasteRealm.getPasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
            pasteData.source?.let { source ->
                locks.getOrPut(source) { Mutex() }.withLock {
                    try {
                        val appInstanceId = pasteData.appInstanceId

                        val iconPath = userDataPathProvider.resolve("$source.png", AppFileType.ICON)
                        if (!fileUtils.existFile(iconPath)) {
                            syncManager.getSyncHandlers()[appInstanceId]?.let {
                                val port = it.syncRuntimeInfo.port
                                it.getConnectHostAddress()?.let { host ->
                                    return pullIcon(source, iconPath, host, port, baseExtraInfo)
                                } ?: run {
                                    return TaskUtils.createFailurePasteTaskResult(
                                        logger = logger,
                                        retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                                        startTime = pasteTask.modifyTime,
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
                                return TaskUtils.createFailurePasteTaskResult(
                                    logger = logger,
                                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                                    startTime = pasteTask.modifyTime,
                                    fails =
                                        listOf(
                                            createFailureResult(
                                                StandardErrorCode.PULL_ICON_TASK_FAIL,
                                                "Failed to sync paste to $appInstanceId",
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
        return SuccessPasteTaskResult()
    }

    private suspend fun pullIcon(
        source: String,
        iconPath: Path,
        host: String,
        port: Int,
        baseExtraInfo: BaseExtraInfo,
    ): PasteTaskResult {
        val toUrl: URLBuilder.(URLBuilder) -> Unit = { urlBuilder: URLBuilder ->
            buildUrl(urlBuilder, host, port)
        }

        val result = pullClientApi.pullIcon(source, toUrl)
        if (result is SuccessResult) {
            val byteReadChannel = result.getResult<ByteReadChannel>()
            fileUtils.writeFile(iconPath, byteReadChannel)

            logger.info { "Success to pull icon" }

            return SuccessPasteTaskResult()
        } else {
            return TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                startTime = Clock.System.now().toEpochMilliseconds(),
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
