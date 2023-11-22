package com.clipevery

import com.clipevery.model.AppInfo

interface AppInfoFactory {
    fun createAppInfo(): AppInfo
}