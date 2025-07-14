package com.crosspaste.net

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.netty.channel.ChannelOption

class DesktopServerFactory : ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    override fun getFactory(): ApplicationEngineFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        Netty

    override fun getConfigure(): NettyApplicationEngine.Configuration.() -> Unit =
        {
            configureBootstrap = {
                childOption(ChannelOption.TCP_NODELAY, true)
                childOption(ChannelOption.SO_KEEPALIVE, true)
            }
        }
}
