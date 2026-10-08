package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor

object PasteDataFlavors {

    val URI_LIST_FLAVOR = DataFlavor("text/uri-list;class=java.io.InputStream")

    val URL_FLAVOR = DataFlavor("application/x-java-url; class=java.net.URL")

    val GNOME_COPIED_FILES_FLAVOR = DataFlavor("x-special/gnome-copied-files;class=java.io.InputStream")

    /**
     * Linux only: tags every clipboard write of ours with an extra target. AWT
     * exports a stream-backed flavor under its bare MIME type as an X11 target,
     * and the compositor's XWayland bridge carries target names into the Wayland
     * offer unchanged, so the native Wayland clipboard monitor can tell the
     * bridged echo of our own write from a real copy.
     */
    const val CROSSPASTE_ORIGIN_MIME = "application/x-crosspaste-origin"

    val CROSSPASTE_ORIGIN_FLAVOR = DataFlavor("$CROSSPASTE_ORIGIN_MIME;class=java.io.InputStream")
}

object LocalOnlyFlavor : DataFlavor("application/x-local-only-flavor;class=java.lang.Boolean", "Local Only Flavor") {
    private fun readResolve(): Any = LocalOnlyFlavor
}
