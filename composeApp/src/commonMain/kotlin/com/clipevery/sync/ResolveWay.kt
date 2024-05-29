package com.clipevery.sync

enum class ResolveWay {
    MANUAL,
    AUTO_FORCE,
    AUTO_NO_FORCE,
    ;

    fun isForce(): Boolean {
        return this == AUTO_FORCE || this == MANUAL
    }

    fun isAuto(): Boolean {
        return this == AUTO_FORCE || this == AUTO_NO_FORCE
    }
}
