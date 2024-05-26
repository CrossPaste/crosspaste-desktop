package com.clipevery.listener

import java.util.function.Consumer

data class ShortcutKeysCore(
    val eventConsumer: Consumer<Any>,
    val keys: Map<String, List<KeyboardKey>>,
)

interface KeyboardKey {

    val name: String
    val code: Int
    val rawCode: Int
}
