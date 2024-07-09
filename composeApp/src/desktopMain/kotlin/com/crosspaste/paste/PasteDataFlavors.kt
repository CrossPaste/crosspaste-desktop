package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor

object PasteDataFlavors {

    val URI_LIST_FLAVOR = DataFlavor("text/uri-list;class=java.io.InputStream")

    val GNOME_COPIED_FILES_FLAVOR = DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream")
}
