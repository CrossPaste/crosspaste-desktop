package com.clipevery.clip

import com.clipevery.dao.clip.ClipData
import java.awt.datatransfer.Transferable

interface TransferableProducer {

    fun produce(clipData: ClipData): Transferable

}