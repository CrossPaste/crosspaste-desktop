package com.clipevery.clip

import java.awt.datatransfer.Transferable

interface TransferableConsumer {

    suspend fun consume(transferable: Transferable, isRemote: Boolean)
}