package com.clipevery.exception

import java.util.Objects

class ErrorCode constructor(
    code: Int,
    name: String,
    type: ErrorType) {
    val code: Int

    val name: String
    private val type: ErrorType

    init {
        require(code >= 0) { "code is negative" }
        this.code = code
        this.name = name
        this.type = type
    }

    fun getType(): ErrorType {
        return type
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
    USER_ERROR,
    INTERNAL_ERROR,
    EXTERNAL
}


interface ErrorCodeSupplier {
    fun toErrorCode(): ErrorCode
}