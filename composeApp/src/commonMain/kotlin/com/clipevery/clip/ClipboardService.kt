package com.clipevery.clip

interface ClipboardService: ClipboardMonitor {

  val clipConsumer: TransferableConsumer

}