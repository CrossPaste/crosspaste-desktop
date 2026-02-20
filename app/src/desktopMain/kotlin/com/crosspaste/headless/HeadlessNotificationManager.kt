package com.crosspaste.headless

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.Message
import com.crosspaste.notification.NotificationManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger

class HeadlessNotificationManager(
    copywriter: GlobalCopywriter,
) : NotificationManager(copywriter) {

    private val logger = KotlinLogging.logger {}

    private val idGenerator = AtomicInteger(0)

    override fun getMessageId(): Int = idGenerator.incrementAndGet()

    override fun doSendNotification(message: Message) {
        logger.info { "[${message.messageType}] ${message.title}${message.message?.let { " - $it" } ?: ""}" }
    }
}
