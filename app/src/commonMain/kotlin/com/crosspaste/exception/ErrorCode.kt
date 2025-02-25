package com.crosspaste.exception

class ErrorCode(
    code: Int,
    name: String,
    type: ErrorType,
) {
    val code: Int
    val name: String
    val type: ErrorType

    init {
        require(code >= 0) { "code is negative" }
        this.code = code
        this.name = name
        this.type = type
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other !is ErrorCode) {
            return false
        }
        return code == other.code && name == other.name && type === other.type
    }

    override fun hashCode(): Int {
        return arrayOf(code, name, type).hashCode()
    }
}

enum class ErrorType {
    EXTERNAL_ERROR,
    INTERNAL_ERROR,
    USER_ERROR,
}

interface ErrorCodeSupplier {
    fun toErrorCode(): ErrorCode
}
