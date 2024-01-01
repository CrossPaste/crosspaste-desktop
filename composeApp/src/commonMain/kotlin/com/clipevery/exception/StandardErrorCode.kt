package com.clipevery.exception

enum class StandardErrorCode(code: Int, errorType: ErrorType): ErrorCodeSupplier {
    UNKNOWN_ERROR(0, ErrorType.INTERNAL_ERROR),
    BOOTSTRAP_ERROR(1, ErrorType.INTERNAL_ERROR),
    INVALID_PARAMETER(2, ErrorType.USER_ERROR),

    SYNC_TIMEOUT(1000, ErrorType.USER_ERROR),
    SYNC_INVALID(1001, ErrorType.USER_ERROR),
    NOT_FOUND_APP_INSTANCE_ID(1002, ErrorType.INTERNAL_ERROR)
    ;

    private val errorCode: ErrorCode

    init {
        errorCode = ErrorCode(code, name, errorType)
    }

    override fun toErrorCode(): ErrorCode {
        return errorCode
    }

}
