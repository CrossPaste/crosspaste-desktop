package com.crosspaste.mcp

interface McpServer {

    suspend fun start()

    suspend fun stop()

    fun port(): Int
}
