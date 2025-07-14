package com.crosspaste.presist

import okio.Path

object FilePersist {

    fun createOneFilePersist(path: Path): OneFilePersist = OneFilePersist(path)
}
