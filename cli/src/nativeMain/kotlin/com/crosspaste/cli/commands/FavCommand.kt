package com.crosspaste.cli.commands

import com.crosspaste.db.paste.PasteDao
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long

class FavCommand : CliktCommand(name = "fav") {

    override fun help(context: Context): String = "Toggle favorite status of a paste"

    private val id by argument(help = "Paste ID to toggle favorite").long()

    override fun run() =
        runWithDao {
            val pasteDao = getDao<PasteDao>()
            val paste = pasteDao.getNoDeletePasteData(id)
            if (paste == null) {
                echo("Paste #$id not found.", err = true)
                throw ProgramResult(1)
            }
            val newFavorite = !paste.favorite
            pasteDao.setFavorite(id, newFavorite)
            val status = if (newFavorite) "favorited" else "unfavorited"
            echo("Paste #$id $status.")
        }
}
