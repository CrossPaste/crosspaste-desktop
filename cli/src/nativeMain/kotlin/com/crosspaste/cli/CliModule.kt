package com.crosspaste.cli

import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.platform.CliConfigReader
import com.crosspaste.cli.platform.NativePlatformPathProvider
import com.crosspaste.cli.platform.createNativePlatformPathProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val cliModule =
    module {
        single<NativePlatformPathProvider> { createNativePlatformPathProvider() }
        singleOf(::CliConfigReader)
        factory { CliClient(get()) }
    }
