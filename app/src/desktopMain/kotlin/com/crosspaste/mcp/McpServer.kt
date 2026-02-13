package com.crosspaste.mcp

interface McpServer {

    suspend fun start()

    suspend fun stop()

    suspend fun restart(newPort: Int)

    fun port(): Int
}
