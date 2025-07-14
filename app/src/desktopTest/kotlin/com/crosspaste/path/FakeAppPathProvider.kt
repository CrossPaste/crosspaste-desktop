package com.crosspaste.path

import com.crosspaste.app.AppFileType
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files

class FakeAppPathProvider : AppPathProvider {

    val tempPasteAppPath = Files.createTempDirectory("tempPasteAppPath").toOkioPath()
    val tempUserHome = Files.createTempDirectory("tempUserHome").toOkioPath()
    val tempPasteAppJarPath = Files.createTempDirectory("tempPasteAppJarPath").toOkioPath()
    val tempPasteAppExePath = Files.createTempDirectory("tempPasteAppExePath").toOkioPath()
    val tempUserPath = Files.createTempDirectory("tempUserPath").toOkioPath()

    override val userHome: Path = tempUserHome
    override val pasteAppPath: Path = tempPasteAppPath
    override val pasteAppJarPath: Path = tempPasteAppJarPath
    override val pasteAppExePath: Path = tempPasteAppExePath
    override val pasteUserPath: Path = tempUserPath

    private val desktopPathProvider = DesktopPathProvider(tempPasteAppPath, tempUserPath)

    init {
        tempPasteAppPath.toFile().deleteOnExit()
        tempUserHome.toFile().deleteOnExit()
        tempPasteAppJarPath.toFile().deleteOnExit()
        tempUserPath.toFile().deleteOnExit()
    }

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path = desktopPathProvider.resolve(fileName, appFileType)
}
