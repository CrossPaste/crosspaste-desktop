package com.clipevery.net

interface ClientHandlerManager {
    fun start()
    fun addHandler(id: String)
    fun removeHandler(id: String)
    fun stop()
    suspend fun checkConnects(checkAction: CheckAction)
    suspend fun checkConnect(id: String, checkAction: CheckAction): Boolean
}
