package com.crosspaste.cli.commands

import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.PasteItemReader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform

const val CLI_VERSION = "1.2.7"

val cliJson =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

fun CliktCommand.runWithDao(block: suspend () -> Unit) {
    runBlocking {
        try {
            block()
        } catch (e: ProgramResult) {
            throw e
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }
}

inline fun <reified T> CliktCommand.getDao(): T = KoinPlatform.getKoin().get()

fun PasteData.toSummaryDto(): PasteSummaryDto {
    val pasteTagDao = KoinPlatform.getKoin().get<PasteTagDao>()
    val pasteDataHelper = KoinPlatform.getKoin().get<PasteDataHelper>()
    return PasteSummaryDto(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        tagged = pasteTagDao.getPasteTagsBlock(id).isNotEmpty(),
        createTime = createTime,
        preview = pasteDataHelper.getSummary(this, "Loading...", ""),
        remote = remote,
    )
}

fun PasteData.toDetailResponse(): PasteDetailResponse {
    val pasteTagDao = KoinPlatform.getKoin().get<PasteTagDao>()
    val pasteItemReader = KoinPlatform.getKoin().get<PasteItemReader>()
    return PasteDetailResponse(
        id = id,
        typeName = getTypeName(),
        source = source,
        size = size,
        tagged = pasteTagDao.getPasteTagsBlock(id).isNotEmpty(),
        createTime = createTime,
        remote = remote,
        hash = hash,
        content = pasteAppearItem?.let { pasteItemReader.getSummary(it) },
    )
}

fun resolveTypeFilter(type: String?): Int? =
    type?.let { name ->
        PasteType.TYPES.firstOrNull { it.name.equals(name, ignoreCase = true) }?.type
    }

@OptIn(ExperimentalForeignApi::class)
fun formatRelativeTime(epochMillis: Long): String {
    val now = platform.posix.time(null) * 1000L
    val diff = now - epochMillis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> {
            val months = days / 30
            "${months}mo ago"
        }
    }
}

fun formatSize(bytes: Long): String =
    when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024 * 1024 * 1024)}GB"
    }
