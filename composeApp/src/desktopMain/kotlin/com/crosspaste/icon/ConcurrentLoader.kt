package com.crosspaste.icon

import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createPlatformLock
import okio.Path

interface ConcurrentLoader : IconLoader<String> {

    val lockMap: ConcurrentPlatformMap<Path, PlatformLock>

    fun resolve(key: String): Path

    fun loggerWarning(
        key: String,
        e: Exception,
    )

    fun save(
        key: String,
        path: Path,
    )

    override fun load(key: String): Path? {
        try {
            val path = resolve(key)
            val file = path.toFile()
            if (file.exists()) {
                return path
            }
            val lock = lockMap.computeIfAbsent(path) { createPlatformLock() }
            lock.lock()
            try {
                if (file.exists()) {
                    return path
                }
                save(key, path)
                return path
            } finally {
                lock.unlock()
            }
        } catch (e: Exception) {
            loggerWarning(key, e)
            return null
        }
    }
}
