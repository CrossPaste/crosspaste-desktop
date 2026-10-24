package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

        assertFalse(properties.isEmpty)
        assertNotNull(properties.getProperty("version"))
    }

    @Test
    fun loadPropertiesFailsWhenResourceIsMissing() {
        assertFailsWith<IllegalArgumentException> {
            DesktopResourceUtils.loadProperties("missing-for-test.properties")
        }
    }
}
