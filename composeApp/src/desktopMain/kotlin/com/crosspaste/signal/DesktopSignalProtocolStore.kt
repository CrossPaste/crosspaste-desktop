package com.crosspaste.signal

import com.crosspaste.dto.sync.RequestTrust
import com.crosspaste.realm.signal.SignalRealm
import com.crosspaste.utils.EncryptUtils
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.InMemorySenderKeyStore
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.signal.libsignal.protocol.state.impl.InMemoryKyberPreKeyStore
import java.util.UUID

class DesktopSignalProtocolStore(
    private val identityKeyStore: IdentityKeyStore,
    private val preKeyStore: PreKeyStore,
    private val sessionStore: SessionStore,
    private val signedPreKeyStore: SignedPreKeyStore,
    private val senderKeyStore: SenderKeyStore = InMemorySenderKeyStore(),
    private val kyberPreKeyStore: KyberPreKeyStore = InMemoryKyberPreKeyStore(),
) : SignalProtocolStore, SignalProtocolStoreInterface {

    // common interface impl

    override fun saveIdentity(
        address: SignalAddress,
        preKeyBundleInterface: PreKeyBundleInterface,
    ) {
        preKeyBundleInterface as DesktopPreKeyBundle
        saveIdentity(
            SignalProtocolAddress(address.name, address.deviceId),
            preKeyBundleInterface.preKeyBundle.identityKey,
        )
    }

    override fun saveIdentity(
        address: SignalAddress,
        preKeySignalMessageInterface: PreKeySignalMessageInterface,
    ) {
        preKeySignalMessageInterface as DesktopPreKeySignalMessage
        saveIdentity(
            SignalProtocolAddress(address.name, address.deviceId),
            preKeySignalMessageInterface.preKeySignalMessage.identityKey,
        )
    }

    override fun saveIdentity(
        address: SignalAddress,
        requestTrust: RequestTrust,
    ) {
        saveIdentity(
            SignalProtocolAddress(address.name, address.deviceId),
            IdentityKey(requestTrust.identityKey),
        )
    }

    override fun getIdentityKeyPublicKey(): ByteArray {
        return identityKeyPair.publicKey.serialize()
    }

    override fun existIdentity(address: SignalAddress): Boolean {
        return getIdentity(SignalProtocolAddress(address.name, address.deviceId)) != null
    }

    override fun existSession(address: SignalAddress): Boolean {
        return loadSession(SignalProtocolAddress(address.name, address.deviceId)) != null
    }

    override fun generatePreKeyBundle(signalRealm: SignalRealm): PreKeyBundleInterface {
        val deviceId = 1
        val preKey = EncryptUtils.generatePreKeyPair(signalRealm)
        val preKeyId = preKey.id
        val preKeyRecord = PreKeyRecord(preKey.serialized)
        val preKeyPairPublicKey = preKeyRecord.keyPair.publicKey

        val signedPreKey = EncryptUtils.generatesSignedPreKeyPair(signalRealm, identityKeyPair.privateKey)
        val signedPreKeyId = signedPreKey.id
        val signedPreKeyRecord = SignedPreKeyRecord(signedPreKey.serialized)
        val signedPreKeySignature = signedPreKeyRecord.signature

        val preKeyBundle =
            PreKeyBundle(
                localRegistrationId,
                deviceId,
                preKeyId,
                preKeyPairPublicKey,
                signedPreKeyId,
                signedPreKeyRecord.keyPair.publicKey,
                signedPreKeySignature,
                identityKeyPair.publicKey,
            )
        return DesktopPreKeyBundle(preKeyBundle)
    }

    // desktop jvm impl

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyStore.identityKeyPair
    }

    override fun getLocalRegistrationId(): Int {
        return identityKeyStore.localRegistrationId
    }

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): Boolean {
        return identityKeyStore.saveIdentity(address, identityKey)
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress?,
        identityKey: IdentityKey?,
        direction: IdentityKeyStore.Direction?,
    ): Boolean {
        return identityKeyStore.isTrustedIdentity(address, identityKey, direction)
    }

    override fun getIdentity(address: SignalProtocolAddress?): IdentityKey? {
        return identityKeyStore.getIdentity(address)
    }

    @Throws(InvalidKeyIdException::class)
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return preKeyStore.loadPreKey(preKeyId)
    }

    override fun storePreKey(
        preKeyId: Int,
        record: PreKeyRecord?,
    ) {
        preKeyStore.storePreKey(preKeyId, record)
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeyStore.containsPreKey(preKeyId)
    }

    override fun removePreKey(preKeyId: Int) {
        preKeyStore.removePreKey(preKeyId)
    }

    override fun loadSession(address: SignalProtocolAddress?): SessionRecord? {
        return sessionStore.loadSession(address)
    }

    @Throws(NoSessionException::class)
    override fun loadExistingSessions(addresses: List<SignalProtocolAddress?>?): List<SessionRecord> {
        return sessionStore.loadExistingSessions(addresses)
    }

    override fun getSubDeviceSessions(name: String?): List<Int> {
        return sessionStore.getSubDeviceSessions(name)
    }

    override fun storeSession(
        address: SignalProtocolAddress?,
        record: SessionRecord?,
    ) {
        sessionStore.storeSession(address, record)
    }

    override fun containsSession(address: SignalProtocolAddress?): Boolean {
        return sessionStore.containsSession(address)
    }

    override fun deleteSession(address: SignalProtocolAddress?) {
        sessionStore.deleteSession(address)
    }

    override fun deleteAllSessions(name: String?) {
        sessionStore.deleteAllSessions(name)
    }

    @Throws(InvalidKeyIdException::class)
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        return signedPreKeyStore.loadSignedPreKey(signedPreKeyId)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return signedPreKeyStore.loadSignedPreKeys()
    }

    override fun storeSignedPreKey(
        signedPreKeyId: Int,
        record: SignedPreKeyRecord?,
    ) {
        signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record)
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeyStore.containsSignedPreKey(signedPreKeyId)
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeyStore.removeSignedPreKey(signedPreKeyId)
    }

    override fun storeSenderKey(
        sender: SignalProtocolAddress?,
        distributionId: UUID?,
        record: SenderKeyRecord?,
    ) {
        senderKeyStore.storeSenderKey(sender, distributionId, record)
    }

    override fun loadSenderKey(
        sender: SignalProtocolAddress?,
        distributionId: UUID?,
    ): SenderKeyRecord {
        return senderKeyStore.loadSenderKey(sender, distributionId)
    }

    @Throws(InvalidKeyIdException::class)
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        return kyberPreKeyStore.loadKyberPreKey(kyberPreKeyId)
    }

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
        return kyberPreKeyStore.loadKyberPreKeys()
    }

    override fun storeKyberPreKey(
        kyberPreKeyId: Int,
        record: KyberPreKeyRecord?,
    ) {
        kyberPreKeyStore.storeKyberPreKey(kyberPreKeyId, record)
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        return kyberPreKeyStore.containsKyberPreKey(kyberPreKeyId)
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {
        kyberPreKeyStore.markKyberPreKeyUsed(kyberPreKeyId)
    }
}
