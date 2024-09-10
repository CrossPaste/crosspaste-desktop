package com.crosspaste.paste

import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.crosspaste.realm.paste.PasteData
import org.mongodb.kbson.ObjectId

interface PastePreviewService {

    var refreshTime: Int

    val pasteDataList: MutableList<PasteData>

    val pasteDataMap: SnapshotStateMap<ObjectId, PasteData>

    suspend fun loadPastePreviewList(
        force: Boolean,
        toLoadMore: Boolean = false,
    )

    suspend fun clearData()
}
