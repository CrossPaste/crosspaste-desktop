package com.clipevery.presist

import kotlin.reflect.KClass

interface OneFilePersist {
    fun <T : Any> read(clazz: KClass<T>): T?

    fun readBytes(): ByteArray?

    fun <T> save(config: T)

    fun saveBytes(bytes: ByteArray)
}