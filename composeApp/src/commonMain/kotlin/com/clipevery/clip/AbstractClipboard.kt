package com.clipevery.clip

import java.awt.datatransfer.Transferable
import java.util.function.Consumer

interface AbstractClipboard: Runnable, ClipboardMonitor {

  val clipConsumer: Consumer<Transferable>

}