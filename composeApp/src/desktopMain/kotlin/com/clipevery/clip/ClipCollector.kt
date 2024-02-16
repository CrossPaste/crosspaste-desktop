package com.clipevery.clip

import com.clipevery.app.AppInfo
import com.clipevery.dao.clip.ClipDao

class ClipCollector(
    appInfo: AppInfo,
    clipDao: ClipDao,
    singleClipPlugins: List<SingleClipPlugin>,
    multiClipPlugins: List<MultiClipPlugin>
) {
    fun needCollectionItem(): Boolean {
        TODO("Not yet implemented")
    }

    fun completeCollect() {
        TODO("Not yet implemented")
    }
}