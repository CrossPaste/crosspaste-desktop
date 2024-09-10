package com.crosspaste.image

import com.crosspaste.utils.PlatformLock
import io.ktor.util.collections.*
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
    override val lockMap: ConcurrentMap<String, PlatformLock> = ConcurrentMap()

    val saveKeys: MutableList<String> = mutableListOf()

    override fun resolve(
        key: String,
        value: String,
    ): String {
        return key
    }

    override fun exist(result: String): Boolean {
        return saveKeys.contains(result)
    }

    override fun save(
        key: String,
        value: String,
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
