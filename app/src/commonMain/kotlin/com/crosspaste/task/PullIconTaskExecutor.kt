package com.crosspaste.task

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.ErrorCodeSupplier
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

class PullIconTaskExecutor(
    private val pasteDao: PasteDao,
    private val userDataPathProvider: UserDataPathProvider,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val fileUtils: FileUtils = getFileUtils()

    override val taskType: Int = TaskType.PULL_ICON_TASK

    private val lock = Mutex()

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val baseExtraInfo: BaseExtraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)

        return pasteDao.getNoDeletePasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
            pasteData.source?.let { source ->
                lock.withLock(source) {
                    runCatching {
                        val appInstanceId = pasteData.appInstanceId

                        val iconPath = userDataPathProvider.resolve("$source.png", AppFileType.ICON)
                        if (!fileUtils.existFile(iconPath)) {
                            syncManager.getSyncHandlers()[appInstanceId]?.let {
                                val port = it.getCurrentSyncRuntimeInfo().port
                                it.getConnectHostAddress()?.let { host ->
                                    pullIcon(source, iconPath, host, port, baseExtraInfo)
                                } ?: run {
                                    createFailurePasteTaskResult(
                                        baseExtraInfo = baseExtraInfo,
                                        errorCodeSupplier = StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                                        errorMessage = "Failed to get connect host address by $appInstanceId",
                                    )
                                }
                            } ?: run {
                                createFailurePasteTaskResult(
                                    baseExtraInfo = baseExtraInfo,
                                    errorCodeSupplier = StandardErrorCode.PULL_ICON_TASK_FAIL,
                                    errorMessage = "Failed to sync paste to $appInstanceId",
                                )
                            }
                        } else {
                            SuccessPasteTaskResult()
                        }
                    }.getOrElse {
                        createFailurePasteTaskResult(
                            baseExtraInfo = baseExtraInfo,
                            errorCodeSupplier = StandardErrorCode.PULL_ICON_TASK_FAIL,
                            errorMessage = "Failed to pull icon $source, ${it.message}",
                        )
                    }
                }
            }
        } ?: SuccessPasteTaskResult()
    }

    private suspend fun pullIcon(
        source: String,
        iconPath: Path,
        host: String,
        port: Int,
        baseExtraInfo: BaseExtraInfo,
    ): PasteTaskResult {
        val toUrl: URLBuilder.() -> Unit = {
            buildUrl(host, port)
        }

        val result = pullClientApi.pullIcon(source, toUrl)
        return if (result is SuccessResult) {
            val byteReadChannel = result.getResult<ByteReadChannel>()
            fileUtils.writeFile(iconPath, byteReadChannel)

            logger.info { "Success to pull icon" }

            SuccessPasteTaskResult()
        } else {
            createFailurePasteTaskResult(
                baseExtraInfo = baseExtraInfo,
                errorMessage = "Failed to pull icon $source",
            )
        }
    }

    private fun createFailurePasteTaskResult(
        baseExtraInfo: BaseExtraInfo,
        errorCodeSupplier: ErrorCodeSupplier = StandardErrorCode.PULL_ICON_TASK_FAIL,
        errorMessage: String,
    ): FailurePasteTaskResult {
        return TaskUtils.createFailurePasteTaskResult(
            logger = logger,
            retryHandler = { baseExtraInfo.executionHistories.size < 2 },
            startTime = nowEpochMilliseconds(),
            fails =
                listOf(
                    createFailureResult(
                        errorCodeSupplier,
                        errorMessage,
                    ),
                ),
            extraInfo = baseExtraInfo,
        )
    }
}
