package com.crosspaste.cli

import com.github.ajalt.clikt.core.main
import org.koin.core.context.startKoin

fun main(args: Array<String>) {
    startKoin {
        modules(cliModule)
    }
    CrossPasteCommand().main(args)
}
