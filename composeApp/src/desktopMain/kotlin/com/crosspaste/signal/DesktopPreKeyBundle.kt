package com.crosspaste.signal

import org.signal.libsignal.protocol.state.PreKeyBundle

class DesktopPreKeyBundle(val preKeyBundle: PreKeyBundle) : PreKeyBundleInterface {

    override fun toString(): String {
        return "DesktopPreKeyBundle(registrationId=${preKeyBundle.registrationId}, " +
            "deviceId=${preKeyBundle.deviceId}, " +
            "preKeyId=${preKeyBundle.preKeyId}, " +
            "preKey=${preKeyBundle.preKey}, " +
            "signedPreKeyId=${preKeyBundle.signedPreKeyId}, " +
            "signedPreKey=${preKeyBundle.signedPreKey}, " +
            "signedPreKeySignature=${preKeyBundle.signedPreKeySignature}, " +
            "identityKey=${preKeyBundle.identityKey})"
    }
}
