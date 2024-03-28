package com.clipevery.clip

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.task.TaskExecutor
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.Transferable

interface ClipboardService : ClipboardMonitor, ClipboardOwner {

    var owner: Boolean

    var ownerTransferable: Transferable?

    val systemClipboard: Clipboard

    val clipDao: ClipDao

    val clipConsumer: TransferableConsumer

    val clipProducer: TransferableProducer

    val taskExecutor: TaskExecutor

    fun tryWriteClipboard(clipData: ClipData, localOnly: Boolean = false, filterFile: Boolean = false) {
        ownerTransferable = clipProducer.produce(clipData, localOnly, filterFile)
        owner = true
        systemClipboard.setContents(ownerTransferable, this)
    }

    suspend fun tryWriteRemoteClipboard(clipData: ClipData) {
        val taskIds = clipDao.releaseRemoteClipData(clipData) { storeClipData, filterFile ->
            tryWriteClipboard(storeClipData, localOnly = true, filterFile)
        }
        if (taskIds.isNotEmpty()) {
            taskExecutor.submitTasks(taskIds)
        }
    }

}