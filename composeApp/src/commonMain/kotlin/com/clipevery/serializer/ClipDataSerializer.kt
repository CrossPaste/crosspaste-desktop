package com.clipevery.serializer

import com.clipevery.clip.item.ClipInit
import com.clipevery.dao.clip.ClipCollection
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipLabel
import com.clipevery.dao.clip.ClipState
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

object ClipDataSerializer : KSerializer<ClipData> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ClipData") {
            element<String>("id")
            element<String>("appInstanceId")
            element<Long>("clipId")
            element<RealmAny?>("clipAppearItem")
            element<ClipCollection?>("clipCollection")
            element<Int>("clipType")
            element<String?>("source")
            element<Long>("size")
            element<String>("md5")
            element<Boolean>("favorite")
            element<RealmSet<ClipLabel>>("labels")
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): ClipData {
        val dec = decoder.beginStructure(descriptor)
        var id = ""
        var appInstanceId = ""
        var clipId = 0L
        var clipAppearContent: RealmAny? = null
        var clipCollection: ClipCollection? = null
        var clipType = 0
        var source: String? = null
        var size = 0L
        var md5 = ""
        var favorite = false
        var labels: RealmSet<ClipLabel> = realmSetOf()
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                0 -> id = dec.decodeStringElement(descriptor, index)
                1 -> appInstanceId = dec.decodeStringElement(descriptor, index)
                2 -> clipId = dec.decodeLongElement(descriptor, index)
                3 -> clipAppearContent = dec.decodeSerializableElement(descriptor, index, RealmAnyKSerializer)
                4 -> clipCollection = dec.decodeSerializableElement(descriptor, index, ClipCollection.serializer())
                5 -> clipType = dec.decodeIntElement(descriptor, index)
                6 -> source = dec.decodeNullableSerializableElement(descriptor, index, String.serializer())
                7 -> size = dec.decodeLongElement(descriptor, index)
                8 -> md5 = dec.decodeStringElement(descriptor, index)
                9 -> favorite = dec.decodeBooleanElement(descriptor, index)
                10 -> labels = dec.decodeSerializableElement(descriptor, index, RealmSetKSerializer(ClipLabel.serializer()))
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)
        val clipData =
            ClipData().apply {
                this.id = BsonObjectId(id)
                this.appInstanceId = appInstanceId
                this.clipId = clipId
                this.clipAppearItem = clipAppearContent
                this.clipCollection = clipCollection
                this.clipType = clipType
                this.source = source
                this.clipSearchContent = ClipCollection.getClipItem(clipAppearContent)?.getSearchContent()
                this.md5 = md5
                this.size = size
                this.createTime = RealmInstant.now()
                this.clipState = ClipState.LOADING
                this.remote = true
                this.favorite = favorite
                this.labels = labels
            }

        for (clipInit in clipData.getClipAppearItems().filterIsInstance<ClipInit>()) {
            clipInit.init(clipData.appInstanceId, clipData.clipId)
        }

        return clipData
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: ClipData,
    ) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeStringElement(descriptor, 0, value.id.toHexString())
        compositeOutput.encodeStringElement(descriptor, 1, value.appInstanceId)

        compositeOutput.encodeLongElement(descriptor, 2, value.clipId)
        value.clipAppearItem?.let {
            compositeOutput.encodeSerializableElement(descriptor, 3, RealmAnyKSerializer, it)
        }
        value.clipCollection?.let {
            compositeOutput.encodeSerializableElement(descriptor, 4, ClipCollection.serializer(), it)
        }
        compositeOutput.encodeIntElement(descriptor, 5, value.clipType)
        compositeOutput.encodeNullableSerializableElement(descriptor, 6, String.serializer(), value.source)
        compositeOutput.encodeLongElement(descriptor, 7, value.size)
        compositeOutput.encodeStringElement(descriptor, 8, value.md5)
        compositeOutput.encodeBooleanElement(descriptor, 9, value.favorite)
        compositeOutput.encodeSerializableElement(descriptor, 10, RealmSetKSerializer(ClipLabel.serializer()), value.labels)
        compositeOutput.endStructure(descriptor)
    }
}
