package com.crosspaste.serializer

import com.crosspaste.db.paste.PasteCollection
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteData.Companion.createSearchContent
import com.crosspaste.db.paste.PasteState
import com.crosspaste.paste.item.PasteCoordinate
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
            element<Long>("pasteId")
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
        var pasteId = 0L
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
                2 -> pasteId = dec.decodeLongElement(descriptor, index)
                3 -> pasteAppearItem = dec.decodeSerializableElement(descriptor, index, PasteItem.serializer())
                4 -> pasteCollection = dec.decodeSerializableElement(descriptor, index, PasteCollection.serializer())
                5 -> pasteType = dec.decodeIntElement(descriptor, index)
                6 -> source = dec.decodeNullableSerializableElement(descriptor, index, String.serializer())
                7 -> size = dec.decodeLongElement(descriptor, index)
                8 -> hash = dec.decodeStringElement(descriptor, index)
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)

        val now = DateUtils.nowEpochMilliseconds()

        val pasteCoordinate = PasteCoordinate(appInstanceId, pasteId, now)

        val newPasteAppearItem = pasteAppearItem?.bind(pasteCoordinate)

        val newPasteCollection = pasteCollection.bind(pasteCoordinate)

        return PasteData(
            appInstanceId = appInstanceId,
            favorite = favorite,
            pasteId = pasteId,
            pasteAppearItem = newPasteAppearItem,
            pasteCollection = newPasteCollection,
            pasteType = pasteType,
            source = source,
            size = size,
            hash = hash,
            createTime = now,
            pasteSearchContent =
                createSearchContent(
                    source,
                    newPasteAppearItem?.getSearchContent(),
                ),
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
        compositeOutput.encodeLongElement(descriptor, 2, value.pasteId)
        value.pasteAppearItem?.let {
            compositeOutput.encodeSerializableElement(descriptor, 3, PasteItem.serializer(), it)
        }
        value.pasteCollection.let {
            compositeOutput.encodeSerializableElement(descriptor, 4, PasteCollection.serializer(), it)
        }
        compositeOutput.encodeIntElement(descriptor, 5, value.pasteType)
        compositeOutput.encodeNullableSerializableElement(descriptor, 6, String.serializer(), value.source)
        compositeOutput.encodeLongElement(descriptor, 7, value.size)
        compositeOutput.encodeStringElement(descriptor, 8, value.hash)
        compositeOutput.endStructure(descriptor)
    }
}
