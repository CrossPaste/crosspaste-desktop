package com.crosspaste.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.i18n.SupportedLanguages.EN
import com.crosspaste.i18n.SupportedLanguages.LANGUAGE_LIST
import com.crosspaste.utils.DateTimeFormatOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime

/**
 * Shared base for every platform's [GlobalCopywriter]. Owns the cache, the validate-or-default
 * language bootstrap, switch dispatch, and the trivial delegating overrides — leaving subclasses
 * to supply only the per-platform [Copywriter] factory and any post-switch side effects.
 */
abstract class AbstractGlobalCopywriter(
    private val configManager: CommonConfigManager,
    factory: (String) -> Copywriter,
) : GlobalCopywriter {

    private val logger = KotlinLogging.logger {}

    private val cache = LanguageCache(factory)

    protected val enCopywriter by lazy { cache.getOrCreate(EN) }

    init {
        val initial = configManager.getCurrentConfig().language
        if (!LANGUAGE_LIST.contains(initial)) {
            configManager.updateConfig("language", EN)
        }
    }

    // mutableStateOf is intentionally used here to trigger Compose recomposition when the
    // language changes. Writes from non-Compose threads (e.g., switchLanguage()) are safe
    // because Compose's global snapshot system handles cross-thread state mutations.
    protected var copywriter: Copywriter by mutableStateOf(
        cache.getOrCreate(configManager.getCurrentConfig().language),
    )

    override fun language(): String = copywriter.language()

    override fun switchLanguage(language: String) {
        logger.info { "Switching language to $language" }
        copywriter = cache.getOrCreate(language)
        configManager.updateConfig("language", language)
        onLanguageSwitched(language)
    }

    /** Subclass hook for post-switch side effects (e.g. dispatching a sync task). */
    protected open fun onLanguageSwitched(language: String) {}

    override fun getAllLanguages(): List<Language> =
        LANGUAGE_LIST.map {
            val cw = cache.getOrCreate(it)
            Language(cw.getAbridge(), cw.getText("current_language"))
        }

    override fun getText(
        id: String,
        vararg args: Any?,
    ): String = copywriter.getText(id, *args)

    override fun getKeys(): Set<String> = copywriter.getKeys()

    override fun getDate(
        date: LocalDateTime,
        options: DateTimeFormatOptions,
    ): String = copywriter.getDate(date, options)

    override fun getAbridge(): String = copywriter.getAbridge()
}
