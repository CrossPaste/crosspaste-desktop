package com.crosspaste.presist

import okio.Path

object DesktopFilePersist : FilePersist {

    override fun createOneFilePersist(path: Path): OneFilePersist {
        return DesktopOneFilePersist(path)
    }
}
