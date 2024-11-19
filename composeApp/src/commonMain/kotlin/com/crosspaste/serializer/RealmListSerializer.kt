package com.crosspaste.serializer

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.Path

object StringRealmListSerializer : KSerializer<RealmList<String>> {
    private val delegateSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: RealmList<String>,
    ) {
        encoder.encodeSerializableValue(delegateSerializer, value)
    }

    override fun deserialize(decoder: Decoder): RealmList<String> {
        val list = decoder.decodeSerializableValue(delegateSerializer)
        return realmListOf(*list.toTypedArray())
    }
}

object PathStringRealmListSerializer : KSerializer<RealmList<String>> {
    private val delegateSerializer = ListSerializer(ListSerializer(String.serializer()))

    private val separator = Path.DIRECTORY_SEPARATOR

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: RealmList<String>,
    ) {
        val pathsList: List<List<String>> = value.map { it.split(separator) }
        encoder.encodeSerializableValue(delegateSerializer, pathsList)
    }

    override fun deserialize(decoder: Decoder): RealmList<String> {
        val pathsList: List<List<String>> = decoder.decodeSerializableValue(delegateSerializer)
        return realmListOf(*pathsList.map { it.joinToString(separator) }.toTypedArray())
    }
}

object RealmAnyRealmListSerializer : KSerializer<RealmList<RealmAny>> {
    private val delegateSerializer = ListSerializer(RealmAnyKSerializer)

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: RealmList<RealmAny>,
    ) {
        encoder.encodeSerializableValue(delegateSerializer, value.toList())
    }

    override fun deserialize(decoder: Decoder): RealmList<RealmAny> {
        val list = decoder.decodeSerializableValue(delegateSerializer)
        return realmListOf(*list.toTypedArray())
    }
}
