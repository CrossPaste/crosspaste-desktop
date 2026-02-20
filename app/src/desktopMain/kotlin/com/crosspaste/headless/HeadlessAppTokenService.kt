package com.crosspaste.headless

import com.crosspaste.app.AppTokenService
import io.github.oshai.kotlinlogging.KotlinLogging

class HeadlessAppTokenService : AppTokenService() {

    private val logger = KotlinLogging.logger {}

    override fun preShowToken() {
        logger.info { "Token: ${token.value.concatToString()}" }
    }
}
