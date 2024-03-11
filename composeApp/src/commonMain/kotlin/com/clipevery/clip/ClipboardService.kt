package com.clipevery.clip

import com.clipevery.dao.clip.ClipData
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner

interface ClipboardService: ClipboardMonitor, ClipboardOwner {

  val systemClipboard: Clipboard

  val clipConsumer: TransferableConsumer

  val clipProducer: TransferableProducer

  fun setContent(clipData: ClipData)

}