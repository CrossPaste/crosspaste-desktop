package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao

abstract class InitPasteDataService(
    protected val pasteDao: PasteDao,
) {

    abstract fun isFirstLaunch(): Boolean

    abstract fun saveData()

    fun initData() {
        if (isFirstLaunch()) {
            if (pasteDao.getSize(allOrFavorite = true) == 0L) {
                saveData()
            }
        }
    }
}
