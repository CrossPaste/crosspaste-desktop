package com.crosspaste.net

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkInterfaceLogDedupTest {

    private val logger = LoggerFactory.getLogger(AbstractNetworkInterfaceService::class.java.name) as Logger

    private val appender = ListAppender<ILoggingEvent>()

    private val service =
        object : AbstractNetworkInterfaceService() {
            override val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>> = MutableStateFlow(emptyList())
        }

    @BeforeTest
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun unchangedInterfacesAreLoggedOnlyOnce() {
        service.getAllNetworkInterfaceInfo()
        val firstCount = appender.list.size
        // Every machine has a loopback interface, which is at least reported as skipped.
        assertTrue(firstCount > 0)

        service.getAllNetworkInterfaceInfo()

        assertEquals(firstCount, appender.list.size)
    }
}
