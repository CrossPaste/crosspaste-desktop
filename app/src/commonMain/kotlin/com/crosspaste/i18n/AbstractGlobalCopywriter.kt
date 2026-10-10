package com.crosspaste.i18n

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.i18n.SupportedLanguages.EN
import com.crosspaste.i18n.SupportedLanguages.LANGUAGE_LIST
import com.crosspaste.utils.DateTimeFormatOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDateTime

/**
 * Shared base for every platform's [GlobalCopywriter]. Owns the cache, the validate-or-default
 * language bootstrap, switch dispatch, and the trivial delegating overrides — leaving subclasses
 * to supply only the per-platform [Copywriter] factory and any post-switch side effects.
 *
 * This class is deliberately free of Compose types: the active language is published through
 * [languageFlow] so non-Compose hosts (a SwiftUI shell observing it through SKIE, background
 * services, tests) can react to [switchLanguage] without pulling in the Compose runtime.
 * Compose UIs that rely on recomposition when the language changes extend
 * `com.crosspaste.ui.i18n.ComposeGlobalCopywriter` instead, which mirrors [copywriter] into
 * snapshot state on top of this class.
 */
abstract class AbstractGlobalCopywriter(
    private val configManager: CommonConfigManager,
    factory: (String) -> Copywriter,
) : GlobalCopywriter {

    private val logger = KotlinLogging.logger {}

    private val cache = LanguageCache(factory)

    protected val enCopywriter by lazy { cache.getOrCreate(EN) }

    private val currentCopywriter: MutableStateFlow<Copywriter>

    private val currentLanguage: MutableStateFlow<String>

    private val switchLock = SynchronizedObject()

    init {
        val initial = configManager.getCurrentConfig().language
        if (!LANGUAGE_LIST.contains(initial)) {
            configManager.updateConfig("language", EN)
        }
        val language = configManager.getCurrentConfig().language
        currentCopywriter = MutableStateFlow(cache.getOrCreate(language))
        currentLanguage = MutableStateFlow(language)
    }

    override val languageFlow: StateFlow<String> = currentLanguage.asStateFlow()

    /**
     * The [Copywriter] for the active language. Open so a UI-aware subclass can route every
     * read through its own observable mirror (see `ComposeGlobalCopywriter`); the mirror is
     * kept in sync via [onCopywriterChanged].
     */
    protected open val copywriter: Copywriter
        get() = currentCopywriter.value

    override fun language(): String = copywriter.language()

    override fun switchLanguage(language: String) {
        if (!LANGUAGE_LIST.contains(language)) {
            logger.warn { "Ignore switching to unsupported language $language" }
            return
        }
        logger.info { "Switching language to $language" }
        val next = cache.getOrCreate(language)
        // Serialized so concurrent switches can't leave config, the mirror and the flows on
        // different languages. languageFlow is published last: by the time an observer sees
        // the new code, config and getText() (incl. the Compose mirror) already reflect it.
        synchronized(switchLock) {
            configManager.updateConfig("language", language)
            onCopywriterChanged(next)
            currentCopywriter.value = next
            currentLanguage.value = language
        }
        onLanguageSwitched(language)
    }

    /**
     * Hook for observable mirrors of [copywriter]; runs before [onLanguageSwitched]. Reserved
     * for the UI bridge subclass — platform implementations should override
     * [onLanguageSwitched] for their side effects.
     */
    protected open fun onCopywriterChanged(copywriter: Copywriter) {}

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
