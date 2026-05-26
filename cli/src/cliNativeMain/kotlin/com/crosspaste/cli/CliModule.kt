package com.crosspaste.cli

import com.crosspaste.Database
import com.crosspaste.app.AppInfo
import com.crosspaste.cli.platform.CliConfigReader
import com.crosspaste.cli.platform.NativePlatformPathProvider
import com.crosspaste.cli.platform.createNativePlatformPathProvider
import com.crosspaste.config.AppMetadata
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.db.NativeDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.paste.SqlPasteDao
import com.crosspaste.db.paste.SqlPasteTagDao
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.db.secure.SqlSecureDao
import com.crosspaste.db.sync.SqlSyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.task.SqlTaskDao
import com.crosspaste.db.task.TaskDao
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskSubmitter
import kotlinx.serialization.json.Json
import okio.FileSystem
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val metadataJson = Json { ignoreUnknownKeys = true }

val cliModule =
    module {
        single<NativePlatformPathProvider> { createNativePlatformPathProvider() }
        singleOf(::CliConfigReader)
    }

val cliDatabaseModule =
    module {
        // Config & path adapters
        single<CommonConfigManager> { CliConfigManager(get()) }
        single<PlatformUserDataPathProvider> { CliPlatformUserDataPathProvider(get()) }
        single<UserDataPathProvider> { UserDataPathProvider(get(), get()) }
        single<AppInfo> {
            val metadataPath =
                get<NativePlatformPathProvider>().getDefaultUserDataPath().resolve(".metadata")
            val appInstanceId =
                runCatching {
                    val content = FileSystem.SYSTEM.read(metadataPath) { readUtf8() }
                    metadataJson.decodeFromString<AppMetadata>(content).appInstanceId
                }.getOrDefault("")
            AppInfo(
                appInstanceId = appInstanceId,
                appVersion = "cli",
                appRevision = "Unknown",
                userName = "",
            )
        }

        // Database
        single<DriverFactory> {
            val configReader = get<CliConfigReader>()
            val dataPath = configReader.resolveUserDataPath().resolve("data").toString()
            NativeDriverFactory(dataPath)
        }
        single<Database> { createDatabase(get()) }

        // No-op services for CLI
        single<PasteItemReader> { DefaultPasteItemReader() }
        single<PasteDataHelper> { PasteDataHelper(get()) }
        single<SearchContentService> { NoOpSearchContentService() }
        single<TaskSubmitter> { NoOpTaskSubmitter() }

        // DAOs
        single<PasteDao> {
            SqlPasteDao(
                appInfo = get(),
                database = get(),
                pasteItemReader = get(),
                searchContentService = get(),
                taskSubmitter = get(),
                userDataPathProvider = get(),
            )
        }
        single<PasteTagDao> { SqlPasteTagDao(get()) }
        single<SyncRuntimeInfoDao> { SqlSyncRuntimeInfoDao(get()) }
        single<TaskDao> { SqlTaskDao(get()) }
        single<SecureIO> { SqlSecureDao(get()) }
    }
