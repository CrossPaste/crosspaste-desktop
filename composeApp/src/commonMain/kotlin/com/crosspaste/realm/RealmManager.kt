package com.crosspaste.realm

import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import okio.Path

class RealmManager(
    private val path: Path,
    private val createConfig: (Path) -> RealmConfiguration,
) {

    private val logger = KotlinLogging.logger {}

    val realm: Realm by lazy {
        createRealm()
    }

    private fun createRealm(): Realm {
        try {
            return Realm.open(createConfig(path))
        } finally {
            logger.info { "RealmManager createRealm - $path" }
        }
    }

    fun close() {
        realm.close()
    }

    fun writeCopyTo(copyToPath: Path) {
        realm.writeCopyTo(createConfig(copyToPath))
    }
}
