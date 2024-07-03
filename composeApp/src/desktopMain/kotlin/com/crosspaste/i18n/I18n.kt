package com.crosspaste.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriterImpl.Companion.EN
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class GlobalCopywriterImpl(private val configManager: ConfigManager) : GlobalCopywriter {

    companion object {
        val logger = KotlinLogging.logger {}

        const val EN = "en"

        val languageList = listOf(EN, "es", "jp", "zh")

        val languageMap = ConcurrentHashMap<String, Copywriter>()
    }

    init {
        val language = configManager.config.language
        if (!languageList.contains(language)) {
            configManager.updateConfig { it.copy(language = EN) }
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
        configManager.updateConfig { it.copy(language = language) }
    }

    override fun getAllLanguages(): List<Language> {
        return languageList
            .map { it ->
                val copywriter = languageMap.computeIfAbsent(it) { CopywriterImpl(it) }
                val abridge = copywriter.getAbridge()
                val name = copywriter.getText("CurrentLanguage")
                Language(abridge, name)
            }
    }

    override fun getText(id: String): String {
        return copywriter.getText(id)
    }

    override fun getDate(
        date: LocalDateTime,
        detail: Boolean,
    ): String {
        return copywriter.getDate(date, detail)
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
            try {
                load(properties, language)
                language
            } catch (e: Exception) {
                logger.error(e) { "Error loading $language properties: ${e.message}" }
                load(properties, EN)
                EN
            }
        return properties
    }

    override fun language(): String {
        return language
    }

    override fun getText(id: String): String {
        val value: String? = properties.getProperty(id)
        return if (value == null) {
            logger.error { "No value for $id" }
            "null"
        } else {
            value
        }
    }

    override fun getDate(
        date: LocalDateTime,
        detail: Boolean,
    ): String {
        val locale =
            when (language) {
                "zh" -> Locale.SIMPLIFIED_CHINESE
                "en" -> Locale.US
                "jp" -> Locale.JAPAN
                "es" -> Locale("es", "ES")
                else -> Locale.getDefault()
            }

        val pattern =
            if (detail) {
                when (language) {
                    "en" -> "MM/dd/yyyy HH:mm:ss"
                    "es" -> "dd/MM/yyyy HH:mm:ss"
                    "jp" -> "yyyy/MM/dd HH:mm:ss"
                    "zh" -> "yyyy年MM月dd日 HH:mm:ss"
                    else -> "MM/dd/yyyy HH:mm:ss"
                }
            } else {
                when (language) {
                    "en" -> "MM/dd/yyyy"
                    "es" -> "dd/MM/yyyy"
                    "jp" -> "yyyy/MM/dd"
                    "zh" -> "yyyy年MM月dd日"
                    else -> "MM/dd/yyyy"
                }
            }
        return dateUtils.getDateText(date, pattern, locale)
    }

    override fun getAbridge(): String {
        return language
    }
}
