package com.crosspaste.path

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.clear
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.clear
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasteDirectoryCleanupTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val fileUtils = getFileUtils()

    private fun provider(storage: File): UserDataPathProvider {
        val appConfig = mockk<AppConfig>()
        every { appConfig.useDefaultStoragePath } returns true
        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig
        val platformProvider = mockk<PlatformUserDataPathProvider>()
        every { platformProvider.getUserDefaultStoragePath() } returns storage.toOkioPath()
        return UserDataPathProvider(configManager, platformProvider)
    }

    private fun relativePath(
        id: Long,
        createTime: Long,
        fileName: String,
    ): String = fileUtils.createPasteRelativePath(PasteCoordinate(id, "app", createTime), fileName)

    private fun pasteData(
        id: Long,
        createTime: Long,
        relativePaths: List<String>,
    ): PasteData {
        val item =
            createFilesPasteItem(
                relativePathList = relativePaths,
                fileInfoTreeMap = relativePaths.associate { it.substringAfterLast('/') to SingleFileInfoTree(1L, "h") },
            )
        return PasteData(
            id = id,
            appInstanceId = "app",
            pasteAppearItem = item,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.FILE_TYPE.type,
            size = item.size,
            hash = item.hash,
            createTime = createTime,
            pasteState = PasteState.LOADED,
        )
    }

    private fun File.write(relativePath: String): File =
        resolve(relativePath).also {
            it.parentFile.mkdirs()
            it.writeText("x")
        }

    @Test
    fun `deleting a paste removes its whole directory under every per-paste root`(
        @TempDir storage: File,
    ) {
        val provider = provider(storage)
        val day = 1_700_000_000_000L
        val own = relativePath(7L, day, "a.txt")
        val ownFile = storage.resolve("files").write(own)
        // Left next to the file by older versions; not part of the record
        val thumbnail = storage.resolve("files").write(own.substringBeforeLast('/') + "/thumbnail_a.png")
        // The FILE-to-IMAGE move left this side's directory behind
        val imageSide = storage.resolve("images").write(relativePath(7L, day, "b.png"))
        val otherPaste = storage.resolve("files").write(relativePath(8L, day, "a.txt"))

        pasteData(7L, day, listOf(own)).clear(provider)

        assertFalse(ownFile.parentFile.exists())
        assertFalse(thumbnail.exists())
        assertFalse(imageSide.parentFile.exists())
        assertTrue(otherPaste.exists(), "another paste's directory must survive")
    }

    @Test
    fun `stored paths win when the paste's create time moved to another day`(
        @TempDir storage: File,
    ) {
        val provider = provider(storage)
        val firstDay = 1_700_000_000_000L
        val stored = relativePath(7L, firstDay, "a.txt")
        val file = storage.resolve("files").write(stored)

        pasteData(7L, firstDay + 3 * 86_400_000L, listOf(stored)).clear(provider)

        assertFalse(file.parentFile.exists())
    }

    @Test
    fun `placeholder row of a failed copy still reclaims the copied files`(
        @TempDir storage: File,
    ) {
        val provider = provider(storage)
        val day = 1_700_000_000_000L
        val copied = storage.resolve("files").write(relativePath(7L, day, "first-of-three.txt"))

        pasteData(7L, day, emptyList()).clear(provider)

        assertFalse(copied.exists())
    }

    @Test
    fun `dropping one item keeps the directory its siblings live in`(
        @TempDir storage: File,
    ) {
        val provider = provider(storage)
        val day = 1_700_000_000_000L
        val dropped = relativePath(7L, day, "dup.png")
        storage.resolve("files").write(dropped)
        val sibling = storage.resolve("files").write(relativePath(7L, day, "kept.png"))

        pasteData(7L, day, listOf(dropped)).pasteAppearItem!!.clear(userDataPathProvider = provider)

        assertTrue(sibling.exists())
    }
}
