package com.clipevery.serializer

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

object ClipDataSerializer: KSerializer<ClipData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClipData") {
        element<Long>("clipId")
        element<RealmAny?>("clipAppearContent")
        element<ClipContent?>("clipContent")
        element<Int>("clipType")
        element<String>("md5")
        element<String>("appInstanceId")
        element<RealmSet<ClipLabel>>("labels")
    }

    override fun deserialize(decoder: Decoder): ClipData {
        val dec = decoder.beginStructure(descriptor)
        var clipId = 0L
        var clipAppearContent: RealmAny? = null
        var clipContent: ClipContent? = null
        var clipType = 0
        var md5 = ""
        var appInstanceId = ""
        var labels: RealmSet<ClipLabel> = realmSetOf()
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                0 -> clipId = dec.decodeLongElement(descriptor, index)
                1 -> clipAppearContent = dec.decodeSerializableElement(descriptor, index, RealmAnyKSerializer)
                2 -> clipContent = dec.decodeSerializableElement(descriptor, index, ClipContent.serializer())
                3 -> clipType = dec.decodeIntElement(descriptor, index)
                4 -> md5 = dec.decodeStringElement(descriptor, index)
                5 -> appInstanceId = dec.decodeStringElement(descriptor, index)
                6 -> labels = dec.decodeSerializableElement(descriptor, index, RealmSetKSerializer(ClipLabel.serializer()))
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)
        return ClipData().apply {
            this.clipId = clipId
            this.clipAppearContent = clipAppearContent
            this.clipContent = clipContent
            this.clipType = clipType
            this.clipSearchContent = ClipContent.getClipItem(clipAppearContent)?.getSearchContent()
            this.md5 = md5
            this.appInstanceId = appInstanceId
            this.createTime = RealmInstant.now()
            this.labels = labels
            this.clipState = ClipState.LOADING
            this.isRemote = true
        }
    }

    override fun serialize(encoder: Encoder, value: ClipData) {
        val compositeOutput = encoder.beginStructure(descriptor)
        compositeOutput.encodeLongElement(descriptor, 0, value.clipId)
        value.clipAppearContent?.let {
            compositeOutput.encodeSerializableElement(descriptor, 1, RealmAnyKSerializer, it)
        }
        value.clipContent?.let {
            compositeOutput.encodeSerializableElement(descriptor, 2, ClipContent.serializer(), it)
        }
        compositeOutput.encodeIntElement(descriptor, 3, value.clipType)
        compositeOutput.encodeStringElement(descriptor, 4, value.md5)
        compositeOutput.encodeStringElement(descriptor, 5, value.appInstanceId)
        compositeOutput.encodeSerializableElement(descriptor, 6, RealmSetKSerializer(ClipLabel.serializer()), value.labels)
        compositeOutput.endStructure(descriptor)
    }
}