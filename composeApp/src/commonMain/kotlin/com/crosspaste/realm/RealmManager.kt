package com.crosspaste.realm

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteCollection
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteLabel
import com.crosspaste.realm.signal.PasteIdentityKey
import com.crosspaste.realm.signal.PastePreKey
import com.crosspaste.realm.signal.PasteSession
import com.crosspaste.realm.signal.PasteSignedPreKey
import com.crosspaste.realm.sync.HostInfo
import com.crosspaste.realm.sync.SyncRuntimeInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.TypedRealmObject
import okio.Path
import kotlin.reflect.KClass

class RealmManager private constructor(private val config: RealmConfiguration) {

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
                ColorPasteItem::class,
                FilesPasteItem::class,
                HtmlPasteItem::class,
                ImagesPasteItem::class,
                RtfPasteItem::class,
                TextPasteItem::class,
                UrlPasteItem::class,
            )

        private val TASK_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                com.crosspaste.realm.task.PasteTask::class,
            )

        private const val NAME = "crosspaste.realm"

        private const val SCHEMA_VALUE: Long = 3

        fun createRealmManager(userDataPathProvider: UserDataPathProvider): RealmManager {
            val path = userDataPathProvider.resolve(appFileType = AppFileType.DATA)
            return RealmManager(createRealmConfig(path))
        }

        fun createRealmConfig(path: Path): RealmConfiguration {
            return RealmConfiguration.Builder(DTO_TYPES + SIGNAL_TYPES + PASTE_TYPES + TASK_TYPES)
                .directory(path.toString())
                .name(NAME)
                .schemaVersion(SCHEMA_VALUE)
                .build()
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

    fun writeCopyTo(path: Path) {
        realm.writeCopyTo(createRealmConfig(path))
    }
}
