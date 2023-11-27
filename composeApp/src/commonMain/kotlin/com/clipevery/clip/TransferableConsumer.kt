package com.clipevery.clip

import java.awt.datatransfer.Transferable

interface TransferableConsumer {

    fun consume(transferable: Transferable)
}