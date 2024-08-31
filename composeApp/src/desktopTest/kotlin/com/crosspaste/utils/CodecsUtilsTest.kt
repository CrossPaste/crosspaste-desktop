package com.crosspaste.utils

import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecsUtilsTest {

    val codecsUtils = getCodecsUtils()

    @Test
    fun testHash() {
        val hash = codecsUtils.hash("test".toByteArray())
        assertEquals("2e9715792ae84f8c71471d75ace36b46", hash)
        val hashStr = codecsUtils.hashByString("test")
        assertEquals("2e9715792ae84f8c71471d75ace36b46", hashStr)
        val hashArray = codecsUtils.hashByArray(arrayOf("test"))
        assertEquals("2e9715792ae84f8c71471d75ace36b46", hashArray)
    }
}
