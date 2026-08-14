package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class TagSummary(
    val id: Long,
    val name: String,
    val color: Long,
)

@Serializable
data class TagCreateRequest(
    val name: String,
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
        runCli { client ->
            val tags =
                client.getBody(
                    "/cli/tags",
                    ListSerializer(TagSummary.serializer()),
                )

            if (ctx.json) {
                echo(
                    cliJson.encodeToString(
                        ListSerializer(TagSummary.serializer()),
                        tags,
                    ),
                )
            } else {
                printTags(tags)
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
        runCli { client ->
            val body = cliJson.encodeToString(TagCreateRequest.serializer(), TagCreateRequest(name))
            val tag = client.postBody("/cli/tags", body, TagSummary.serializer())
            echo("Tag '${tag.name}' created (id=${tag.id}).")
        }
}

class TagDeleteCommand : CliktCommand(name = "delete") {

    override fun help(context: Context): String = "Delete a tag"

    private val id by argument(help = "Tag ID to delete").long()

    override fun run() =
        runCli { client ->
            val response = client.deleteBody("/cli/tags/$id", MessageResponse.serializer())
            echo(response.message)
        }
}
