package com.clipevery.controller

import com.clipevery.dao.SyncDao
import com.clipevery.encrypt.decodePreKeyBundle
import com.clipevery.exception.ClipException
import com.clipevery.exception.StandardErrorCode
import com.clipevery.model.sync.RequestSyncInfo
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.io.IOException
import java.lang.Exception

class SyncController(private val syncValidator: SyncValidator,
                     private val syncDao: SyncDao) {

    fun receiveEndpoint(requestSyncInfo: RequestSyncInfo): RequestSyncInfo {
        val validateResult = syncValidator.validate(requestSyncInfo)
        when (validateResult) {
            ValidateResult.TIMEOUT -> {
                throw ClipException(StandardErrorCode.SYNC_TIMEOUT.toErrorCode())
            }
            ValidateResult.INVALID -> {
                throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode())
            }

            ValidateResult.SUCCESS -> {
                try {
                    val appInfo = requestSyncInfo.appInfo
                    val preKeyBundleBytes = requestSyncInfo.preKeyBundle
                    val requestEndpointInfo = requestSyncInfo.requestEndpointInfo
                    val preKeyBundle: PreKeyBundle = decodePreKeyBundle(preKeyBundleBytes)
                    syncDao.saveSyncEndpoint(appInfo, requestEndpointInfo, preKeyBundle)
                }  catch (e: IOException) {
                    throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode())
                } catch (e: InvalidKeyException) {
                    throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode())
                } catch (e: Exception) {
                    throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode())
                }
            }
        }
    }
}

class SyncValidator(private val refreshTime: Long) {
    private var salt: Int = 0
    private var generateTime: Long = 0

    fun getSalt(): Int {
        salt = (0..999999).random()
        generateTime = System.currentTimeMillis()
        return salt
    }

    fun validate(requestSyncInfo: RequestSyncInfo): ValidateResult {
        val currentTimeMillis = System.currentTimeMillis()
        return if (currentTimeMillis - generateTime > refreshTime) {
            ValidateResult.TIMEOUT
        } else if (requestSyncInfo.salt != this.salt) {
            ValidateResult.INVALID
        } else {
            ValidateResult.SUCCESS
        }
    }

    fun getRefreshTime(): Long {
        return refreshTime
    }
}

enum class ValidateResult {
    TIMEOUT,
    INVALID,
    SUCCESS,
}