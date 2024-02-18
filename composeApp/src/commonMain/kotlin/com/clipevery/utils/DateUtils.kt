package com.clipevery.utils

import java.util.Calendar

object DateUtils {

    fun getPrevDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        return calendar.timeInMillis
    }
}