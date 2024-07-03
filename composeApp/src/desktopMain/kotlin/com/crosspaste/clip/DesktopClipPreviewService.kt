package com.crosspaste.clip

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId

class DesktopClipPreviewService(
    private val clipDao: ClipDao,
) : ClipPreviewService {

    private var loadJob: Job? = null

    private val ioScope = CoroutineScope(ioDispatcher)

    private var limit: Int = 50

    private var existMore: Boolean = false

    override var refreshTime: Int = 0

    override val clipDataList: MutableList<ClipData> = mutableStateListOf()

    override val clipDataMap = mutableStateMapOf<ObjectId, ClipData>()

    override suspend fun loadClipPreviewList(
        force: Boolean,
        toLoadMore: Boolean,
    ) {
        if (force) {
            loadJob?.cancel()
        }

        if (toLoadMore && !existMore && !force) {
            return
        } else if (toLoadMore && existMore) {
            limit += 20
        }

        if (loadJob == null || !(loadJob!!.isActive)) {
            loadJob =
                ioScope.launch {
                    val list = clipDao.getClipData(limit = limit)
                    existMore = list.size == limit

                    val listFlow = list.asFlow()
                    listFlow.collect { changes: ResultsChange<ClipData> ->
                        withContext(mainDispatcher) {
                            clipDataList.clear()
                            clipDataList.addAll(changes.list)
                            refreshTime++
                        }
                    }
                }
        }
    }

    override suspend fun clearData() {
        loadJob?.cancel()
        clipDataList.clear()
        existMore = false
        limit = 50
    }
}
