package com.clipevery.exception

enum class StandardErrorCode(code: Int, errorType: ErrorType): ErrorCodeSupplier {
    UNKNOWN_ERROR(0, ErrorType.INTERNAL_ERROR),
    BOOTSTRAP_ERROR(1, ErrorType.INTERNAL_ERROR),
    INVALID_PARAMETER(2, ErrorType.USER_ERROR),


    NOT_FOUND_APP_INSTANCE_ID(1000, ErrorType.EXTERNAL_ERROR),
    TOKEN_INVALID(1001, ErrorType.EXTERNAL_ERROR),

    SIGNAL_INVALID_MESSAGE(2000, ErrorType.EXTERNAL_ERROR),
    SIGNAL_INVALID_KEY_ID(2001, ErrorType.EXTERNAL_ERROR),
    SIGNAL_INVALID_KEY(2002, ErrorType.EXTERNAL_ERROR),
    SIGNAL_UNTRUSTED_IDENTITY(2003, ErrorType.EXTERNAL_ERROR),
    SIGNAL_EXCHANGE_FAIL(2004, ErrorType.EXTERNAL_ERROR),

    SYNC_CLIP_ERROR(3000, ErrorType.EXTERNAL_ERROR),
    SYNC_CLIP_NOT_FOUND_RESOURCE(3001, ErrorType.EXTERNAL_ERROR),
    ;

    private val errorCode: ErrorCode

    init {
        errorCode = ErrorCode(code, name, errorType)
    }

    override fun toErrorCode(): ErrorCode {
        return errorCode
    }

}
