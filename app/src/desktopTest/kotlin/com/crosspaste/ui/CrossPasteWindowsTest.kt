package com.crosspaste.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CrossPasteWindowsTest {

    @Test
    fun firstLaunch_showsMainWindowBeforeMarkingLaunchCompleted() {
        val actions = mutableListOf<String>()

        handleFirstLaunch(
            firstLaunch = true,
            firstLaunchCompleted = false,
            showMainWindow = { actions += "show" },
            markFirstLaunchCompleted = { actions += "complete" },
        )

        assertEquals(listOf("show", "complete"), actions)
    }

    @Test
    fun nonFirstLaunch_keepsMainWindowHidden() {
        val actions = mutableListOf<String>()

        handleFirstLaunch(
            firstLaunch = false,
            firstLaunchCompleted = false,
            showMainWindow = { actions += "show" },
            markFirstLaunchCompleted = { actions += "complete" },
        )

        assertEquals(emptyList(), actions)
    }

    @Test
    fun completedFirstLaunch_doesNotShowMainWindowAgain() {
        val actions = mutableListOf<String>()

        handleFirstLaunch(
            firstLaunch = true,
            firstLaunchCompleted = true,
            showMainWindow = { actions += "show" },
            markFirstLaunchCompleted = { actions += "complete" },
        )

        assertEquals(emptyList(), actions)
    }
}
