package com.crosspaste.exception

class PasteException : RuntimeException {

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

    fun getErrorCode(): ErrorCode = errorCode

    fun match(errorCode: StandardErrorCode): Boolean = this.errorCode == errorCode.toErrorCode()
}
