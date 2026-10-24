package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopResourceUtilsTest {

    @Test
    fun loadOptionalPropertiesReturnsEmptyWhenResourceIsMissing() {
        val properties = DesktopResourceUtils.loadOptionalProperties("missing-for-test.properties")

        assertTrue(properties.isEmpty)
    }

    @Test
    fun loadOptionalPropertiesReadsExistingResource() {
        val properties = DesktopResourceUtils.loadOptionalProperties("crosspaste-version.properties")

        assertEquals(DesktopResourceUtils.loadProperties("crosspaste-version.properties"), properties)
    }
}
