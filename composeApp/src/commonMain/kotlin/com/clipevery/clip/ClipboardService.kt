package com.clipevery.clip

import java.awt.datatransfer.Transferable
import java.util.function.Consumer

interface ClipboardService: Runnable, ClipboardMonitor {

  val clipConsumer: Consumer<Transferable>

}