package com.crosspaste.app

interface AppInfoFactory {

    fun createAppInfo(): AppInfo

    fun getVersion(): String

    fun getRevision(): String

    fun getUserName(): String
}
