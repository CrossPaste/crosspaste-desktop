package com.crosspaste.clip

import com.crosspaste.dao.clip.ClipData
import java.awt.datatransfer.Transferable

interface TransferableProducer {

    fun produce(
        clipData: ClipData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
    ): Transferable?
}
