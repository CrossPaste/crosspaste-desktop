package com.crosspaste.cli

import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.commands.AppAutoStarter
import com.crosspaste.cli.platform.AppLauncher
import com.crosspaste.cli.platform.AppReadinessChecker
import com.crosspaste.cli.platform.CliAppPathProvider
import com.crosspaste.cli.platform.CliConfigReader
import com.crosspaste.cli.platform.NativePlatformPathProvider
import com.crosspaste.cli.platform.createAppLauncher
import com.crosspaste.cli.platform.createNativePlatformPathProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val cliModule =
    module {
        single<NativePlatformPathProvider> { createNativePlatformPathProvider() }
        singleOf(::CliConfigReader)
        factory { CliClient(get()) }
        singleOf(::CliAppPathProvider)
        single<AppLauncher> { createAppLauncher(get()) }
        singleOf(::AppReadinessChecker)
        singleOf(::AppAutoStarter)
    }
