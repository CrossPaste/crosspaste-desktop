package com.clipevery.listener

import com.clipevery.ui.base.ComposeMessageViewFactory

interface GlobalListener {

    var errorCode: Int?

    fun isRegistered(): Boolean

    fun start()

    fun stop()

    fun getComposeMessageViewFactory(): ComposeMessageViewFactory
}
