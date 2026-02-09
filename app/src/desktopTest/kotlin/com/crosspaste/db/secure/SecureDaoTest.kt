package com.crosspaste.db.secure

import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureDaoTest {

    private val database = createDatabase(TestDriverFactory())
    private val secureDao = SecureDao(database)

    // --- Save and retrieve ---

    @Test
    fun `saveCryptPublicKey and retrieve with serializedPublicKey`() = runTest {
        val appInstanceId = "test-app-1"
        val keyData = byteArrayOf(1, 2, 3, 4, 5)

        secureDao.saveCryptPublicKey(appInstanceId, keyData)
        val retrieved = secureDao.serializedPublicKey(appInstanceId)

        assertNotNull(retrieved)
        assertContentEquals(keyData, retrieved)
    }

    @Test
    fun `saveCryptPublicKey with empty byte array`() = runTest {
        val appInstanceId = "test-empty"
        val keyData = byteArrayOf()

        secureDao.saveCryptPublicKey(appInstanceId, keyData)
        val retrieved = secureDao.serializedPublicKey(appInstanceId)

        assertNotNull(retrieved)
        assertContentEquals(keyData, retrieved)
    }

    @Test
    fun `saveCryptPublicKey with large byte array`() = runTest {
        val appInstanceId = "test-large"
        val keyData = ByteArray(4096) { it.toByte() }

        secureDao.saveCryptPublicKey(appInstanceId, keyData)
        val retrieved = secureDao.serializedPublicKey(appInstanceId)

        assertNotNull(retrieved)
        assertContentEquals(keyData, retrieved)
    }

    @Test
    fun `saveCryptPublicKey overwrites existing key`() = runTest {
        val appInstanceId = "test-overwrite"
        val originalKey = byteArrayOf(1, 2, 3)
        val updatedKey = byteArrayOf(4, 5, 6, 7)

        secureDao.saveCryptPublicKey(appInstanceId, originalKey)
        secureDao.saveCryptPublicKey(appInstanceId, updatedKey)
        val retrieved = secureDao.serializedPublicKey(appInstanceId)

        assertNotNull(retrieved)
        assertContentEquals(updatedKey, retrieved)
    }

    // --- Existence check ---

    @Test
    fun `existCryptPublicKey returns true for existing key`() = runTest {
        val appInstanceId = "test-exists"
        secureDao.saveCryptPublicKey(appInstanceId, byteArrayOf(1, 2, 3))

        assertTrue(secureDao.existCryptPublicKey(appInstanceId))
    }

    @Test
    fun `existCryptPublicKey returns false for non-existent key`() = runTest {
        assertFalse(secureDao.existCryptPublicKey("non-existent-id"))
    }

    // --- Delete ---

    @Test
    fun `deleteCryptPublicKey removes stored key`() = runTest {
        val appInstanceId = "test-delete"
        secureDao.saveCryptPublicKey(appInstanceId, byteArrayOf(1, 2, 3))
        assertTrue(secureDao.existCryptPublicKey(appInstanceId))

        secureDao.deleteCryptPublicKey(appInstanceId)

        assertFalse(secureDao.existCryptPublicKey(appInstanceId))
        assertNull(secureDao.serializedPublicKey(appInstanceId))
    }

    @Test
    fun `deleteCryptPublicKey on non-existent key does not throw`() = runTest {
        secureDao.deleteCryptPublicKey("non-existent-id")
        // Should not throw
    }

    // --- Retrieval ---

    @Test
    fun `serializedPublicKey returns null for non-existent key`() = runTest {
        assertNull(secureDao.serializedPublicKey("non-existent-id"))
    }

    // --- Multiple keys ---

    @Test
    fun `store and retrieve multiple keys independently`() = runTest {
        val key1 = byteArrayOf(1, 1, 1)
        val key2 = byteArrayOf(2, 2, 2)
        val key3 = byteArrayOf(3, 3, 3)

        secureDao.saveCryptPublicKey("app-1", key1)
        secureDao.saveCryptPublicKey("app-2", key2)
        secureDao.saveCryptPublicKey("app-3", key3)

        assertContentEquals(key1, secureDao.serializedPublicKey("app-1"))
        assertContentEquals(key2, secureDao.serializedPublicKey("app-2"))
        assertContentEquals(key3, secureDao.serializedPublicKey("app-3"))
    }

    @Test
    fun `delete one key does not affect other keys`() = runTest {
        secureDao.saveCryptPublicKey("app-a", byteArrayOf(10))
        secureDao.saveCryptPublicKey("app-b", byteArrayOf(20))

        secureDao.deleteCryptPublicKey("app-a")

        assertFalse(secureDao.existCryptPublicKey("app-a"))
        assertTrue(secureDao.existCryptPublicKey("app-b"))
        assertContentEquals(byteArrayOf(20), secureDao.serializedPublicKey("app-b"))
    }
}
