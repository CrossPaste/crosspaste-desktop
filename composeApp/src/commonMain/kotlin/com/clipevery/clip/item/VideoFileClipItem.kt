package com.clipevery.clip.item

import java.io.File

val SUPPORT_VIDEO_EXTENSIONS = setOf("mp4", "avi", "mov", "mkv", "flv", "wmv")

class VideoFileClipItem(override val file: File, override val text: String): FileClipItem(file, text) {
    override val clipItemType: ClipItemType = ClipItemType.VideoFile
}