package com.crosspaste

import org.koin.core.module.Module

interface CrossPasteModule {

    fun appModule(): Module

    fun realmModule(): Module

    fun networkModule(): Module

    fun securityModule(): Module

    fun pasteTypePluginModule(): Module

    fun pasteComponentModule(): Module

    fun uiModule(): Module

    fun viewModelModule(): Module
}
