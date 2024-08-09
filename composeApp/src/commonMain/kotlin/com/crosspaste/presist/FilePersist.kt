package com.crosspaste.presist

import okio.Path

interface FilePersist {

    fun createOneFilePersist(path: Path): OneFilePersist
}
