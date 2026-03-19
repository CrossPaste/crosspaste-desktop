package com.crosspaste.utils

import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.DelayedDeleteExtraInfo
import com.crosspaste.db.task.PasteTaskExtraInfo
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.SwitchLanguageInfo
import com.crosspaste.db.task.SyncExtraInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Extended JSON instance that includes PasteTaskExtraInfo polymorphic registration,
 * building on top of core's JSON serializer modules.
 */
object SharedJsonUtils {

    val JSON: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            serializersModule =
                getJsonUtils().JSON.serializersModule.overwriteWith(
                    SerializersModule {
                        polymorphic(PasteTaskExtraInfo::class) {
                            subclass(BaseExtraInfo::class)
                            subclass(SyncExtraInfo::class)
                            subclass(PullExtraInfo::class)
                            subclass(SwitchLanguageInfo::class)
                            subclass(DelayedDeleteExtraInfo::class)
                        }
                    },
                )
        }
}
