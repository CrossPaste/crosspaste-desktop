package com.clipevery.exception

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Objects

class ErrorCode @JsonCreator constructor(
    @JsonProperty("code") code: Int,
    @JsonProperty("name") name: String,
    @JsonProperty("type") type: ErrorType) {
    @get:JsonProperty
    val code: Int

    @get:JsonProperty
    val name: String
    private val type: ErrorType

    init {
        require(code >= 0) { "code is negative" }
        this.code = code
        this.name = name
        this.type = type
    }

    @JsonProperty
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