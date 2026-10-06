package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.isInside
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.getAppFileType
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.toPath

class UserDataPathProvider(
    private val configManager: CommonConfigManager,
    private val platformUserDataPathProvider: PlatformUserDataPathProvider,
) : PathProvider {

    private val logger = KotlinLogging.logger {}

    override val fileUtils: FileUtils = getFileUtils()

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path =
        resolve(fileName, appFileType, autoCreate = true) {
            getUserDataPath()
        }

    fun resolve(
        fileName: String? = null,
        appFileType: AppFileType,
        autoCreate: Boolean,
    ): Path =
        resolve(fileName, appFileType, autoCreate) {
            getUserDataPath()
        }

    fun resolve(
        fileName: String?,
        appFileType: AppFileType,
        getBasePath: () -> Path,
    ): Path = resolve(fileName, appFileType, autoCreate = true, getBasePath)

    fun resolve(
        fileName: String?,
        appFileType: AppFileType,
        autoCreate: Boolean,
        getBasePath: () -> Path,
    ): Path {
        val basePath = getBasePath()
        val path =
            when (appFileType) {
                AppFileType.FILE -> basePath.resolve("files")
                AppFileType.IMAGE -> basePath.resolve("images")
                AppFileType.DATA -> basePath.resolve("data")
                AppFileType.HTML -> basePath.resolve("html")
                AppFileType.OPEN_GRAPH -> basePath.resolve("opengraph")
                AppFileType.RTF -> basePath.resolve("rtf")
                AppFileType.ICON -> basePath.resolve("icons")
                AppFileType.FAVICON -> basePath.resolve("favicon")
                AppFileType.FILE_EXT_ICON -> basePath.resolve("file_ext_icons")
                AppFileType.VIDEO -> basePath.resolve("videos")
                AppFileType.TEMP -> basePath.resolve("temp")
                AppFileType.MARKETING -> basePath.resolve("marketing")
                else -> basePath
            }

        if (autoCreate) {
            autoCreateDir(path)
        }

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    fun cleanTemp() {
        runCatching {
            val tempPath = resolve(appFileType = AppFileType.TEMP)
            fileUtils.fileSystem.deleteRecursively(tempPath)
        }.onFailure { e ->
            logger.warn(e) { "Failed to clean temp directory" }
        }
    }

    fun resolve(
        appInstanceId: String,
        dateString: String,
        pasteId: Long,
        pasteFiles: PasteFiles,
        isPull: Boolean,
        filesIndexBuilder: FilesIndexBuilder?,
        resolveConflicts: Boolean = true,
    ): Map<String, String> {
        val renameMap = mutableMapOf<String, String>()
        val isDownloadDir = pasteFiles.basePath != null
        val basePath =
            pasteFiles.basePath?.toPath() ?: run {
                resolve(appFileType = pasteFiles.getAppFileType())
                    .resolveStorageComponent(appInstanceId)
                    .resolve(dateString)
                    .resolve(pasteId.toString())
            }

        if (isPull) {
            autoCreateDir(basePath)
        }

        val fileInfoTreeMap = pasteFiles.fileInfoTreeMap

        for (filePath in pasteFiles.getFilePaths(this)) {
            val originalName = filePath.name
            fileInfoTreeMap[originalName]?.let { fileInfoTree ->
                val resolvedName =
                    if (isPull && isDownloadDir && resolveConflicts) {
                        val resolved = fileUtils.resolveNonConflictFileName(basePath, originalName)
                        if (resolved != originalName) {
                            renameMap[originalName] = resolved
                        }
                        resolved
                    } else {
                        originalName
                    }
                resolveFileInfoTree(basePath, resolvedName, fileInfoTree, isPull, filesIndexBuilder)
            }
        }

        return renameMap
    }

    /**
     * Deletes the managed-storage directories a paste owns: `<appInstanceId>/<date>/<id>`
     * under each root that lays files out per paste. Paste ids are AUTOINCREMENT, so such
     * a directory never holds another paste's files. This reclaims what deleting the
     * listed files leaves behind: files of a copy that failed partway (its row still
     * holds placeholder items), the FILE-side directory of files moved to IMAGE, and
     * derived files older versions wrote next to an image.
     *
     * Only for deleting a whole paste: a plugin dropping one item must not take the
     * directory its sibling items live in.
     */
    fun deletePasteDirectories(pasteData: PasteData) {
        if (pasteData.id <= 0) return
        val id = pasteData.id.toString()
        val coordinateDir =
            fileUtils
                .createPasteRelativePath(pasteData.getPasteCoordinate(), "_")
                .toPath()
                .parent
        // The date in stored paths can differ from the coordinate's (updateCreateTime)
        val storedDirs =
            pasteData
                .getPasteAppearItems()
                .filterIsInstance<PasteFiles>()
                .filter { it.basePath == null }
                .flatMap { it.relativePathList }
                .map { it.toPath().segments }
                .filter { it.size > 3 && it[2] == id }
                .map { it.take(3).joinToString("/").toPath() }
        val pasteDirs = (listOfNotNull(coordinateDir) + storedDirs).distinct()
        for (appFileType in PASTE_DIRECTORY_FILE_TYPES) {
            val root = resolve(appFileType = appFileType, autoCreate = false)
            if (!fileUtils.existFile(root)) continue
            for (pasteDir in pasteDirs) {
                if (pasteDir.segments.any { it == ".." || it == "." }) continue
                val targetDir = root.resolve(pasteDir)
                if (!fileUtils.existFile(targetDir)) continue
                if (isInside(targetDir, root)) {
                    runCatching {
                        fileUtils.fileSystem.deleteRecursively(targetDir, mustExist = false)
                    }.onFailure { e ->
                        logger.warn(e) { "Failed to delete paste directory $pasteDir under $appFileType" }
                    }
                }
            }
        }
    }

    /**
     * Deletes the files a receive pre-allocated outside managed storage (the large-file
     * destination). The delete pipeline leaves files under a [PasteFiles.basePath]
     * alone, because a local paste uses one to reference files the user owns; a
     * received row only gets a basePath for slots CrossPaste created itself, so
     * whoever discards that row before it completes has to reclaim them.
     */
    fun deleteReceivedFilesOutsideStorage(pasteData: PasteData) {
        pasteData
            .getPasteAppearItems()
            .filterIsInstance<PasteFiles>()
            .filter { it.basePath != null }
            .flatMap { it.getFilePaths(this) }
            .distinct()
            .filter { fileUtils.existFile(it) }
            .forEach { path ->
                fileUtils.deleteFile(path).onFailure { e ->
                    logger.warn(e) { "Failed to delete received file outside storage: $path" }
                }
            }
    }

    fun validateReceivePaths(
        appInstanceId: String,
        pasteFiles: PasteFiles,
    ) {
        validateStorageComponent(appInstanceId)

        val pending = ArrayDeque<FileInfoTree>()
        pasteFiles.fileInfoTreeMap.forEach { (name, fileInfoTree) ->
            validateStorageComponent(name)
            pending.addLast(fileInfoTree)
        }
        while (pending.isNotEmpty()) {
            val fileInfoTree = pending.removeFirst()
            if (fileInfoTree is DirFileInfoTree) {
                fileInfoTree.iterator().forEach { (name, child) ->
                    validateStorageComponent(name)
                    pending.addLast(child)
                }
            }
        }
    }

    /**
     * Import archives are untrusted input: [PasteData.appInstanceId] and file names
     * become path components under managed storage, so they get the same component
     * rules as received pastes. File names are taken from relativePathList because
     * that is what import lays files out by, and it need not match fileInfoTreeMap keys.
     */
    fun validateImportPaths(pasteData: PasteData) {
        validateStorageComponent(pasteData.appInstanceId)
        pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>().forEach { pasteFiles ->
            validateReceivePaths(pasteData.appInstanceId, pasteFiles)
            pasteFiles.relativePathList.forEach { relativePath ->
                validateStorageComponent(relativePath.toPath().name)
            }
        }
    }

    private fun resolveFileInfoTree(
        basePath: Path,
        name: String,
        fileInfoTree: FileInfoTree,
        isPull: Boolean,
        filesIndexBuilder: FilesIndexBuilder?,
    ) {
        if (fileInfoTree.isFile()) {
            val filePath = basePath.resolveStorageComponent(name)
            if (isPull) {
                if (fileUtils.createEmptyPasteFile(filePath, fileInfoTree.size).isFailure) {
                    throw PasteException(
                        StandardErrorCode.CANT_CREATE_FILE.toErrorCode(),
                        "Failed to create file: $filePath",
                    )
                }
            }
            filesIndexBuilder?.addFile(filePath, fileInfoTree.size)
        } else {
            val dirPath = basePath.resolveStorageComponent(name)
            if (isPull) {
                autoCreateDir(dirPath)
            }
            val dirFileInfoTree = fileInfoTree as DirFileInfoTree
            dirFileInfoTree.iterator().forEach { (subName, subFileInfoTree) ->
                resolveFileInfoTree(dirPath, subName, subFileInfoTree, isPull, filesIndexBuilder)
            }
        }
    }

    fun resolveIconPath(
        appInstanceId: String,
        source: String,
    ): Path {
        require(isSafeIconComponent(appInstanceId)) { "Invalid appInstanceId: $appInstanceId" }
        require(isSafeIconComponent(source)) { "Invalid source: $source" }
        val iconDir = resolve(appFileType = AppFileType.ICON)
        val instanceDir = iconDir.resolve(appInstanceId)
        autoCreateDir(instanceDir)
        return instanceDir.resolve("$source.png")
    }

    fun findIconPath(
        appInstanceId: String?,
        source: String,
    ): Path? {
        if (!isSafeIconComponent(source)) return null
        val iconDir = resolve(appFileType = AppFileType.ICON)
        if (appInstanceId != null && isSafeIconComponent(appInstanceId)) {
            val instancePath = iconDir.resolve(appInstanceId).resolve("$source.png")
            if (fileUtils.existFile(instancePath)) return instancePath
        }
        val fallbackPath = iconDir.resolve("$source.png")
        return if (fileUtils.existFile(fallbackPath)) fallbackPath else null
    }

    private fun isSafeIconComponent(value: String): Boolean =
        value.isNotEmpty() &&
            !value.contains('/') &&
            !value.contains('\\') &&
            !value.contains("..")

    private fun Path.resolveStorageComponent(component: String): Path {
        validateStorageComponent(component)
        val normalizedBase = normalized()
        val resolved = normalizedBase.resolve(component, normalize = true)
        require(resolved.parent == normalizedBase) { "Storage path escaped its parent" }
        return resolved
    }

    private fun validateStorageComponent(component: String) {
        require(
            component.isNotEmpty() &&
                component != "." &&
                component != ".." &&
                component.none { char ->
                    char == '/' ||
                        char == '\\' ||
                        char == ':' ||
                        char.code == 0 ||
                        char.code in 1..31 ||
                        char.code == 127
                },
        ) { "Unsafe storage path component" }
    }

    fun getUserDataPath(): Path =
        if (configManager.getCurrentConfig().useDefaultStoragePath) {
            platformUserDataPathProvider.getUserDefaultStoragePath()
        } else {
            configManager.getCurrentConfig().storagePath.toPath(normalize = true)
        }

    private companion object {
        val PASTE_DIRECTORY_FILE_TYPES = listOf(AppFileType.FILE, AppFileType.IMAGE, AppFileType.OPEN_GRAPH)
    }
}
