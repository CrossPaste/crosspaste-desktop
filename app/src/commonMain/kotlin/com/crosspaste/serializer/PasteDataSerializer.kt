package com.crosspaste.serializer

import com.crosspaste.db.paste.PasteCollection
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteState
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.utils.DateUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class PasteDataSerializer : KSerializer<PasteData> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("pasteData") {
            element<String>("appInstanceId")
            element<Boolean>("favorite")
            element<PasteItem?>("pasteAppearItem")
            element<PasteCollection?>("pasteCollection")
            element<Int>("pasteType")
            element<String?>("source")
            element<Long>("size")
            element<String>("hash")
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): PasteData {
        val dec = decoder.beginStructure(descriptor)
        var appInstanceId = ""
        var favorite = false
        var pasteAppearItem: PasteItem? = null
        var pasteCollection = PasteCollection(listOf())
        var pasteType = 0
        var source: String? = null
        var size = 0L
        var hash = ""
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                0 -> appInstanceId = dec.decodeStringElement(descriptor, index)
                1 -> favorite = dec.decodeBooleanElement(descriptor, index)
                2 -> pasteAppearItem = dec.decodeSerializableElement(descriptor, index, PasteItem.serializer())
                3 -> pasteCollection = dec.decodeSerializableElement(descriptor, index, PasteCollection.serializer())
                4 -> pasteType = dec.decodeIntElement(descriptor, index)
                5 -> source = dec.decodeNullableSerializableElement(descriptor, index, String.serializer())
                6 -> size = dec.decodeLongElement(descriptor, index)
                7 -> hash = dec.decodeStringElement(descriptor, index)
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)

        return PasteData(
            appInstanceId = appInstanceId,
            favorite = favorite,
            pasteAppearItem = pasteAppearItem,
            pasteCollection = pasteCollection,
            pasteType = pasteType,
            source = source,
            size = size,
            hash = hash,
            createTime = DateUtils.nowEpochMilliseconds(),
            pasteSearchContent = null,
            pasteState = PasteState.LOADING,
            remote = true,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: PasteData,
    ) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeStringElement(descriptor, 0, value.appInstanceId)
        compositeOutput.encodeBooleanElement(descriptor, 1, value.favorite)
        value.pasteAppearItem?.let {
            compositeOutput.encodeSerializableElement(descriptor, 2, PasteItem.serializer(), it)
        }
        value.pasteCollection.let {
            compositeOutput.encodeSerializableElement(descriptor, 3, PasteCollection.serializer(), it)
        }
        compositeOutput.encodeIntElement(descriptor, 4, value.pasteType)
        compositeOutput.encodeNullableSerializableElement(descriptor, 5, String.serializer(), value.source)
        compositeOutput.encodeLongElement(descriptor, 6, value.size)
        compositeOutput.encodeStringElement(descriptor, 7, value.hash)
        compositeOutput.endStructure(descriptor)
    }
}
