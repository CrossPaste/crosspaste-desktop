package com.crosspaste.realm

import com.crosspaste.path.UserDataPathProvider
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

class DesktopRealmManagerFactory(
    override val userDataPathProvider: UserDataPathProvider,
) : RealmManagerFactory() {

    override val schemaVersion: Long = 4

    override fun getSchema(): Set<KClass<out TypedRealmObject>> {
        return DTO_TYPES + SECURE_TYPES + PASTE_TYPES + TASK_TYPES
    }
}
