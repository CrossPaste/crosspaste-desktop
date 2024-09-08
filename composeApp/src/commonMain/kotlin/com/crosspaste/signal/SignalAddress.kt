package com.crosspaste.signal

data class SignalAddress(val name: String, val deviceId: Int) {

    override fun toString(): String {
        return "$name.$deviceId"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalAddress) return false

        if (name != other.name) return false
        if (deviceId != other.deviceId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + deviceId
        return result
    }
}
