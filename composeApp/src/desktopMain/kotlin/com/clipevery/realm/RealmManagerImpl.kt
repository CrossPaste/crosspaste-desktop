package com.clipevery.realm

import com.clipevery.app.AppFileType
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipLabel
import com.clipevery.dao.clip.ClipResource
import com.clipevery.dao.signal.ClipIdentityKey
import com.clipevery.dao.signal.ClipPreKey
import com.clipevery.dao.signal.ClipSession
import com.clipevery.dao.signal.ClipSignedPreKey
import com.clipevery.dao.sync.HostInfo
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.task.ClipTask
import com.clipevery.path.PathProvider
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
                ClipResource::class,
                ClipData::class,
                ClipContent::class,
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
            val config =
                RealmConfiguration.Builder(DTO_TYPES + SIGNAL_TYPES + CLIP_TYPES + TASK_TYPES)
                    .directory(path.pathString)
                    .name("clipevery.realm")
                    .schemaVersion(1)
                    .build()
            return RealmManagerImpl(config)
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
