package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.paste.PasteTag
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class TagSummary(
    val id: Long,
    val name: String,
    val color: Long,
)

class TagsCommand : CliktCommand(name = "tags") {

    override fun help(context: Context): String = "Manage paste tags"

    override val invokeWithoutSubcommand = true

    private val ctx by requireObject<CliContext>()

    init {
        subcommands(TagCreateCommand(), TagDeleteCommand())
    }

    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        runWithDao {
            val tagDao = getDao<PasteTagDao>()
            val tags = tagDao.getAllTagsFlow().first()
            val summaries = tags.map { TagSummary(id = it.id, name = it.name, color = it.color) }

            if (ctx.json) {
                echo(
                    cliJson.encodeToString(
                        ListSerializer(TagSummary.serializer()),
                        summaries,
                    ),
                )
            } else {
                printTags(summaries)
            }
        }
    }

    private fun printTags(tags: List<TagSummary>) {
        if (tags.isEmpty()) {
            echo("No tags defined.")
            return
        }
        echo("${tags.size} tag(s):")
        echo("")
        for (tag in tags) {
            echo("  #${tag.id}  ${tag.name}")
        }
    }
}

class TagCreateCommand : CliktCommand(name = "create") {

    override fun help(context: Context): String = "Create a new tag"

    private val name by argument(help = "Tag name")

    override fun run() =
        runWithDao {
            val tagDao = getDao<PasteTagDao>()
            val maxSortOrder = tagDao.getMaxSortOrder()
            val color = PasteTag.getColor(maxSortOrder + 1)
            val newId = tagDao.createPasteTag(name, color)
            echo("Tag '$name' created (id=$newId).")
        }
}

class TagDeleteCommand : CliktCommand(name = "delete") {

    override fun help(context: Context): String = "Delete a tag"

    private val id by argument(help = "Tag ID to delete").long()

    override fun run() =
        runWithDao {
            val tagDao = getDao<PasteTagDao>()
            tagDao.deletePasteTagBlock(id)
            echo("Tag #$id deleted.")
        }
}
