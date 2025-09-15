package com.crosspaste.ui

import androidx.navigation.NavHostController

interface NavigationManager {

    fun navigate(route: Route)

    fun navigateAndClearStack(route: Route)

    fun navigateUp()

    fun navigateAction(
        event: NavigationEvent,
        navController: NavHostController,
    )
}
