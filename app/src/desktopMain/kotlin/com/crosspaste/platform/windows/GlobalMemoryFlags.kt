package com.crosspaste.platform.windows

/**
 * Memory allocation flags
 */
object GlobalMemoryFlags {
    const val GMEM_FIXED = 0x0000
    const val GMEM_MOVEABLE = 0x0002
    const val GMEM_ZEROINIT = 0x0040
    const val GMEM_MODIFY = 0x0080
    const val GMEM_DISCARDABLE = 0x0100
    const val GMEM_NOT_BANKED = 0x1000
    const val GMEM_SHARE = 0x2000
    const val GMEM_DDESHARE = 0x2000
    const val GMEM_NOTIFY = 0x4000
    const val GMEM_LOWER = GMEM_NOT_BANKED
    const val GMEM_VALID_FLAGS = 0x7F72
    const val GMEM_INVALID_HANDLE = 0x8000
}
