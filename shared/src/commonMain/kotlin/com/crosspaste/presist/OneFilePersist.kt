package com.crosspaste.presist

import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okio.BufferedSink
import okio.Path
import okio.Path.Companion.toPath
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class OneFilePersist(
    val path: Path,
) {

    private val jsonUtils = getJsonUtils()
    private val fileSystem = getFileUtils().fileSystem

    fun exists(): Boolean = fileSystem.exists(path)

    @OptIn(InternalSerializationApi::class)
    fun <T : Any> read(clazz: KClass<T>): T? =
        if (fileSystem.exists(path)) {
            val content = fileSystem.read(path) { readUtf8() }
            val serializer = clazz.serializer()
            jsonUtils.JSON.decodeFromString(serializer, content)
        } else {
            null
        }

    fun readBytes(): ByteArray? =
        if (fileSystem.exists(path)) {
            fileSystem.read(path) { readByteArray() }
        } else {
            null
        }

    /** @throws okio.IOException if the filesystem write fails (disk full, permission denied, etc.) */
    @OptIn(InternalSerializationApi::class)
    fun <T : Any> save(config: T) {
        val serializer: KSerializer<T> = config::class.serializer() as KSerializer<T>
        val jsonString = jsonUtils.JSON.encodeToString(serializer, config)
        writeWithParentDirs { writeUtf8(jsonString) }
    }

    /** @throws okio.IOException if the filesystem write fails (disk full, permission denied, etc.) */
    fun saveBytes(bytes: ByteArray) {
        writeWithParentDirs { write(bytes) }
    }

    /** @throws okio.IOException if deletion fails (e.g. permission denied). */
    fun delete(): Boolean =
        if (fileSystem.exists(path)) {
            fileSystem.delete(path)
            true
        } else {
            false
        }

    /**
     * Move the current file aside to `<name>.corrupt` in the same directory, so an
     * unreadable payload stays available for inspection and manual recovery instead
     * of being overwritten by regenerated content. Returns the backup path, or null
     * if there was nothing to move.
     *
     * @throws okio.IOException if the move fails.
     */
    fun quarantine(): Path? {
        if (!fileSystem.exists(path)) {
            return null
        }
        val target = "$path.corrupt".toPath()
        fileSystem.atomicMove(path, target)
        return target
    }

    private fun writeWithParentDirs(writeOperation: BufferedSink.() -> Unit) {
        val parent = path.parent
        if (parent != null && !fileSystem.exists(parent)) {
            fileSystem.createDirectories(parent)
        }
        // Write to a same-directory temp file, then atomically move it into place: a
        // crash or full disk mid-write must never leave a truncated file at [path]
        // (R2-02-006).
        val tempPath = "$path.tmp".toPath()
        try {
            fileSystem.write(tempPath, mustCreate = false, writeOperation)
            fileSystem.atomicMove(tempPath, path)
        } catch (e: Throwable) {
            runCatching { fileSystem.delete(tempPath, mustExist = false) }
            throw e
        }
    }
}
