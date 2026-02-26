package com.crosspaste.cli.commands

import com.crosspaste.db.paste.PasteDao
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long

class DeleteCommand : CliktCommand(name = "delete") {

    override fun help(context: Context): String = "Delete a paste by ID"

    private val id by argument(help = "Paste ID to delete").long()

    override fun run() =
        runWithDao {
            val pasteDao = getDao<PasteDao>()
            pasteDao
                .markDeletePasteData(id)
                .onSuccess { echo("Paste #$id deleted.") }
                .onFailure { echo("Failed to delete paste #$id: ${it.message}", err = true) }
        }
}
