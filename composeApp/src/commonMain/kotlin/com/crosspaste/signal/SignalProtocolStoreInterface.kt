package com.crosspaste.signal

import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.dto.sync.RequestTrust

interface SignalProtocolStoreInterface {

    fun saveIdentity(
        address: SignalAddress,
        preKeyBundleInterface: PreKeyBundleInterface,
    )

    fun saveIdentity(
        address: SignalAddress,
        preKeySignalMessageInterface: PreKeySignalMessageInterface,
    )

    fun saveIdentity(
        address: SignalAddress,
        requestTrust: RequestTrust,
    )

    fun getIdentityKeyPublicKey(): ByteArray

    fun existIdentity(address: SignalAddress): Boolean

    fun existSession(address: SignalAddress): Boolean

    fun generatePreKeyBundle(signalDao: SignalDao): PreKeyBundleInterface

    fun containsSignedPreKey(signedPreKeyId: Int): Boolean
}
