package com.clipevery.net

import com.clipevery.encrypt.SignalProtocol
import com.clipevery.model.AppHostInfo
import com.clipevery.model.AppRequestBindInfo
import com.clipevery.platform.currentPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.NetworkInterface
import java.util.Collections


class DesktopClipServer(private val signalProtocol: SignalProtocol): ClipServer {

    private val logger = KotlinLogging.logger {}

    private var port = 0


    private var server: NettyApplicationEngine = embeddedServer(Netty, port = 0) {
        routing {
            get("/") {
                call.respondText("Hello, world!")
            }
        }
    }

    override fun start(): ClipServer {
        server.start(wait = false)
        port = runBlocking { server.resolvedConnectors().first().port }
        if (port == 0) {
            logger.error { "Failed to start server" }
        } else {
            logger.info { "Server started at port $port" }
        }
        return this
    }

    override fun stop() {
        server.stop()
    }

    override fun port(): Int {
        return port
    }

    private fun getHostInfoList(): List<AppHostInfo> {
        val nets = NetworkInterface.getNetworkInterfaces()

        return buildList {
            for (netInterface in Collections.list(nets)) {
                val inetAddresses = netInterface.inetAddresses
                for (inetAddress in Collections.list(inetAddresses)) {
                    if (inetAddress.isSiteLocalAddress) {
                        add(AppHostInfo(displayName = netInterface.displayName,
                            hostAddress = inetAddress.hostAddress))
                    }
                }
            }
        }
    }

    override fun appRequestBindInfo(): AppRequestBindInfo {
        return AppRequestBindInfo(
            platform = currentPlatform().name,
            publicKey = signalProtocol.identityKeyPair.publicKey,
            port = port(),
            hostInfoList = getHostInfoList())
    }
}