package com.crosspaste.path

import okio.Path

/**
 * The form an absolute directory is stored in as a [com.crosspaste.paste.item.PasteFiles.basePath].
 * Platforms where stored absolute paths stay valid store it as is; a platform
 * whose app storage moves between launches (the iOS app container) stores the
 * moving part as a placeholder.
 */
expect fun encodeStoredPath(path: String): String

/** Inverse of [encodeStoredPath], against the storage locations of this launch. */
expect fun decodeStoredPath(storedPath: String): Path
