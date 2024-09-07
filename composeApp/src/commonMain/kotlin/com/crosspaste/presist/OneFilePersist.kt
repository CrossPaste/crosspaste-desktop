package com.crosspaste.presist

import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okio.FileSystem
import okio.Path
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class OneFilePersist(val path: Path) {

    private val jsonUtils = getJsonUtils()
    private val fileSystem = FileSystem.SYSTEM

    fun <T : Any> read(clazz: KClass<T>): T? {
        return if (fileSystem.exists(path)) {
            val content = fileSystem.read(path) { readUtf8() }
            val serializer = serializer(clazz.java) as KSerializer<T>
            jsonUtils.JSON.decodeFromString(serializer, content)
        } else {
            null
        }
    }

    fun readBytes(): ByteArray? {
        return if (fileSystem.exists(path)) {
            fileSystem.read(path) { readByteArray() }
        } else {
            null
        }
    }

    fun <T> save(config: T) {
        val kClass = config!!::class
        val serializer = serializer(kClass.java)
        val jsonString = jsonUtils.JSON.encodeToString(serializer, config)
        writeWithParentDirs { writeUtf8(jsonString) }
    }

    fun saveBytes(bytes: ByteArray) {
        writeWithParentDirs { write(bytes) }
    }

    fun delete(): Boolean {
        return if (fileSystem.exists(path)) {
            fileSystem.delete(path)
            true
        } else {
            false
        }
    }

    private fun writeWithParentDirs(writeOperation: okio.BufferedSink.() -> Unit) {
        val parent = path.parent
        if (parent != null && !fileSystem.exists(parent)) {
            fileSystem.createDirectories(parent)
        }
        fileSystem.write(path, mustCreate = false, writeOperation)
    }
}
