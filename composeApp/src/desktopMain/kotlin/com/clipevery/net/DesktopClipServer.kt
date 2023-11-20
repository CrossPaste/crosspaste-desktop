package com.clipevery.net

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.apibuilder.EndpointGroup
import io.javalin.compression.CompressionStrategy
import io.javalin.config.JavalinConfig
import io.javalin.http.ExceptionHandler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.util.function.Consumer


class DesktopClipServer: ClipServer {

    private val logger = KotlinLogging.logger {}


    private var server: Javalin? = null

    private fun config(): Consumer<JavalinConfig> {
        return Consumer<JavalinConfig> {
            it.jetty.server { Server(QueuedThreadPool(10)) }
            it.http.maxRequestSize = Long.MAX_VALUE
            it.http.defaultContentType = "application/json"
            it.http.generateEtags = true
            it.compression.custom(CompressionStrategy.GZIP)
        }
    }

    private fun endpoints(): EndpointGroup {
        return EndpointGroup {
        }
    }

    private fun exceptionHandler(): ExceptionHandler<in java.lang.Exception?> {
        return ExceptionHandler<java.lang.Exception?> { e, ctx -> logger.error(e) { "${ctx.path()} exception" } }
    }

    override fun start(): ClipServer {
        server = Javalin.create(config())
            .routes(endpoints())
            .exception(Exception::class.java, exceptionHandler())
            .start("0.0.0.0", 0)
        logger.info { "start server ${port()}" }
        return this
    }

    override fun stop() {
        server?.stop()
    }

    override fun port(): Int {
        return server?.port() ?: 0
    }
}