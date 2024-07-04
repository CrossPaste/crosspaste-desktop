package com.crosspaste.paste

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId

class DesktopPastePreviewService(
    private val pasteDao: PasteDao,
) : PastePreviewService {

    private var loadJob: Job? = null

    private val ioScope = CoroutineScope(ioDispatcher)

    private var limit: Int = 50

    private var existMore: Boolean = false

    override var refreshTime: Int = 0

    override val pasteDataList: MutableList<PasteData> = mutableStateListOf()

    override val pasteDataMap = mutableStateMapOf<ObjectId, PasteData>()

    override suspend fun loadPastePreviewList(
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
                    val list = pasteDao.getPasteData(limit = limit)
                    existMore = list.size == limit

                    val listFlow = list.asFlow()
                    listFlow.collect { changes: ResultsChange<PasteData> ->
                        withContext(mainDispatcher) {
                            pasteDataList.clear()
                            pasteDataList.addAll(changes.list)
                            refreshTime++
                        }
                    }
                }
        }
    }

    override suspend fun clearData() {
        loadJob?.cancel()
        pasteDataList.clear()
        existMore = false
        limit = 50
    }
}
