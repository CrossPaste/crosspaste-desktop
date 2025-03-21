package com.crosspaste.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriterImpl.Companion.EN
import com.crosspaste.utils.DateTimeFormatOptions
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class GlobalCopywriterImpl(private val configManager: ConfigManager) : GlobalCopywriter {

    companion object {
        val logger = KotlinLogging.logger {}

        const val EN = "en"

        const val ES = "es"

        const val JA = "ja"

        const val ZH = "zh"

        val languageList = listOf(EN, ES, JA, ZH)

        val languageMap = ConcurrentHashMap<String, Copywriter>()
    }

    init {
        val language = configManager.config.language
        if (!languageList.contains(language)) {
            configManager.updateConfig("language", EN)
        }
    }

    private var copywriter: Copywriter by mutableStateOf(
        languageMap
            .computeIfAbsent(configManager.config.language) {
                CopywriterImpl(configManager.config.language)
            },
    )

    override fun language(): String {
        return copywriter.language()
    }

    override fun switchLanguage(language: String) {
        copywriter = languageMap.computeIfAbsent(language) { CopywriterImpl(language) }
        configManager.updateConfig("language", language)
    }

    override fun getAllLanguages(): List<Language> {
        return languageList
            .map { it ->
                val copywriter = languageMap.computeIfAbsent(it) { CopywriterImpl(it) }
                val abridge = copywriter.getAbridge()
                val name = copywriter.getText("current_language")
                Language(abridge, name)
            }
    }

    override fun getText(
        id: String,
        vararg args: Any?,
    ): String {
        return copywriter.getText(id, *args)
    }

    override fun getKeys(): Set<String> {
        return copywriter.getKeys()
    }

    override fun getDate(
        date: LocalDateTime,
        options: DateTimeFormatOptions,
    ): String {
        return copywriter.getDate(date, options)
    }

    override fun getAbridge(): String {
        return copywriter.getAbridge()
    }
}

class CopywriterImpl(private val language: String) : Copywriter {

    val logger = KotlinLogging.logger {}

    private val dateUtils = getDateUtils()

    private val properties: Properties = loadProperties()

    private var currentLanguage: String = EN

    private fun load(
        properties: Properties,
        language: String,
    ) {
        properties.load(
            CopywriterImpl::class.java.getResourceAsStream("/i18n/$language.properties")
                ?.let {
                    InputStreamReader(
                        it,
                        StandardCharsets.UTF_8,
                    )
                } ?: (throw FileNotFoundException("No properties for $language")),
        )
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        currentLanguage =
            runCatching {
                load(properties, language)
                language
            }.getOrElse { e ->
                logger.error(e) { "Error loading $language properties" }
                load(properties, EN)
                EN
            }
        return properties
    }

    override fun language(): String {
        return language
    }

    override fun getText(
        id: String,
        vararg args: Any?,
    ): String {
        val value: String? = properties.getProperty(id)
        return if (value == null) {
            logger.error { "No value for $id" }
            "null"
        } else {
            value.format(*args)
        }
    }

    override fun getKeys(): Set<String> {
        return properties.keys.map { it.toString() }.toSet()
    }

    override fun getDate(
        date: LocalDateTime,
        options: DateTimeFormatOptions,
    ): String {
        return dateUtils.getDateDesc(date, options, language.toString())
    }

    override fun getAbridge(): String {
        return language
    }
}
