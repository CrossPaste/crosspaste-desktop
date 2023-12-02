package com.clipevery.model

import com.clipevery.encrypt.base64Encode
import org.signal.libsignal.protocol.IdentityKey
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

data class AppRequestBindInfo(
    private val platform: String,
    private val publicKey: IdentityKey,
    private val hostInfoList: List<AppHostInfo>,
    private val port: Int) {

    fun getBase64Encode(salt: Int): String {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        dataStream.writeInt(salt)
        dataStream.writeUTF(platform)
        val serialize = publicKey.serialize()
        dataStream.writeInt(serialize.size)
        dataStream.write(serialize)
        dataStream.writeInt(hostInfoList.size)
        hostInfoList.forEach {
            dataStream.writeUTF(it.displayName)
            dataStream.writeUTF(it.hostAddress)
        }
        dataStream.writeInt(port)
        return base64Encode(byteStream.toByteArray())
    }
}
