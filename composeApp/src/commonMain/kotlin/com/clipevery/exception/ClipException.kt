package com.clipevery.exception

class ClipException: RuntimeException {

    private val errorCode: ErrorCode

    constructor(errorCode: ErrorCode) : super() {
        this.errorCode = errorCode
    }

    constructor(errorCode: ErrorCode, message: String) : super(message) {
        this.errorCode = errorCode
    }

    constructor(errorCode: ErrorCode, message: String, cause: Throwable) : super(message, cause) {
        this.errorCode = errorCode
    }

    constructor(errorCode: ErrorCode, cause: Throwable) : super(cause) {
        this.errorCode = errorCode
    }

    fun getErrorCode(): ErrorCode {
        return errorCode
    }
}
