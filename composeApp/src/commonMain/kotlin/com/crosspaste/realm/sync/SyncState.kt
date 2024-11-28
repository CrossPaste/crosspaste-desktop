package com.crosspaste.realm.sync

class SyncState {

    companion object {
        const val CONNECTED = 0
        const val CONNECTING = 1
        const val DISCONNECTED = 2
        const val UNMATCHED = 3
        const val UNVERIFIED = 4
        const val INCOMPATIBLE = 5
    }
}
