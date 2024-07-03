package com.crosspaste.exception

import java.util.Objects

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
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val errorCode = other as ErrorCode
        return code == errorCode.code && name == errorCode.name && type === errorCode.type
    }

    override fun hashCode(): Int {
        return Objects.hash(code, name, type)
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
