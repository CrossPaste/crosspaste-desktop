package com.crosspaste.icon

import okio.Path

interface IconLoader<T> {

    fun load(key: T): Path?
}

interface FaviconLoader : IconLoader<String>
