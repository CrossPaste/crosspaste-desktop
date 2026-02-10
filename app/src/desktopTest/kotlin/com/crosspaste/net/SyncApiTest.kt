package com.crosspaste.net

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncApiTest {

    @Test
    fun `compareVersion returns EQUAL_TO for same version`() {
        assertEquals(VersionRelation.EQUAL_TO, SyncApi.compareVersion(SyncApi.VERSION))
    }

    @Test
    fun `compareVersion returns LOWER_THAN when connected version is higher`() {
        assertEquals(VersionRelation.LOWER_THAN, SyncApi.compareVersion(SyncApi.VERSION + 1))
    }

    @Test
    fun `compareVersion returns HIGHER_THAN when connected version is lower`() {
        assertEquals(VersionRelation.HIGHER_THAN, SyncApi.compareVersion(SyncApi.VERSION - 1))
    }

    @Test
    fun `compareVersion returns HIGHER_THAN for zero`() {
        assertEquals(VersionRelation.HIGHER_THAN, SyncApi.compareVersion(0))
    }

    @Test
    fun `compareVersion returns LOWER_THAN for MAX_VALUE`() {
        assertEquals(VersionRelation.LOWER_THAN, SyncApi.compareVersion(Int.MAX_VALUE))
    }
}
