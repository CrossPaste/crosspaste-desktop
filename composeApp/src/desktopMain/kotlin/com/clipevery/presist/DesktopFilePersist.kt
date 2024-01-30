package com.clipevery.presist

import com.clipevery.path.PathProvider
import com.clipevery.path.getPathProvider
import java.nio.file.Path

fun getFilePersist(): FilePersist {
    return DesktopFilePersist()
}

class DesktopFilePersist: FilePersist {

    override val pathProvider: PathProvider = getPathProvider()
    override fun createOneFilePersist(path: Path): OneFilePersist {
        return DesktopOneFilePersist(path)
    }
}