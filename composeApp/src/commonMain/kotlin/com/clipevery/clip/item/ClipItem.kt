package com.clipevery.clip.item

import com.clipevery.clip.ClipItemType
import java.awt.datatransfer.Transferable

interface ClipItem {
    val clipItemType: ClipItemType

    fun toTransferable(): Transferable
}