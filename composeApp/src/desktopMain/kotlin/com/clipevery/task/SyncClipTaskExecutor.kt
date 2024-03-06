package com.clipevery.task

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.utils.JsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString

class SyncClipTaskExecutor(private val lazyClipDao: Lazy<ClipDao>): SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val clipDao: ClipDao by lazy { lazyClipDao.value }

    override fun executeTask(clipTask: ClipTask) {
        clipDao.getClipData(clipTask.clipId)?.let {
            logger.info { "print clipData\n${JsonUtils.JSON.encodeToString(it)}" }
        } ?: run {
            logger.info { "ClipData not found for clipId: ${clipTask.clipId}" }
        }
    }

    override fun needRetry(clipTask: ClipTask): Boolean {
        return true
    }
}