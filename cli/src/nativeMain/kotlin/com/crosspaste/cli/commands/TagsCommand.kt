package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import io.ktor.client.call.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class TagSummary(
    val id: Long,
    val name: String,
    val color: Long,
)

@Serializable
data class CreateTagRequest(
    val name: String,
    val color: Long? = null,
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
        runWithClient { client ->
            val response = client.get("/cli/tags")
            handleResponse(response) { resp ->
                val tags = resp.body<List<TagSummary>>()
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
        runWithClient { client ->
            val body =
                Json.encodeToString(
                    CreateTagRequest.serializer(),
                    CreateTagRequest(name = name),
                )
            val response = client.post("/cli/tags", body)
            handleResponse(response) { resp ->
                val tag = resp.body<TagSummary>()
                echo("Tag '${tag.name}' created (id=${tag.id}).")
            }
        }
}

class TagDeleteCommand : CliktCommand(name = "delete") {

    override fun help(context: Context): String = "Delete a tag"

    private val id by argument(help = "Tag ID to delete").long()

    override fun run() =
        runWithClient { client ->
            val response = client.delete("/cli/tags/$id")
            handleResponse(response) {
                echo("Tag #$id deleted.")
            }
        }
}
