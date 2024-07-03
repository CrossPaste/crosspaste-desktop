package com.crosspaste.app

interface AppStartUpService {

    fun followConfig()

    fun isAutoStartUp(): Boolean

    fun makeAutoStatUp()

    fun removeAutoStartUp()
}
