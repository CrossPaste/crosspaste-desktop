package com.crosspaste.i18n

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.task.SwitchLanguageInfo
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EMPTY_STRING
import com.crosspaste.i18n.SupportedLanguages.EN
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

class DesktopGlobalCopywriter(
    configManager: CommonConfigManager,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
    private val taskDao: TaskDao,
) : AbstractGlobalCopywriter(configManager, { DesktopCopywriter(it) }) {

    companion object {

        const val EMPTY_STRING = ""
    }

    private val logger = KotlinLogging.logger {}

    private val taskExecutor by lazy { lazyTaskExecutor.value }

    override fun onLanguageSwitched(language: String) {
        cpuCoroutineDispatcher.launch {
            taskExecutor.submitTask(
                taskDao.createTask(null, TaskType.SWITCH_LANGUAGE_TASK, SwitchLanguageInfo(language)),
            )
        }
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
