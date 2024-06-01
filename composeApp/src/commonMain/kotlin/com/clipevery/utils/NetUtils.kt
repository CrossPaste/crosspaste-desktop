package com.clipevery.utils

expect fun getNetUtils(): NetUtils

interface NetUtils {

    fun getHostList(): List<String>

    fun getEn0IPAddress(): String?
}
