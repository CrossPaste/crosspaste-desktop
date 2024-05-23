package com.clipevery.path

import com.clipevery.utils.FileUtils
import com.clipevery.utils.getFileUtils
import java.nio.file.Path

object TestPathProvider : PathProvider {

    override val fileUtils: FileUtils = getFileUtils()

    override val userHome: Path by lazy { needMockUserHome() }

    override val clipAppPath: Path by lazy { needMockClipAppPath() }

    override val clipAppJarPath: Path by lazy { needMockClipAppJarPath() }

    override val clipUserPath: Path by lazy { needMockUserPath() }

    fun needMockClipAppPath(): Path {
        throw NotImplementedError("need mock for test")
    }

    fun needMockUserHome(): Path {
        throw NotImplementedError("need mock for test")
    }

    fun needMockClipAppJarPath(): Path {
        throw NotImplementedError("need mock for test")
    }

    fun needMockUserPath(): Path {
        throw NotImplementedError("need mock for test")
    }
}
