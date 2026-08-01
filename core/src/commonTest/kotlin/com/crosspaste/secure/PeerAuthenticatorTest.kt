package com.crosspaste.secure

import com.crosspaste.utils.CryptographyUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeerAuthenticatorTest {

    @Test
    fun `both peers derive the same authentication key`() =
        runTest {
            val first = CryptographyUtils.generateSecureKeyPair()
            val second = CryptographyUtils.generateSecureKeyPair()
            val firstAuthenticator =
                PeerAuthenticator(first.cryptKeyPair.privateKey, second.cryptKeyPair.publicKey)
            val secondAuthenticator =
                PeerAuthenticator(second.cryptKeyPair.privateKey, first.cryptKeyPair.publicKey)
            val payload = "authenticated payload".encodeToByteArray()

            val code = firstAuthenticator.authenticationCode(payload)

            assertTrue(secondAuthenticator.verify(payload, code))
            assertFalse(secondAuthenticator.verify("tampered payload".encodeToByteArray(), code))
        }
}
