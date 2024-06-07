package com.clipevery.dao.store

import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.InvalidVersionException
import org.signal.libsignal.protocol.LegacyMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.Medium
import org.signal.libsignal.protocol.util.Pair
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SessionBuilderTest {

    private val ALICE_ADDRESS = SignalProtocolAddress("+14151111111", 1)
    private val BOB_ADDRESS = SignalProtocolAddress("+14152222222", 1)

    @Test
    @Throws(
        InvalidKeyException::class,
        InvalidVersionException::class,
        InvalidMessageException::class,
        InvalidKeyIdException::class,
        DuplicateMessageException::class,
        LegacyMessageException::class,
        UntrustedIdentityException::class,
        NoSessionException::class,
    )
    fun testBasicPreKey() {
        var aliceStore: SignalProtocolStore = TestInMemorySignalProtocolStore()
        var aliceSessionBuilder = SessionBuilder(aliceStore, aliceStore, aliceStore, aliceStore, BOB_ADDRESS)
        val bobStore: SignalProtocolStore = TestInMemorySignalProtocolStore()
        val bundleFactory = X3DHBundleFactory()
        val bobPreKey: PreKeyBundle = bundleFactory.createBundle(bobStore)
        aliceSessionBuilder.process(bobPreKey)
        assertTrue(aliceStore.containsSession(BOB_ADDRESS))
        assertTrue(aliceStore.loadSession(BOB_ADDRESS).sessionVersion == 3)
        val originalMessage = "Good, fast, cheap: pick two"
        var aliceSessionCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        var outgoingMessage = aliceSessionCipher.encrypt(originalMessage.toByteArray())
        assertTrue(outgoingMessage.type == CiphertextMessage.PREKEY_TYPE)
        val incomingMessage = PreKeySignalMessage(outgoingMessage.serialize())
        val bobSessionCipher = SessionCipher(bobStore, ALICE_ADDRESS)
        var plaintext = bobSessionCipher.decrypt(incomingMessage)
        assertTrue(bobStore.containsSession(ALICE_ADDRESS))
        assertEquals(
            bobStore.loadSession(ALICE_ADDRESS).sessionVersion.toLong(),
            3.toLong(),
        )
        assertNotNull(bobStore.loadSession(ALICE_ADDRESS).aliceBaseKey)
        assertTrue(originalMessage == String(plaintext!!))
        val bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.toByteArray())
        assertTrue(bobOutgoingMessage.type == CiphertextMessage.WHISPER_TYPE)
        val alicePlaintext =
            aliceSessionCipher.decrypt(SignalMessage(bobOutgoingMessage.serialize()))
        assertTrue(String(alicePlaintext) == originalMessage)
        runInteraction(aliceStore, bobStore)
        aliceStore = TestInMemorySignalProtocolStore()
        aliceSessionBuilder = SessionBuilder(aliceStore, BOB_ADDRESS)
        aliceSessionCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        val anotherBundle: PreKeyBundle = bundleFactory.createBundle(bobStore)
        aliceSessionBuilder.process(anotherBundle)
        outgoingMessage = aliceSessionCipher.encrypt(originalMessage.toByteArray())
        try {
            bobSessionCipher.decrypt(PreKeySignalMessage(outgoingMessage.serialize()))
            fail("shouldn't be trusted!")
        } catch (uie: UntrustedIdentityException) {
            bobStore.saveIdentity(
                ALICE_ADDRESS,
                PreKeySignalMessage(outgoingMessage.serialize()).identityKey,
            )
        }
        plaintext = bobSessionCipher.decrypt(PreKeySignalMessage(outgoingMessage.serialize()))
        assertTrue(String(plaintext) == originalMessage)
        val random = Random()
        val badIdentityBundle =
            PreKeyBundle(
                bobStore.localRegistrationId,
                1,
                random.nextInt(Medium.MAX_VALUE),
                Curve.generateKeyPair().publicKey,
                random.nextInt(Medium.MAX_VALUE),
                bobPreKey.signedPreKey,
                bobPreKey.signedPreKeySignature,
                aliceStore.identityKeyPair.publicKey,
            )
        try {
            aliceSessionBuilder.process(badIdentityBundle)
            fail("shoulnd't be trusted!")
        } catch (uie: UntrustedIdentityException) {
            // good
        }
    }

    @Throws(
        DuplicateMessageException::class,
        LegacyMessageException::class,
        InvalidMessageException::class,
        InvalidVersionException::class,
        InvalidKeyException::class,
        NoSessionException::class,
        UntrustedIdentityException::class,
    )
    private fun runInteraction(
        aliceStore: SignalProtocolStore,
        bobStore: SignalProtocolStore,
    ) {
        val aliceSessionCipher = SessionCipher(aliceStore, BOB_ADDRESS)
        val bobSessionCipher = SessionCipher(bobStore, ALICE_ADDRESS)
        val originalMessage = "smert ze smert"
        val aliceMessage = aliceSessionCipher.encrypt(originalMessage.toByteArray())
        assertEquals(aliceMessage.type.toLong(), CiphertextMessage.WHISPER_TYPE.toLong())
        var plaintext = bobSessionCipher.decrypt(SignalMessage(aliceMessage.serialize()))
        assertTrue(String(plaintext!!) == originalMessage)
        val bobMessage = bobSessionCipher.encrypt(originalMessage.toByteArray())
        assertEquals(bobMessage.type.toLong(), CiphertextMessage.WHISPER_TYPE.toLong())
        plaintext = aliceSessionCipher.decrypt(SignalMessage(bobMessage.serialize()))
        assertTrue(String(plaintext) == originalMessage)
        for (i in 0..9) {
            val loopingMessage = (
                "What do we mean by saying that existence precedes essence? " +
                    "We mean that man first of all exists, encounters himself, " +
                    "surges up in the world--and defines himself aftward. " +
                    i
            )
            val aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.toByteArray())
            val loopingPlaintext =
                bobSessionCipher.decrypt(SignalMessage(aliceLoopingMessage.serialize()))
            assertTrue(String(loopingPlaintext) == loopingMessage)
        }
        for (i in 0..9) {
            val loopingMessage = (
                "What do we mean by saying that existence precedes essence? " +
                    "We mean that man first of all exists, encounters himself, " +
                    "surges up in the world--and defines himself aftward. " +
                    i
            )
            val bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.toByteArray())
            val loopingPlaintext =
                aliceSessionCipher.decrypt(SignalMessage(bobLoopingMessage.serialize()))
            assertTrue(String(loopingPlaintext) == loopingMessage)
        }
        val aliceOutOfOrderMessages: MutableSet<Pair<String, CiphertextMessage>> = HashSet()
        for (i in 0..9) {
            val loopingMessage = (
                "What do we mean by saying that existence precedes essence? " +
                    "We mean that man first of all exists, encounters himself, " +
                    "surges up in the world--and defines himself aftward. " +
                    i
            )
            val aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.toByteArray())
            aliceOutOfOrderMessages.add(Pair(loopingMessage, aliceLoopingMessage))
        }
        for (i in 0..9) {
            val loopingMessage = (
                "What do we mean by saying that existence precedes essence? " +
                    "We mean that man first of all exists, encounters himself, " +
                    "surges up in the world--and defines himself aftward. " +
                    i
            )
            val aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.toByteArray())
            val loopingPlaintext =
                bobSessionCipher.decrypt(SignalMessage(aliceLoopingMessage.serialize()))
            assertTrue(String(loopingPlaintext) == loopingMessage)
        }
        for (i in 0..9) {
            val loopingMessage = "You can only desire based on what you know: $i"
            val bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.toByteArray())
            val loopingPlaintext =
                aliceSessionCipher.decrypt(SignalMessage(bobLoopingMessage.serialize()))
            assertTrue(String(loopingPlaintext) == loopingMessage)
        }
        for (aliceOutOfOrderMessage in aliceOutOfOrderMessages) {
            val outOfOrderPlaintext =
                bobSessionCipher.decrypt(SignalMessage(aliceOutOfOrderMessage.second().serialize()))
            assertTrue(String(outOfOrderPlaintext) == aliceOutOfOrderMessage.first())
        }
    }
}

