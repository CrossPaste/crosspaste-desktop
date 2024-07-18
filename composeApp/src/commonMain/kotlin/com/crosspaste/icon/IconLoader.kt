package com.crosspaste.icon

import okio.Path

interface IconLoader<T, R> {

    fun load(value: T): R?
}

interface FaviconLoader : IconLoader<String, Path>

interface FileExtIconLoader : IconLoader<String, Path>
