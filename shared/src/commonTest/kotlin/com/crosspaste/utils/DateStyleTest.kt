package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertTrue

class DateStyleTest {

    @Test
    fun testToPattern() {
        val dateUtils = getDateUtils()
        val locales = listOf("en", "zh", "zh_hant", "ja", "ko", "es", "de", "fr", "fa")
        val dateStyles = DateStyle.entries.toTypedArray()
        for (locale in locales) {
            for (dateStyle in dateStyles) {
                assertTrue {
                    runCatching {
                        dateUtils.getDateDesc(dateUtils.now(), DateTimeFormatOptions(dateStyle), locale)
                    }.onFailure { e ->
                        println("Failed to format date with locale=$locale, dateStyle=$dateStyle: ${e.message}")
                    }.isSuccess
                }
            }
        }
    }
}
