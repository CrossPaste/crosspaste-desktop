package com.clipevery.presist

import kotlin.reflect.KClass

interface OneFilePersist {
    fun <T : Any> readAs(clazz: KClass<T>): T?
    fun <T> save(config: T)
}