package com.crosspaste.utils

interface Loader<T, R> {

    suspend fun load(value: T): R?
}
