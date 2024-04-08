package com.clipevery.presist

import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import java.nio.file.Path

object DesktopFilePersist : FilePersist {

    override val pathProvider: PathProvider = DesktopPathProvider

    override fun createOneFilePersist(path: Path): OneFilePersist {
        return DesktopOneFilePersist(path)
    }
}
