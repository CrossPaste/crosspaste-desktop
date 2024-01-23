package com.clipevery.dao.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ClipLabel: RealmObject {
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var color: Int = 0
    @Index
    var text: String = ""
    var createTime: RealmInstant = RealmInstant.now()

    constructor()

    constructor(color: Color, text: String) {
        this.color = color.toArgb()
        this.text = text
    }

    fun getColor(): Color {
        return Color(color)
    }
}
