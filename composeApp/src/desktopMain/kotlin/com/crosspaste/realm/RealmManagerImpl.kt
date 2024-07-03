package com.crosspaste.realm

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppFileType
import com.crosspaste.clip.item.FilesClipItem
import com.crosspaste.clip.item.HtmlClipItem
import com.crosspaste.clip.item.ImagesClipItem
import com.crosspaste.clip.item.TextClipItem
import com.crosspaste.clip.item.UrlClipItem
import com.crosspaste.dao.clip.ClipCollection
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.dao.clip.ClipLabel
import com.crosspaste.dao.signal.ClipIdentityKey
import com.crosspaste.dao.signal.ClipPreKey
import com.crosspaste.dao.signal.ClipSession
import com.crosspaste.dao.signal.ClipSignedPreKey
import com.crosspaste.dao.sync.HostInfo
import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.dao.task.ClipTask
import com.crosspaste.path.PathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.TypedRealmObject
import kotlin.io.path.pathString
import kotlin.reflect.KClass

class RealmManagerImpl private constructor(private val config: RealmConfiguration) : RealmManager {

    companion object {

        private val DTO_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                SyncRuntimeInfo::class,
                HostInfo::class,
            )

        private val SIGNAL_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                ClipIdentityKey::class,
                ClipPreKey::class,
                ClipSession::class,
                ClipSignedPreKey::class,
            )

        private val CLIP_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                ClipData::class,
                ClipCollection::class,
                ClipLabel::class,
                FilesClipItem::class,
                HtmlClipItem::class,
                ImagesClipItem::class,
                TextClipItem::class,
                UrlClipItem::class,
            )

        private val TASK_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                ClipTask::class,
            )

        fun createRealmManager(pathProvider: PathProvider): RealmManager {
            val path = pathProvider.resolve(appFileType = AppFileType.DATA)
            val builder =
                RealmConfiguration.Builder(DTO_TYPES + SIGNAL_TYPES + CLIP_TYPES + TASK_TYPES)
                    .directory(path.pathString)
                    .name("crosspaste.realm")
                    .schemaVersion(1)

            if (!AppEnv.CURRENT.isProduction()) {
                builder.deleteRealmIfMigrationNeeded()
            }

            return RealmManagerImpl(builder.build())
        }
    }

    private val logger = KotlinLogging.logger {}

    override val realm: Realm by lazy {
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
