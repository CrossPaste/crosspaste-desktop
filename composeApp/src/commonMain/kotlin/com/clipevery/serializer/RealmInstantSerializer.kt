package com.clipevery.serializer

import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.types.RealmInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RealmInstantSerializer: KSerializer<RealmInstant> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RealmInstant") {}

    override fun deserialize(decoder: Decoder): RealmInstant {
        val dec = decoder.beginStructure(descriptor)
        var epochSeconds: Long = 0L
        var nanosecondsOfSecond: Int = 0
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                0 -> epochSeconds = dec.decodeLongElement(descriptor, index)
                1 -> nanosecondsOfSecond = dec.decodeIntElement(descriptor, index)
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)
        return RealmInstantImpl(epochSeconds, nanosecondsOfSecond)
    }

    override fun serialize(encoder: Encoder, value: RealmInstant) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeLongElement(descriptor, 0, value.epochSeconds)
        compositeOutput.encodeIntElement(descriptor, 1, value.nanosecondsOfSecond)
        compositeOutput.endStructure(descriptor)
    }
}