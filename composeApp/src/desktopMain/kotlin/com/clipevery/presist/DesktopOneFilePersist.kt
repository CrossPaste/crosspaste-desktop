package com.clipevery.presist

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.nio.file.Path
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
class DesktopOneFilePersist(private val path: Path) : OneFilePersist {
    override fun <T: Any> readAs(clazz: KClass<T>): T? {
        val file = path.toFile()
        return if (file.exists()) {
            val serializer = Json.serializersModule.serializer(clazz.java)
            Json.decodeFromString(serializer, file.readText()) as T
        } else {
            null
        }
    }

    override fun <T> save(config: T) {
        val kClass = config!!::class
        val serializer = Json.serializersModule.serializer(kClass.java)
        val json = Json.encodeToString(serializer, config)
        val file = path.toFile()
        file.parentFile?.mkdirs()
        file.writeText(json)
    }
}