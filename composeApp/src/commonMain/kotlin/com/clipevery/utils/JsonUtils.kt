package com.clipevery.utils

import com.clipevery.serializer.Base64ByteArraySerializer
import com.clipevery.serializer.IdentityKeySerializer
import com.clipevery.serializer.PreKeyBundleSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.state.PreKeyBundle

object JsonUtils {

    val JSON: Json = Json {
        serializersModule = SerializersModule {
            serializersModuleOf(ByteArray::class, Base64ByteArraySerializer)
            serializersModuleOf(PreKeyBundle::class, PreKeyBundleSerializer)
            serializersModuleOf(IdentityKey::class, IdentityKeySerializer)
        }
    }

}