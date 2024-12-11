package com.crosspaste.app

interface AppLaunch {

    suspend fun launch(): AppLaunchState
}
