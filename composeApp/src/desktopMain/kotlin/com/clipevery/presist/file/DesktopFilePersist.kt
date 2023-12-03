package com.clipevery.presist.file

import com.clipevery.path.PathProvider
import com.clipevery.path.getPathProvider
import com.clipevery.presist.FilePersist
import com.clipevery.presist.OneFilePersist
import java.nio.file.Path

class DesktopFilePersist: FilePersist {

    override val pathProvider: PathProvider = getPathProvider()
    override fun createOneFilePersist(path: Path): OneFilePersist {
        return DesktopOneFilePersist(path)
    }
}