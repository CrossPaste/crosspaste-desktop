package com.crosspaste.serializer

import com.crosspaste.realm.paste.PasteLabel
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class PasteLabelRealmSetSerializer : KSerializer<RealmSet<PasteLabel>> {
    private val delegateSerializer = SetSerializer(PasteLabel.serializer())

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): RealmSet<PasteLabel> {
        val set = decoder.decodeSerializableValue(delegateSerializer)
        return realmSetOf(*set.toTypedArray())
    }

    override fun serialize(
        encoder: Encoder,
        value: RealmSet<PasteLabel>,
    ) {
        encoder.encodeSerializableValue(delegateSerializer, value.toSet())
    }
}
