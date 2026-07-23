package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class PairingV3ErrorMappingTest {

    /** Every protocol error code must map 1:1 onto a transport StandardErrorCode. */
    @Test
    fun testEveryPairingErrorCodeRoundTrips() {
        PairingV3ErrorCode.entries.forEach { code ->
            val standard = code.toStandardErrorCode()
            assertEquals(code.name, standard.name)
            assertEquals(code, pairingV3ErrorCodeOf(standard.getCode()))
        }
    }

    @Test
    fun testNonPairingCodeMapsToNull() {
        assertEquals(null, pairingV3ErrorCodeOf(0))
        assertEquals(null, pairingV3ErrorCodeOf(1001))
    }
}
