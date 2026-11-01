package com.crosspaste.path

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class StoredPathTest {

    private val oldHome = "/var/mobile/Containers/Data/Application/1111".toPath()
    private val newHome = "/var/mobile/Containers/Data/Application/2222".toPath()

    @Test
    fun pathUnderAppHomeIsStoredRelativeToIt() {
        assertEquals("{appHome}/Documents", encodeStoredPath("$oldHome/Documents", oldHome))
        assertEquals("{appHome}", encodeStoredPath(oldHome.toString(), oldHome))
    }

    @Test
    fun storedPathFollowsTheAppHomeOfThisLaunch() {
        val stored = encodeStoredPath("$oldHome/Documents/sub", oldHome)
        assertEquals("$newHome/Documents/sub".toPath(), decodeStoredPath(stored, newHome))
        assertEquals(newHome, decodeStoredPath("{appHome}", newHome))
    }

    @Test
    fun pathsOutsideAppHomeStayAbsolute() {
        assertEquals("/Users/a/Downloads", encodeStoredPath("/Users/a/Downloads", oldHome))
        assertEquals("/var/mobile/Containers/Data/Application/11110", encodeStoredPath("${oldHome}0", oldHome))
    }

    @Test
    fun withoutRelocatableAppHomePathsAreStoredAndReadAsIs() {
        assertEquals("/Users/a/Downloads", encodeStoredPath("/Users/a/Downloads", null))
        assertEquals("/Users/a/Downloads".toPath(), decodeStoredPath("/Users/a/Downloads", null))
        assertEquals("/Users/a/Downloads".toPath(), decodeStoredPath("/Users/a/Downloads", newHome))
    }
}
