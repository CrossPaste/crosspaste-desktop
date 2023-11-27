package com.clipevery.clip

interface ClipboardService: Runnable, ClipboardMonitor {

  val clipConsumer: TransferableConsumer

}