package com.crosspaste.path

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class AppContainerPathTest {

    private val oldDocuments =
        "/var/mobile/Containers/Data/Application/11111111-2222-3333-4444-555555555555/Documents"
    private val currentDocuments =
        "/private/var/mobile/Containers/Data/Application/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE/Documents"

    @Test
    fun movesStoredPathIntoCurrentContainer() {
        assertEquals(
            currentDocuments.toPath(),
            relocateToAppContainer(oldDocuments.toPath(), currentDocuments.toPath()),
        )
        assertEquals(
            "/private/var/mobile/Containers/Data/Application/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE/Documents/sub/a.zip"
                .toPath(),
            relocateToAppContainer("$oldDocuments/sub/a.zip".toPath(), currentDocuments.toPath()),
        )
    }

    @Test
    fun relocatesSimulatorContainers() {
        val device = "/Users/me/Library/Developer/CoreSimulator/Devices/99999999-8888-7777-6666-555555555555/data"
        val stored = "$device/Containers/Data/Application/11111111-2222-3333-4444-555555555555/Documents"
        val current = "$device/Containers/Data/Application/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE/Documents"
        assertEquals(current.toPath(), relocateToAppContainer(stored.toPath(), current.toPath()))
    }

    @Test
    fun leavesPathsOutsideAnAppContainerUnchanged() {
        val downloads = "/Users/me/Downloads".toPath()
        assertEquals(downloads, relocateToAppContainer(downloads, currentDocuments.toPath()))
        assertEquals(oldDocuments.toPath(), relocateToAppContainer(oldDocuments.toPath(), downloads))
        val macContainer = "/Users/me/Library/Containers/com.crosspaste.ios/Data/Documents".toPath()
        assertEquals(macContainer, relocateToAppContainer(macContainer, currentDocuments.toPath()))
    }

    @Test
    fun keepsStoredPathWhenItIsNotUnderAnAppContainer() {
        val desktopPath = "/home/me/Downloads/file.bin".toPath()
        assertEquals(desktopPath, relocateToAppContainer(desktopPath))
    }
}
