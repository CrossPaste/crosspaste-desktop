package com.crosspaste.paste

interface PasteboardMonitor {

    fun start()

    fun stop()

    fun toggle()

    /**
     * Application-exit variant of [stop]: additionally gives in-flight asynchronous
     * collection work a bounded chance to finish instead of cancelling it halfway.
     * Defaults to plain [stop] for implementations without such work.
     */
    suspend fun shutdown() {
        stop()
    }
}
