package com.crosspaste.presist

import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.path.PathProvider
import java.nio.file.Path

object DesktopFilePersist : FilePersist {

    override val pathProvider: PathProvider = DesktopPathProvider

    override fun createOneFilePersist(path: Path): OneFilePersist {
        return DesktopOneFilePersist(path)
    }
}
