package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clipevery.listener.GlobalListener

class DesktopMessageManager(
    private val globalListener: GlobalListener,
) : MessageManager {

    override var messageId by mutableStateOf(0)

    override fun getCurrentMessageView(): (@Composable () -> Unit)? {
        globalListener.errorCode?.let { code ->
            val factory = globalListener.getComposeMessageViewFactory()
            if (factory.showMessage) {
                return {
                    factory.MessageView(code)
                }
            }
        }
        return null
    }
}
