package com.clipevery.listener

import java.util.function.Consumer

data class ShortcutKeysCore(
    val eventConsumer: Consumer<Any>,
    val keys: Map<String, Array<String>>,
)
