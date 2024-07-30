package com.crosspaste.serializer

import com.crosspaste.dao.paste.PasteCollection
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteLabel
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.paste.item.PasteInit
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.mongodb.kbson.BsonObjectId

object PasteDataSerializer : KSerializer<PasteData> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("pasteData") {
            element<String>("id")
            element<String>("appInstanceId")
            element<Long>("pasteId")
            element<RealmAny?>("pasteAppearItem")
            element<PasteCollection?>("pasteCollection")
            element<Int>("pasteType")
            element<String?>("source")
            element<Long>("size")
            element<String>("md5")
            element<Boolean>("favorite")
            element<RealmSet<PasteLabel>>("labels")
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): PasteData {
        val dec = decoder.beginStructure(descriptor)
        var id = ""
        var appInstanceId = ""
        var pasteId = 0L
        var pasteAppearContent: RealmAny? = null
        var pasteCollection: PasteCollection? = null
        var pasteType = 0
        var source: String? = null
        var size = 0L
        var md5 = ""
        var favorite = false
        var labels: RealmSet<PasteLabel> = realmSetOf()
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                0 -> id = dec.decodeStringElement(descriptor, index)
                1 -> appInstanceId = dec.decodeStringElement(descriptor, index)
                2 -> pasteId = dec.decodeLongElement(descriptor, index)
                3 -> pasteAppearContent = dec.decodeSerializableElement(descriptor, index, RealmAnyKSerializer)
                4 -> pasteCollection = dec.decodeSerializableElement(descriptor, index, PasteCollection.serializer())
                5 -> pasteType = dec.decodeIntElement(descriptor, index)
                6 -> source = dec.decodeNullableSerializableElement(descriptor, index, String.serializer())
                7 -> size = dec.decodeLongElement(descriptor, index)
                8 -> md5 = dec.decodeStringElement(descriptor, index)
                9 -> favorite = dec.decodeBooleanElement(descriptor, index)
                10 -> labels = dec.decodeSerializableElement(descriptor, index, RealmSetKSerializer(PasteLabel.serializer()))
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)
        val pasteData =
            PasteData().apply {
                this.id = BsonObjectId(id)
                this.appInstanceId = appInstanceId
                this.pasteId = pasteId
                this.pasteAppearItem = pasteAppearContent
                this.pasteCollection = pasteCollection
                this.pasteType = pasteType
                this.source = source
                this.pasteSearchContent =
                    PasteData.createSearchContent(
                        source,
                        PasteCollection.getPasteItem(pasteAppearContent)?.getSearchContent(),
                    )
                this.md5 = md5
                this.size = size
                this.createTime = RealmInstant.now()
                this.pasteState = PasteState.LOADING
                this.remote = true
                this.favorite = favorite
                this.labels = labels
            }

        for (pasteInit in pasteData.getPasteAppearItems().filterIsInstance<PasteInit>()) {
            pasteInit.init(pasteData.appInstanceId, pasteData.pasteId)
        }

        return pasteData
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: PasteData,
    ) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeStringElement(descriptor, 0, value.id.toHexString())
        compositeOutput.encodeStringElement(descriptor, 1, value.appInstanceId)

        compositeOutput.encodeLongElement(descriptor, 2, value.pasteId)
        value.pasteAppearItem?.let {
            compositeOutput.encodeSerializableElement(descriptor, 3, RealmAnyKSerializer, it)
        }
        value.pasteCollection?.let {
            compositeOutput.encodeSerializableElement(descriptor, 4, PasteCollection.serializer(), it)
        }
        compositeOutput.encodeIntElement(descriptor, 5, value.pasteType)
        compositeOutput.encodeNullableSerializableElement(descriptor, 6, String.serializer(), value.source)
        compositeOutput.encodeLongElement(descriptor, 7, value.size)
        compositeOutput.encodeStringElement(descriptor, 8, value.md5)
        compositeOutput.encodeBooleanElement(descriptor, 9, value.favorite)
        compositeOutput.encodeSerializableElement(descriptor, 10, RealmSetKSerializer(PasteLabel.serializer()), value.labels)
        compositeOutput.endStructure(descriptor)
    }
}
