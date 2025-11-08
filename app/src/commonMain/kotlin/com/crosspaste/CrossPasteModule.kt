package com.crosspaste

import org.koin.core.module.Module

interface CrossPasteModule {

    fun appModule(): Module

    fun extensionModule(): Module

    fun sqlDelightModule(): Module

    fun networkModule(): Module

    fun securityModule(): Module

    fun pasteTypePluginModule(): Module

    fun pasteComponentModule(): Module

    fun uiModule(): Module

    fun viewModelModule(): Module
}
