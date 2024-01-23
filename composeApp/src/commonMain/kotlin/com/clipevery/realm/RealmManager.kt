package com.clipevery.realm

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipLabel
import com.clipevery.dao.signal.ClipIdentityKey
import com.clipevery.dao.signal.ClipPreKey
import com.clipevery.dao.signal.ClipSession
import com.clipevery.dao.signal.ClipSignedPreKey
import com.clipevery.dao.sync.HostInfo
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.path.PathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.TypedRealmObject
import kotlin.io.path.pathString
import kotlin.reflect.KClass

class RealmManager private constructor(private val config: RealmConfiguration) {

    companion object {

        private val DTO_TYPES: Set<KClass<out TypedRealmObject>> = setOf(
            SyncRuntimeInfo::class,
            HostInfo::class
        )

        private val SIGNAL_TYPES: Set<KClass<out TypedRealmObject>> = setOf(
            ClipIdentityKey::class,
            ClipPreKey::class,
            ClipSession::class,
            ClipSignedPreKey::class
        )

        private val CLIP_TYPES: Set<KClass<out TypedRealmObject>> = setOf(
            ClipData::class,
            ClipContent::class,
            ClipLabel::class
        )

        fun createRealmManager(pathProvider: PathProvider): RealmManager {
            val path = pathProvider.resolve(appFileType = AppFileType.DATA)
            val config = RealmConfiguration.Builder(DTO_TYPES + SIGNAL_TYPES + CLIP_TYPES)
                .directory(path.pathString)
                .name("clipevery.realm")
                .schemaVersion(1)
                .build()
            return RealmManager(config)
        }
    }

    private val logger = KotlinLogging.logger {}

    val realm: Realm by lazy {
        createRealm()
    }

    private fun createRealm(): Realm {
        try {
            return Realm.open(config)
        } finally {
            logger.info { "RealmManager createRealm - ${config.path}" }
        }
    }
}
