package com.crosspaste.utils

import okio.FileSystem
import okio.Path

val Path.extension: String
    get() {
        val fileName = name

        val index = fileName.lastIndexOf('.')

        return if (index != -1 && index != fileName.length - 1) {
            fileName.substring(index + 1)
        } else {
            ""
        }
    }

val Path.isDirectory: Boolean
    get() {
        val metadata = FileSystem.SYSTEM.metadata(this)
        return metadata.isDirectory
    }

val Path.noOptionParent: Path
    get() {
        return this.parent ?: this
    }
