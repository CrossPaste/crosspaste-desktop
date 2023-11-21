package com.clipevery.presist

import kotlin.reflect.KClass

interface OneFilePersist {
    fun <T : Any> read(clazz: KClass<T>): T?

    fun read(): ByteArray?

    fun <T> save(config: T)

    fun save(bytes: ByteArray)
}