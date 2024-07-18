package com.crosspaste.icon

import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createConcurrentPlatformMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ConcurrentLoaderTest {
    @Test
    fun testLoad() {
        val loader = TestConcurrentLoader()
        val key = "key"
        loader.load(key)
        assert(loader.saveKeys.contains(key))
    }

    @Test
    fun testConcurrentLoad() =
        runBlocking {
            val loader = TestConcurrentLoader()
            val key = "key"
            val threads = 4
            val jobs = mutableListOf<Job>()

            repeat(threads) {
                jobs.add(
                    launch {
                        loader.load(key)
                    },
                )
            }

            jobs.forEach { it.join() }

            assertTrue(loader.saveKeys.contains(key))
            assertEquals(1, loader.saveKeys.size)
        }
}

class TestConcurrentLoader : ConcurrentLoader<String, String> {
    override val lockMap: ConcurrentPlatformMap<String, PlatformLock> = createConcurrentPlatformMap()

    val saveKeys: MutableList<String> = mutableListOf()

    override fun resolve(key: String): String {
        return key
    }

    override fun exist(result: String): Boolean {
        return saveKeys.contains(result)
    }

    override fun save(
        key: String,
        result: String,
    ) {
        saveKeys.add(key)
    }

    override fun convertToKey(value: String): String {
        return value
    }

    override fun loggerWarning(
        value: String,
        e: Exception,
    ) {
        assertFails { throw e }
    }
}
