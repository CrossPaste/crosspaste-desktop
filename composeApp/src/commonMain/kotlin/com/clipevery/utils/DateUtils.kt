package com.clipevery.utils

import io.realm.kotlin.types.RealmInstant
import java.util.Calendar

object DateUtils {

    fun getPrevDay(): RealmInstant {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        // Convert milliseconds to seconds for the epochSeconds parameter
        val epochSeconds = calendar.timeInMillis / 1000
        // The nanosecondAdjustment parameter is 0 as we're not adjusting nanoseconds here
        return RealmInstant.from(epochSeconds, 0)
    }
}