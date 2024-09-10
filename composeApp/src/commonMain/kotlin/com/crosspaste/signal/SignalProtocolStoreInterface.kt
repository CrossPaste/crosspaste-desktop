package com.crosspaste.signal

import com.crosspaste.dto.sync.RequestTrust
import com.crosspaste.realm.signal.SignalRealm

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

    fun generatePreKeyBundle(signalRealm: SignalRealm): PreKeyBundleInterface

    fun containsSignedPreKey(signedPreKeyId: Int): Boolean
}
