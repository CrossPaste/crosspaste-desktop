package com.clipevery.utils

import java.util.Properties

expect fun getResourceUtils(): ResourceUtils

interface ResourceUtils {

    fun loadProperties(fileName: String): Properties
}
