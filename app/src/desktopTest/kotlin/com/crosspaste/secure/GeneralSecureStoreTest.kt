package com.crosspaste.secure

import com.crosspaste.db.secure.MemorySecureIO
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneralSecureStoreTest {

    private val serializer = SecureKeyPairSerializer()

    private fun createStore(): Pair<GeneralSecureStore, MemorySecureIO> {
        val keyPair = generateSecureKeyPair()
        val secureIO = MemorySecureIO()
        val store = GeneralSecureStore(keyPair, serializer, secureIO)
        return store to secureIO
    }

    @Test
    fun `secureKeyPair is accessible`() {
        val (store, _) = createStore()
        assertNotNull(store.secureKeyPair)
        assertNotNull(store.secureKeyPair.signKeyPair)
        assertNotNull(store.secureKeyPair.cryptKeyPair)
    }

    @Test
    fun `saveCryptPublicKey stores key`() =
        runBlocking {
            val (store, _) = createStore()
            val otherKeyPair = generateSecureKeyPair()
            val publicKeyBytes = serializer.encodeCryptPublicKey(otherKeyPair.cryptKeyPair.publicKey)

            store.saveCryptPublicKey("instance-1", publicKeyBytes)
            assertTrue(store.existCryptPublicKey("instance-1"))
        }

    @Test
    fun `existCryptPublicKey returns false for unknown instance`() =
        runBlocking {
            val (store, _) = createStore()
            assertFalse(store.existCryptPublicKey("nonexistent"))
        }

    @Test
    fun `deleteCryptPublicKey removes key`() =
        runBlocking {
            val (store, _) = createStore()
            val otherKeyPair = generateSecureKeyPair()
            val publicKeyBytes = serializer.encodeCryptPublicKey(otherKeyPair.cryptKeyPair.publicKey)

            store.saveCryptPublicKey("instance-1", publicKeyBytes)
            assertTrue(store.existCryptPublicKey("instance-1"))

            store.deleteCryptPublicKey("instance-1")
            assertFalse(store.existCryptPublicKey("instance-1"))
        }

    @Test
    fun `getMessageProcessor returns processor for saved key`() =
        runBlocking {
            val (store, _) = createStore()
            val otherKeyPair = generateSecureKeyPair()
            val publicKeyBytes = serializer.encodeCryptPublicKey(otherKeyPair.cryptKeyPair.publicKey)

            store.saveCryptPublicKey("instance-1", publicKeyBytes)
            val processor = store.getMessageProcessor("instance-1")
            assertNotNull(processor)
        }

    @Test
    fun `getMessageProcessor throws for missing key`() =
        runBlocking {
            val (store, _) = createStore()
            val exception =
                assertFailsWith<PasteException> {
                    store.getMessageProcessor("nonexistent")
                }
            assertEquals(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), exception.getErrorCode())
        }

    @Test
    fun `getMessageProcessor caches processor on second call`() =
        runBlocking {
            val (store, _) = createStore()
            val otherKeyPair = generateSecureKeyPair()
            val publicKeyBytes = serializer.encodeCryptPublicKey(otherKeyPair.cryptKeyPair.publicKey)

            store.saveCryptPublicKey("instance-1", publicKeyBytes)

            val processor1 = store.getMessageProcessor("instance-1")
            val processor2 = store.getMessageProcessor("instance-1")
            assertTrue(processor1 === processor2, "Processor should be cached")
        }

    @Test
    fun `saveCryptPublicKey invalidates cached processor`() =
        runBlocking {
            val (store, _) = createStore()
            val otherKeyPair = generateSecureKeyPair()
            val publicKeyBytes = serializer.encodeCryptPublicKey(otherKeyPair.cryptKeyPair.publicKey)

            store.saveCryptPublicKey("instance-1", publicKeyBytes)
            val processor1 = store.getMessageProcessor("instance-1")

            // Save again invalidates
            store.saveCryptPublicKey("instance-1", publicKeyBytes)
            val processor2 = store.getMessageProcessor("instance-1")
            assertFalse(processor1 === processor2, "Processor should be re-created after save")
        }

    @Test
    fun `encrypt and decrypt with stored processor`() =
        runBlocking {
            val storeKeyPair = generateSecureKeyPair()
            val otherKeyPair = generateSecureKeyPair()
            val secureIO = MemorySecureIO()
            val store = GeneralSecureStore(storeKeyPair, serializer, secureIO)

            val publicKeyBytes = serializer.encodeCryptPublicKey(otherKeyPair.cryptKeyPair.publicKey)
            store.saveCryptPublicKey("peer", publicKeyBytes)

            // Create reverse processor (other's private + store's public)
            val reverseProcessor =
                SecureMessageProcessor(
                    otherKeyPair.cryptKeyPair.privateKey,
                    storeKeyPair.cryptKeyPair.publicKey,
                )

            val storeProcessor = store.getMessageProcessor("peer")
            val plaintext = "Hello, encrypted world!".encodeToByteArray()

            val encrypted = storeProcessor.encrypt(plaintext)
            val decrypted = reverseProcessor.decrypt(encrypted)
            assertEquals(String(plaintext), String(decrypted))
        }

    @Test
    fun `multiple instances with separate sessions`() =
        runBlocking {
            val (store, _) = createStore()
            val keyPair1 = generateSecureKeyPair()
            val keyPair2 = generateSecureKeyPair()

            store.saveCryptPublicKey("inst-1", serializer.encodeCryptPublicKey(keyPair1.cryptKeyPair.publicKey))
            store.saveCryptPublicKey("inst-2", serializer.encodeCryptPublicKey(keyPair2.cryptKeyPair.publicKey))

            assertTrue(store.existCryptPublicKey("inst-1"))
            assertTrue(store.existCryptPublicKey("inst-2"))

            val p1 = store.getMessageProcessor("inst-1")
            val p2 = store.getMessageProcessor("inst-2")
            assertFalse(p1 === p2, "Different instances should have different processors")
        }

    @Test
    fun `concurrent access to different sessions`() =
        runBlocking {
            val (store, _) = createStore()
            val jobs =
                (1..10).map { i ->
                    async {
                        val keyPair = generateSecureKeyPair()
                        val publicKeyBytes = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey)
                        store.saveCryptPublicKey("inst-$i", publicKeyBytes)
                        store.existCryptPublicKey("inst-$i")
                    }
                }
            val results = jobs.awaitAll()
            assertTrue(results.all { it }, "All concurrent saves should succeed")
        }

    @Test
    fun `deleteCryptPublicKey for nonexistent instance does not throw`() =
        runBlocking {
            val (store, _) = createStore()
            // Should not throw
            store.deleteCryptPublicKey("nonexistent")
        }
}
