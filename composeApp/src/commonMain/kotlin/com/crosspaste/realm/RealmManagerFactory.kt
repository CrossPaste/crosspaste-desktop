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
import com.crosspaste.realm.secure.CryptPublicKey
import com.crosspaste.realm.sync.HostInfo
import com.crosspaste.realm.sync.SyncRuntimeInfo
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.TypedRealmObject
import okio.Path
import kotlin.reflect.KClass

abstract class RealmManagerFactory {

    companion object {
        val DTO_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                SyncRuntimeInfo::class,
                HostInfo::class,
            )

        val SECURE_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                CryptPublicKey::class,
            )

        val PASTE_TYPES: Set<KClass<out TypedRealmObject>> =
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

        val TASK_TYPES: Set<KClass<out TypedRealmObject>> =
            setOf(
                com.crosspaste.realm.task.PasteTask::class,
            )
    }

    private val name = "crosspaste.realm"

    abstract val schemaVersion: Long

    abstract val userDataPathProvider: UserDataPathProvider

    fun createRealmManager(): RealmManager {
        val path = userDataPathProvider.resolve(appFileType = AppFileType.DATA)
        return RealmManager(path, ::createRealmConfig)
    }

    fun createRealmConfig(path: Path): RealmConfiguration {
        return RealmConfiguration.Builder(getSchema())
            .directory(path.toString())
            .name(name)
            .schemaVersion(schemaVersion)
            .build()
    }

    abstract fun getSchema(): Set<KClass<out TypedRealmObject>>
}
