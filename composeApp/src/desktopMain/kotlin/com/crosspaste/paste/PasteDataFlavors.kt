package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor

object PasteDataFlavors {

    val URI_LIST = DataFlavor("text/uri-list;class=java.lang.String")

    val GNOME_COPIED_FILES_FLAVOR = DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream")
}
