package com.crosspaste.clip

import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.crosspaste.dao.clip.ClipData
import org.mongodb.kbson.ObjectId

interface ClipPreviewService {

    var refreshTime: Int

    val clipDataList: MutableList<ClipData>

    val clipDataMap: SnapshotStateMap<ObjectId, ClipData>

    suspend fun loadClipPreviewList(
        force: Boolean,
        toLoadMore: Boolean = false,
    )

    suspend fun clearData()
}
