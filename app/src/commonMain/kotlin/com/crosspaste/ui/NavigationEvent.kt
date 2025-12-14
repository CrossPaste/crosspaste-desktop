package com.crosspaste.ui

interface NavigationEvent

data class Navigate(
    val route: Route,
) : NavigationEvent

data class NavigateAndClearStack(
    val route: Route,
) : NavigationEvent

object NavigateUp : NavigationEvent
