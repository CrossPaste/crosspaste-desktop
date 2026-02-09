package com.crosspaste.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.task.SwitchLanguageInfo
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EMPTY_STRING
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EN
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.DateTimeFormatOptions
import com.crosspaste.utils.GlobalCoroutineScope.cpuCoroutineDispatcher
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class DesktopGlobalCopywriter(
    private val configManager: CommonConfigManager,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
    private val taskDao: TaskDao,
) : GlobalCopywriter {

    companion object {

        const val DE = "de"

        const val EN = "en"

        const val ES = "es"

        const val FA = "fa"

        const val FR = "fr"

        const val KO = "ko"

        const val JA = "ja"

        const val ZH = "zh"

        const val ZH_HANT = "zh_hant"

        val LANGUAGE_LIST = listOf(DE, EN, ES, FA, FR, KO, JA, ZH, ZH_HANT)

        val LANGUAGE_MAP = ConcurrentHashMap<String, Copywriter>()

        const val EMPTY_STRING = ""
    }

    private val logger = KotlinLogging.logger {}

    private var language: String =
        run {
            val language = configManager.getCurrentConfig().language
            if (!LANGUAGE_LIST.contains(language)) {
                configManager.updateConfig("language", EN)
                EN
            } else {
                language
            }
        }

    // mutableStateOf is intentionally used here to trigger Compose recomposition when the
    // language changes. Writes from non-Compose threads (e.g., switchLanguage()) are safe
    // because Compose's global snapshot system handles cross-thread state mutations.
    private var copywriter: Copywriter by mutableStateOf(
        LANGUAGE_MAP
            .computeIfAbsent(language) {
                DesktopCopywriter(language)
            },
    )

    private val enCopywriter by lazy {
        LANGUAGE_MAP.computeIfAbsent(EN) {
            DesktopCopywriter(EN)
        }
    }

    private val taskExecutor by lazy { lazyTaskExecutor.value }

    override fun language(): String = copywriter.language()

    override fun switchLanguage(language: String) {
        logger.info { "Switching language $language" }
        copywriter = LANGUAGE_MAP.computeIfAbsent(language) { DesktopCopywriter(language) }
        configManager.updateConfig("language", language)
        cpuCoroutineDispatcher.launch {
            taskExecutor.submitTask(
                taskDao.createTask(null, TaskType.SWITCH_LANGUAGE_TASK, SwitchLanguageInfo(language)),
            )
        }
    }

    override fun getAllLanguages(): List<Language> =
        LANGUAGE_LIST
            .map { it ->
                val copywriter = LANGUAGE_MAP.computeIfAbsent(it) { DesktopCopywriter(it) }
                val abridge = copywriter.getAbridge()
                val name = copywriter.getText("current_language")
                Language(abridge, name)
            }

    override fun getText(
        id: String,
        vararg args: Any?,
    ): String {
        val text = copywriter.getText(id, *args)
        return if (text == EMPTY_STRING && copywriter.language() != EN) {
            logger.debug { "Missing text for id: $id in language: ${copywriter.language()}" }
            val enText = enCopywriter.getText(id, *args) // Fallback to English if not found
            if (enText == EMPTY_STRING) {
                logger.warn { "Missing text for id: $id in English" }
                "[$id]"
            } else {
                enText
            }
        } else {
            text
        }
    }

    override fun getKeys(): Set<String> = copywriter.getKeys()

    override fun getDate(
        date: LocalDateTime,
        options: DateTimeFormatOptions,
    ): String = copywriter.getDate(date, options)

    override fun getAbridge(): String = copywriter.getAbridge()
}

class DesktopCopywriter(
    private val language: String,
) : Copywriter {

    private val logger = KotlinLogging.logger {}

    private val dateUtils = getDateUtils()

    private val properties: Properties = loadProperties()

    private var currentLanguage: String = EN

    private fun load(
        properties: Properties,
        language: String,
    ) {
        properties.load(
            DesktopCopywriter::class.java
                .getResourceAsStream("/i18n/$language.properties")
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

    override fun language(): String = language

    override fun getText(
        id: String,
        vararg args: Any?,
    ): String {
        val value: String? = properties.getProperty(id)
        return value?.format(*args) ?: EMPTY_STRING
    }

    override fun getKeys(): Set<String> = properties.keys.map { it.toString() }.toSet()

    override fun getDate(
        date: LocalDateTime,
        options: DateTimeFormatOptions,
    ): String = dateUtils.getDateDesc(date, options, language)

    override fun getAbridge(): String = language
}
