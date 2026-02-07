package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor

object PasteDataFlavors {

    val URI_LIST_FLAVOR = DataFlavor("text/uri-list;class=java.io.InputStream")

    val URL_FLAVOR = DataFlavor("application/x-java-url; class=java.net.URL")

    val GNOME_COPIED_FILES_FLAVOR = DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream")
}

object LocalOnlyFlavor : DataFlavor("application/x-local-only-flavor;class=java.lang.Boolean", "Local Only Flavor") {
    private fun readResolve(): Any = LocalOnlyFlavor
}
