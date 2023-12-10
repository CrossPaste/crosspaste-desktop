package com.clipevery.clip.item

import java.awt.datatransfer.Transferable

interface ClipItem {
    val clipItemType: ClipItemType

    fun toTransferable(): Transferable
}