package com.crosspaste.realm.paste

import com.crosspaste.dto.paste.SyncPasteLabel
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.ObjectId

@Serializable
@SerialName("label")
class PasteLabel : RealmObject {

    companion object {

        fun toSyncPasteLabel(pasteLabel: PasteLabel): SyncPasteLabel {
            return SyncPasteLabel(
                id = pasteLabel.id.toHexString(),
                color = pasteLabel.color,
                text = pasteLabel.text,
            )
        }

        fun fromSyncPasteLabel(syncPasteLabel: SyncPasteLabel): PasteLabel {
            return PasteLabel().apply {
                this.id = ObjectId(syncPasteLabel.id)
                this.color = syncPasteLabel.color
                this.text = syncPasteLabel.text
            }
        }
    }

    @PrimaryKey
    @Transient
    var id: ObjectId = ObjectId()
    var color: Int = 0

    @Index
    var text: String = ""

    @Transient
    var createTime: RealmInstant = RealmInstant.now()

    constructor()

    constructor(color: Int, text: String) {
        this.color = color
        this.text = text
    }
}
