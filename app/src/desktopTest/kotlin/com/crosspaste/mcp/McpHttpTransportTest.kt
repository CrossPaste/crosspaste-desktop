package com.crosspaste.mcp

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class McpHttpTransportTest {

    @Test
    fun `streamable HTTP initializes a session alongside the legacy SSE route`() =
        testApplication {
            application {
                installCrossPasteMcp(
                    Server(
                        serverInfo = Implementation(name = "test-mcp-server", version = "1.0.0"),
                        options = ServerOptions(capabilities = ServerCapabilities()),
                    ),
                )
            }

            val initialized =
                client.post("/mcp") {
                    header(HttpHeaders.Host, "127.0.0.1")
                    header(HttpHeaders.Accept, "application/json, text/event-stream")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "method": "initialize",
                          "params": {
                            "protocolVersion": "2025-11-25",
                            "capabilities": {},
                            "clientInfo": {"name": "test-client", "version": "1.0.0"}
                          }
                        }
                        """.trimIndent(),
                    )
                }
            val response = initialized.bodyAsText()
            assertEquals(HttpStatusCode.OK, initialized.status, response)
            assertNotNull(initialized.headers["Mcp-Session-Id"])
            val result =
                Json
                    .parseToJsonElement(response)
                    .jsonObject
                    .getValue("result")
                    .jsonObject
            assertEquals(
                "test-mcp-server",
                result
                    .getValue("serverInfo")
                    .jsonObject
                    .getValue("name")
                    .jsonPrimitive.content,
            )

            val legacy =
                client.post("/?sessionId=expired") {
                    header(HttpHeaders.Host, "127.0.0.1")
                }
            assertEquals(HttpStatusCode.NotFound, legacy.status)
            assertEquals("Session not found", legacy.bodyAsText())

            val rejected =
                client.post("/mcp") {
                    header(HttpHeaders.Host, "127.0.0.1")
                    header(HttpHeaders.Origin, "https://example.com")
                }
            assertEquals(HttpStatusCode.Forbidden, rejected.status)
        }
}
