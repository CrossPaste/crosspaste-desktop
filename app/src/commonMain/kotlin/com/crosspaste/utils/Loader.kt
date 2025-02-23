package com.crosspaste.utils

interface Loader<T, R> {

    fun load(value: T): R?
}
