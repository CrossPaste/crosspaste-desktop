package com.crosspaste.utils

sealed class Box<out T> {

    companion object {
        fun <T> of(value: T?): Box<T> =
            if (value != null) {
                Present(value)
            } else {
                Empty
            }
    }

    data class Present<T>(
        val value: T,
    ) : Box<T>()

    object Empty : Box<Nothing>()

    fun getOrNull(): T? =
        when (this) {
            is Present -> value
            Empty -> null
        }
}
