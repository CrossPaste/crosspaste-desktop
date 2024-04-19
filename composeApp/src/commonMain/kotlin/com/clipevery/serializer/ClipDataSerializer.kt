package com.clipevery.serializer

import com.clipevery.clip.item.ClipInit
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipLabel
import com.clipevery.dao.clip.ClipState
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmSet
import kotlinx.serialization.KSerializer
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
            element<RealmAny?>("clipAppearContent")
            element<ClipContent?>("clipContent")
            element<Int>("clipType")
            element<Long>("size")
            element<String>("md5")
            element<Boolean>("isFavorite")
            element<RealmSet<ClipLabel>>("labels")
        }

    override fun deserialize(decoder: Decoder): ClipData {
        val dec = decoder.beginStructure(descriptor)
        var id = ""
        var appInstanceId = ""
        var clipId = 0L
        var clipAppearContent: RealmAny? = null
        var clipContent: ClipContent? = null
        var clipType = 0
        var size = 0L
        var md5 = ""
        var isFavorite = false
        var labels: RealmSet<ClipLabel> = realmSetOf()
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                0 -> id = dec.decodeStringElement(descriptor, index)
                1 -> appInstanceId = dec.decodeStringElement(descriptor, index)
                2 -> clipId = dec.decodeLongElement(descriptor, index)
                3 -> clipAppearContent = dec.decodeSerializableElement(descriptor, index, RealmAnyKSerializer)
                4 -> clipContent = dec.decodeSerializableElement(descriptor, index, ClipContent.serializer())
                5 -> clipType = dec.decodeIntElement(descriptor, index)
                6 -> size = dec.decodeLongElement(descriptor, index)
                7 -> md5 = dec.decodeStringElement(descriptor, index)
                8 -> isFavorite = dec.decodeBooleanElement(descriptor, index)
                9 -> labels = dec.decodeSerializableElement(descriptor, index, RealmSetKSerializer(ClipLabel.serializer()))
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)
        val clipData =
            ClipData().apply {
                this.id = BsonObjectId(id)
                this.appInstanceId = appInstanceId
                this.clipId = clipId
                this.clipAppearContent = clipAppearContent
                this.clipContent = clipContent
                this.clipType = clipType
                this.clipSearchContent = ClipContent.getClipItem(clipAppearContent)?.getSearchContent()
                this.md5 = md5
                this.size = size
                this.createTime = RealmInstant.now()
                this.clipState = ClipState.LOADING
                this.isRemote = true
                this.isFavorite = isFavorite
                this.labels = labels
            }

        for (clipInit in clipData.getClipAppearItems().filterIsInstance<ClipInit>()) {
            clipInit.init(clipData.appInstanceId, clipData.clipId)
        }

        return clipData
    }

    override fun serialize(
        encoder: Encoder,
        value: ClipData,
    ) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeStringElement(descriptor, 0, value.id.toHexString())
        compositeOutput.encodeStringElement(descriptor, 1, value.appInstanceId)

        compositeOutput.encodeLongElement(descriptor, 2, value.clipId)
        value.clipAppearContent?.let {
            compositeOutput.encodeSerializableElement(descriptor, 3, RealmAnyKSerializer, it)
        }
        value.clipContent?.let {
            compositeOutput.encodeSerializableElement(descriptor, 4, ClipContent.serializer(), it)
        }
        compositeOutput.encodeIntElement(descriptor, 5, value.clipType)
        compositeOutput.encodeLongElement(descriptor, 6, value.size)
        compositeOutput.encodeStringElement(descriptor, 7, value.md5)
        compositeOutput.encodeBooleanElement(descriptor, 8, value.isFavorite)
        compositeOutput.encodeSerializableElement(descriptor, 9, RealmSetKSerializer(ClipLabel.serializer()), value.labels)
        compositeOutput.endStructure(descriptor)
    }
}
