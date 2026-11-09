package com.crosspaste.net

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        // Every machine has a loopback interface, which is at least reported as skipped;
        // a sandbox that enumerates nothing cannot exercise the dedup.
        assumeTrue(firstCount > 0)

        service.getAllNetworkInterfaceInfo()

        assertEquals(firstCount, appender.list.size)
    }

    @Test
    fun changedSnapshotIsLoggedInFullAndRepeatedSnapshotIsNot() {
        val before = listOf("Skipped 1 network interfaces: lo0", "Local address found: en0 192.168.0.108")
        val after = listOf("Skipped 1 network interfaces: lo0", "Local address found: en0 192.168.0.109")

        service.logIfChanged(before)
        service.logIfChanged(before)
        assertEquals(before, messages())

        service.logIfChanged(after)
        service.logIfChanged(after)
        assertEquals(before + after, messages())

        // Changing back is a change too.
        service.logIfChanged(before)
        assertEquals(before + after + before, messages())
    }

    private fun messages(): List<String> = appender.list.map { it.formattedMessage }
}
