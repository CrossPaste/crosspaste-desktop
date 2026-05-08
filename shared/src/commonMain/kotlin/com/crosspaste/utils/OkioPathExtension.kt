package com.crosspaste.utils

import okio.Path

val Path.fileNameRemoveExtension: String
    get() {
        val fileName = name

        val index = fileName.lastIndexOf('.')

        return if (index != -1) {
            fileName.substring(0, index)
        } else {
            fileName
        }
    }

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
        val metadata = getFileUtils().fileSystem.metadata(this)
        return metadata.isDirectory
    }

val Path.safeIsDirectory: Boolean
    get() {
        return runCatching {
            this.isDirectory
        }.getOrElse {
            false
        }
    }

val Path.noOptionParent: Path
    get() {
        return this.parent ?: this
    }

val imageExtensions =
    setOf(
        "png",
        "jpg",
        "jpeg",
        "gif",
        "bmp",
        "webp",
        "heic",
        "heif",
        "tiff",
        "svg",
    )

val videoExtensions =
    setOf(
        "mp4",
        "mov",
        "m4v",
        "mkv",
        "webm",
        "avi",
        "wmv",
        "flv",
        "ts",
        "mts",
        "m2ts",
        "3gp",
        "mpg",
        "mpeg",
        "vob",
        "ogv",
    )

val Path.isImageFile: Boolean
    get() = extension.lowercase() in imageExtensions

val Path.isVideoFile: Boolean
    get() = extension.lowercase() in videoExtensions
