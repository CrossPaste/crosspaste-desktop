package com.clipevery.serializer

import com.clipevery.dao.clip.ClipLabel
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ClipLabelRealmSetSerializer : KSerializer<RealmSet<ClipLabel>> {
    private val delegateSerializer = SetSerializer(ClipLabel.serializer())

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): RealmSet<ClipLabel> {
        val set = decoder.decodeSerializableValue(delegateSerializer)
        return realmSetOf(*set.toTypedArray())
    }

    override fun serialize(
        encoder: Encoder,
        value: RealmSet<ClipLabel>,
    ) {
        encoder.encodeSerializableValue(delegateSerializer, value.toSet())
    }
}
