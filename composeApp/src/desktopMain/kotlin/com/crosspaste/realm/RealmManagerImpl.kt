package com.crosspaste.realm

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteCollection
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteLabel
import com.crosspaste.dao.signal.PasteIdentityKey
import com.crosspaste.dao.signal.PastePreKey
import com.crosspaste.dao.signal.PasteSession
import com.crosspaste.dao.signal.PasteSignedPreKey
import com.crosspaste.dao.sync.HostInfo
import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.dao.task.PasteTask
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.PathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.TypedRealmObject
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
                PasteIdentityKey::class,
                PastePreKey::class,
                PasteSession::class,
                PasteSignedPreKey::class,
            )

        private val PASTE_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                PasteData::class,
                PasteCollection::class,
                PasteLabel::class,
                FilesPasteItem::class,
                HtmlPasteItem::class,
                ImagesPasteItem::class,
                TextPasteItem::class,
                UrlPasteItem::class,
            )

        private val TASK_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                PasteTask::class,
            )

        fun createRealmManager(pathProvider: PathProvider): RealmManager {
            val path = pathProvider.resolve(appFileType = AppFileType.DATA)
            val builder =
                RealmConfiguration.Builder(DTO_TYPES + SIGNAL_TYPES + PASTE_TYPES + TASK_TYPES)
                    .directory(path.toString())
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
