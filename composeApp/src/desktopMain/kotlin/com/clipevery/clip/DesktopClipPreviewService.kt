package com.clipevery.clip

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.utils.ioDispatcher
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mongodb.kbson.ObjectId

class DesktopClipPreviewService(
    private val clipDao: ClipDao,
) : ClipPreviewService {

    private var loadJob: Job? = null

    private val ioScope = CoroutineScope(ioDispatcher)

    private val mutex: Mutex = Mutex()

    private var limit: Int = 50

    private var existMore: Boolean = false

    override val clipDataList: MutableList<ClipData> = mutableStateListOf()

    override val clipDataMap = mutableStateMapOf<ObjectId, ClipData>()

    override suspend fun loadClipPreviewList(
        force: Boolean,
        toLoadMore: Boolean,
    ) {
        mutex.withLock {
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
                            mutex.withLock {
                                clipDataList.clear()
                                clipDataList.addAll(changes.list)
                            }
                        }
                    }
            }
        }
    }

    override suspend fun clearData() {
        mutex.withLock {
            loadJob?.cancel()
            clipDataList.clear()
            existMore = false
            limit = 50
        }
    }
}
