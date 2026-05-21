package com.crosspaste.bootstrap

import com.crosspaste.app.AppFileType
import com.crosspaste.config.migrateAppInstanceIdIfNeeded
import com.crosspaste.net.LanBypassProxySelector
import com.crosspaste.path.AppPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.ui.AwtExceptionHandler
import io.github.oshai.kotlinlogging.KLogger

/**
 * Orchestrates process-wide bootstrap steps for the desktop entrypoint.
 *
 * Bootstrap is split into two phases by timing constraint. Each step lives
 * in the phase whose preconditions match its requirements; the doc on each
 * phase records what must NOT yet have happened by the time it runs. Add
 * new steps to whichever phase fits, alongside a comment naming the
 * subsystem that forces the phase choice.
 */
object DesktopBootstrap {

    /**
     * Phase 1 — runs during `CrossPaste` companion-object init, before any
     * further class loading triggered by `main()`. Anything that must
     * complete before Compose / skiko / AWT classes get touched belongs
     * here.
     */
    fun preClassLoad(
        appPathProvider: AppPathProvider,
        metadataFilePersist: OneFilePersist,
        configFilePersist: OneFilePersist,
    ) {
        // skiko / compose / sun.java2d / sun.awt overrides — must precede
        // the first reference to any Compose or AWT class.
        JvmSystemPropertiesOverride.apply(
            FilePersist.createOneFilePersist(
                appPathProvider.resolve(JvmSystemPropertiesOverride.FILE_NAME, AppFileType.USER),
            ),
        )
        // Legacy appInstanceId migration touches config files only; must
        // run before AppMetadataRepository / DesktopConfigManager read them.
        migrateAppInstanceIdIfNeeded(metadataFilePersist, configFilePersist)
    }

    /**
     * Phase 2 — runs first thing in `main()`, before `initModule()` /
     * `startApplication()` / the Compose `application { }` launch. All
     * JVM-wide hooks that must precede any HttpClient request, AWT
     * EventQueue initialisation, or worker thread spawn belong here.
     */
    fun preStart(logger: KLogger) {
        // Wrap the JVM ProxySelector so Ktor CIO bypasses the user's system
        // proxy for LAN sync — must run before any HttpClient construction.
        LanBypassProxySelector.install()
        // Route AWT EDT exceptions through our handler — must precede AWT
        // EventQueue init (Toolkit.getDefaultToolkit() / Compose application).
        System.setProperty("sun.awt.exception.handler", AwtExceptionHandler::class.java.name)
        // Catch-all for threads that don't set their own handler. Order-
        // agnostic, but kept here so all process-wide hooks live together.
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.error(throwable) { "Uncaught exception in thread: $thread" }
        }
    }
}
