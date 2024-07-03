package com.crosspaste.presist

import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.ioDispatcher
import io.ktor.utils.io.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class DesktopOneFilePersist(override val path: Path) : OneFilePersist {
    override fun <T : Any> read(clazz: KClass<T>): T? {
        val file = path.toFile()
        return if (file.exists()) {
            val serializer = Json.serializersModule.serializer(clazz.java)
            DesktopJsonUtils.JSON.decodeFromString(serializer, file.readText()) as T
        } else {
            null
        }
    }

    override fun readBytes(): ByteArray? {
        val file = path.toFile()
        return if (file.exists()) {
            file.readBytes()
        } else {
            null
        }
    }

    override fun <T> save(config: T) {
        val kClass = config!!::class
        val serializer = Json.serializersModule.serializer(kClass.java)
        val json = DesktopJsonUtils.JSON.encodeToString(serializer, config)
        val file = path.toFile()
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    override fun saveBytes(bytes: ByteArray) {
        val file = path.toFile()
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override fun delete(): Boolean {
        val file = path.toFile()
        if (file.exists()) {
            return file.delete()
        }
        return false
    }

    override suspend fun writeChannel(channel: ByteReadChannel) {
        withContext(ioDispatcher) {
            val fileChannel = Files.newByteChannel(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val buffer = ByteArray(4096)
            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead <= 0) {
                    continue
                }
                fileChannel.write(java.nio.ByteBuffer.wrap(buffer, 0, bytesRead))
            }
            fileChannel.close()
        }
    }
}
