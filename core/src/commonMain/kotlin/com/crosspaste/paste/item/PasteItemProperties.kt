package com.crosspaste.paste.item

object PasteItemProperties {

    const val BACKGROUND = "background"

    // set when basePath is the system Downloads dir, which is resolved anew on read
    // because its absolute path can change (the iOS app container is not stable)
    const val BASE_DIR = "baseDir"

    const val BASE_DIR_DOWNLOAD = "download"

    const val MARKETING_PATH = "marketingPath"

    // user edit name of the paste item
    const val NAME = "name"

    // title of the url paste item
    const val TITLE = "title"

    // flag to force sync files to remote download directory
    const val SYNC_TO_DOWNLOAD = "syncToDownload"
}
