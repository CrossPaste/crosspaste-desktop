package com.clipevery.signal

interface SignalProcessorCache {

    fun getSignalMessageProcessor(appInstanceId: String): SignalMessageProcessor

    fun removeSignalMessageProcessor(appInstanceId: String)
}
