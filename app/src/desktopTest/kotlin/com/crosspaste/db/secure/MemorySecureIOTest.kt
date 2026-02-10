package com.crosspaste.db.secure

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemorySecureIOTest {

    @Test
    fun `saveCryptPublicKey stores key`() =
        runBlocking {
            val io = MemorySecureIO()
            val key = byteArrayOf(1, 2, 3, 4)
            io.saveCryptPublicKey("app1", key)
            assertTrue(io.existCryptPublicKey("app1"))
        }

    @Test
    fun `existCryptPublicKey returns false for missing key`() =
        runBlocking {
            val io = MemorySecureIO()
            assertFalse(io.existCryptPublicKey("missing"))
        }

    @Test
    fun `deleteCryptPublicKey removes key`() =
        runBlocking {
            val io = MemorySecureIO()
            io.saveCryptPublicKey("app1", byteArrayOf(1))
            assertTrue(io.existCryptPublicKey("app1"))

            io.deleteCryptPublicKey("app1")
            assertFalse(io.existCryptPublicKey("app1"))
        }

    @Test
    fun `serializedPublicKey returns stored key`() =
        runBlocking {
            val io = MemorySecureIO()
            val key = byteArrayOf(10, 20, 30)
            io.saveCryptPublicKey("app1", key)

            val retrieved = io.serializedPublicKey("app1")
            assertNotNull(retrieved)
            assertContentEquals(key, retrieved)
        }

    @Test
    fun `serializedPublicKey returns null for missing key`() =
        runBlocking {
            val io = MemorySecureIO()
            assertNull(io.serializedPublicKey("missing"))
        }

    @Test
    fun `saveCryptPublicKey overwrites existing key`() =
        runBlocking {
            val io = MemorySecureIO()
            io.saveCryptPublicKey("app1", byteArrayOf(1, 2))
            io.saveCryptPublicKey("app1", byteArrayOf(3, 4))

            val retrieved = io.serializedPublicKey("app1")
            assertNotNull(retrieved)
            assertContentEquals(byteArrayOf(3, 4), retrieved)
        }

    @Test
    fun `multiple app instances are independent`() =
        runBlocking {
            val io = MemorySecureIO()
            io.saveCryptPublicKey("app1", byteArrayOf(1))
            io.saveCryptPublicKey("app2", byteArrayOf(2))

            assertContentEquals(byteArrayOf(1), io.serializedPublicKey("app1"))
            assertContentEquals(byteArrayOf(2), io.serializedPublicKey("app2"))

            io.deleteCryptPublicKey("app1")
            assertFalse(io.existCryptPublicKey("app1"))
            assertTrue(io.existCryptPublicKey("app2"))
        }

    @Test
    fun `deleteCryptPublicKey for nonexistent key does not throw`() =
        runBlocking {
            val io = MemorySecureIO()
            io.deleteCryptPublicKey("nonexistent")
        }

    @Test
    fun `saveCryptPublicKey with empty key`() =
        runBlocking {
            val io = MemorySecureIO()
            io.saveCryptPublicKey("app1", byteArrayOf())
            assertTrue(io.existCryptPublicKey("app1"))
            assertContentEquals(byteArrayOf(), io.serializedPublicKey("app1"))
        }
}