package com.crosspaste.realm

import com.crosspaste.path.UserDataPathProvider
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.TypedRealmObject
import okio.Path
import kotlin.reflect.KClass

class DesktopRealmManagerFactory(
    override val userDataPathProvider: UserDataPathProvider,
) : RealmManagerFactory() {

    override val schemaVersion: Long = 4

    override fun createRealmConfig(path: Path): RealmConfiguration {
        return RealmConfiguration.Builder(getSchema())
            .directory(path.toString())
            .name(CROSSPASTE_REALM_NAME)
            .schemaVersion(schemaVersion)
            .build()
    }

    override fun getSchema(): Set<KClass<out TypedRealmObject>> {
        return DTO_TYPES + SECURE_TYPES + PASTE_TYPES + TASK_TYPES
    }
}
