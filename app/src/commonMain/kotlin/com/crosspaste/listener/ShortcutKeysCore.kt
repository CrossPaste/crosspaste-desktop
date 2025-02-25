package com.crosspaste.listener

data class ShortcutKeysCore(
    val eventConsumer: EventConsumer<Any>,
    val keys: Map<String, List<KeyboardKey>>,
)

interface KeyboardKey {

    val name: String
    val code: Int
    val rawCode: Int
}

fun interface EventConsumer<T> {

    fun accept(event: T)
}
