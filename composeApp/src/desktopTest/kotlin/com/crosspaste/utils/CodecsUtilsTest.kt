package com.crosspaste.utils

import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecsUtilsTest {

    val codecsUtils = getCodecsUtils()

    @Test
    fun testHash() {
        val hash = codecsUtils.hash("test".toByteArray())
        assertEquals("2e97157971471d75", hash)
        val hashStr = codecsUtils.hashByString("test")
        assertEquals("2e97157971471d75", hashStr)
        val hashArray = codecsUtils.hashByArray(arrayOf("test"))
        assertEquals("2e97157971471d75", hashArray)
    }
}
