package com.crosspaste.path

import com.crosspaste.utils.getPlatformUtils
import okio.Path

private val CONTAINER_UUID = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")

/**
 * iOS keeps an app's data under `.../Containers/Data/Application/<UUID>/`, and the
 * UUID is not stable: it can change across app updates, restores and device
 * migration. A stored absolute path with an old UUID still names the right file
 * relative to the container, so it is moved under the container the system
 * Downloads dir (Documents on iOS) currently lives in.
 *
 * Paths outside such a container, i.e. every path off iOS, are returned unchanged.
 */
fun relocateToAppContainer(storedPath: Path): Path =
    if (storedPath.segments.appContainerEnd() == null) {
        storedPath
    } else {
        relocateToAppContainer(storedPath, getPlatformUtils().getSystemDownloadDir())
    }

/**
 * Moves [storedPath] under the app container [currentPath] lives in. Returns
 * [storedPath] unchanged when either path is not inside an app container.
 */
fun relocateToAppContainer(
    storedPath: Path,
    currentPath: Path,
): Path {
    val storedSegments = storedPath.segments
    val storedEnd = storedSegments.appContainerEnd() ?: return storedPath
    val currentSegments = currentPath.segments
    val currentEnd = currentSegments.appContainerEnd() ?: return storedPath
    var currentContainer = currentPath
    repeat(currentSegments.size - currentEnd) {
        currentContainer = currentContainer.parent ?: return storedPath
    }
    return storedSegments.drop(storedEnd).fold(currentContainer) { path, segment -> path / segment }
}

/** Index just past `Containers/Data/Application/<UUID>`, or null if absent. */
private fun List<String>.appContainerEnd(): Int? {
    for (i in 0..size - 4) {
        if (this[i] == "Containers" &&
            this[i + 1] == "Data" &&
            this[i + 2] == "Application" &&
            CONTAINER_UUID.matches(this[i + 3])
        ) {
            return i + 4
        }
    }
    return null
}