interface BundleFactory {
    @Throws(InvalidKeyException::class)
    fun createBundle(store: SignalProtocolStore): PreKeyBundle
}

class X3DHBundleFactory : BundleFactory {
    @Throws(InvalidKeyException::class)
    override fun createBundle(store: SignalProtocolStore): PreKeyBundle {
        val preKeyPair = Curve.generateKeyPair()
        val signedPreKeyPair = Curve.generateKeyPair()
        val signedPreKeySignature =
            Curve.calculateSignature(
                store.identityKeyPair.privateKey,
                signedPreKeyPair.publicKey.serialize(),
            )
        val random = Random()
        val preKeyId = random.nextInt(Medium.MAX_VALUE)
        val signedPreKeyId = random.nextInt(Medium.MAX_VALUE)
        store.storePreKey(preKeyId, PreKeyRecord(preKeyId, preKeyPair))
        store.storeSignedPreKey(
            signedPreKeyId,
            SignedPreKeyRecord(
                signedPreKeyId,
                System.currentTimeMillis(),
                signedPreKeyPair,
                signedPreKeySignature,
            ),
        )
        return PreKeyBundle(
            store.localRegistrationId,
            1,
            preKeyId,
            preKeyPair.publicKey,
            signedPreKeyId,
            signedPreKeyPair.publicKey,
            signedPreKeySignature,
            store.identityKeyPair.publicKey,
        )
    }
}
