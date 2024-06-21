package com.clipevery.signal

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.util.PriorityQueue

class SignalMessageProcessorImpl(
    appInstanceId: String,
    signalProtocolStore: SignalProtocolStore,
) : SignalMessageProcessor {

    private val mutex = Mutex()

    private var expectedCounter = 0

    override val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)

    private val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

    private val queue: PriorityQueue<SignalMessage> = PriorityQueue(compareBy { it.counter })

    override fun encrypt(data: ByteArray): CiphertextMessage {
        return sessionCipher.encrypt(data)
    }

    override suspend fun decrypt(signalMessage: SignalMessage): ByteArray {
        val counter = signalMessage.counter
        mutex.withLock {
            queue.add(signalMessage)
        }
        return processQueue(counter)
    }

    override suspend fun decrypt(preKeySignalMessage: PreKeySignalMessage): ByteArray {
        return mutex.withLock {
            val result = sessionCipher.decrypt(preKeySignalMessage)
            expectedCounter = 0
            result
        }
    }

    private suspend fun processQueue(counter: Int): ByteArray {
        val timeoutDuration = 10L
        val waitStartTime = System.currentTimeMillis()

        while (true) {
            mutex.withLock {
                queue.peek()?.let { topMessage ->
                    when {
                        // 检查顶部消息是否是期望的counter，并且符合initialCounter
                        topMessage.counter == expectedCounter && topMessage.counter == counter -> {
                            queue.poll() // 符合期望，取出处理
                            expectedCounter++ // 更新期望counter
                            return@withLock sessionCipher.decrypt(topMessage) // 解密并准备返回
                        }
                        // 超时检查，如果超过10毫秒且顶部消息是initialCounter，则更新期望counter
                        System.currentTimeMillis() - waitStartTime > timeoutDuration && topMessage.counter == counter -> {
                            queue.poll() // 超时且匹配，取出消息
                            expectedCounter = topMessage.counter + 1 // 更新期望counter
                            return@withLock sessionCipher.decrypt(topMessage) // 解密并返回
                        }

                        else -> {}
                    }
                }
            } ?: delay(1) // 如果没有合适的消息处理，稍作延迟后继续检查
        }
    }
}
