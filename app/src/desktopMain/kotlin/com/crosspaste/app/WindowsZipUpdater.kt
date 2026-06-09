package com.crosspaste.app

import com.crosspaste.net.DownloadProgressListener
import com.crosspaste.net.ResourcesClient
import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getCompressUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import okio.FileSystem
import okio.Path
import okio.buffer
import java.nio.file.Files
import java.security.MessageDigest
import javax.swing.SwingUtilities

/**
 * Which mechanism keeps this Windows install up to date.
 *
 * - [STORE]: installed from Microsoft Store; updates are owned by the Store
 *   (the `WindowsApps` directory is ACL-protected and self-replacement violates
 *   Store policy), so the app can only notify and deep-link into the Store.
 * - [CONVEYOR_INSTALLER]: packaged/installed via Conveyor, which performs its
 *   own silent background updates. Left untouched.
 * - [PORTABLE_ZIP]: extracted from the portable zip with no installer. This is
 *   the channel that needs the explicit in-app updater implemented here.
 * - [UNSUPPORTED]: not Windows, or the install directory is not writable; the
 *   caller falls back to opening the download page in a browser.
 */
enum class WindowsUpdateChannel {
    STORE,
    CONVEYOR_INSTALLER,
    PORTABLE_ZIP,
    UNSUPPORTED,
}

/** Drives the portable-zip update UI. [Failed.reasonKey] is an i18n key. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** [percent] is -1 when the total size is unknown. */
    data class Downloading(
        val percent: Int,
    ) : UpdateState

    data object Verifying : UpdateState

    data object Extracting : UpdateState

    data class ReadyToApply(
        val version: String,
    ) : UpdateState

    data object Applying : UpdateState

    data class Failed(
        val reasonKey: String,
    ) : UpdateState
}

/** A specific published release, enough to build its Windows zip download URLs. */
private data class RemoteRelease(
    val version: String,
    val revision: String,
    // The release tag that names the download directory ([mirrorBases]). Defaults
    // to the conventional "$version.$revision", but callers pass the tag reported
    // by the metadata source so it stays authoritative if the scheme ever differs.
    val tag: String = "$version.$revision",
) {
    val fileName: String = "crosspaste-$version-$revision-windows-amd64.zip"
}

/**
 * In-app self-update for the Windows **portable zip** distribution: detect the
 * latest release, download the zip from the fastest of the GitHub / Aliyun OSS
 * mirrors, verify its SHA-256 against the release `checksum.txt`, extract it,
 * and on confirmation hand off to a detached batch script that waits for this
 * process to exit, mirrors the new files over the install directory, and
 * relaunches.
 *
 * Store and Conveyor-installer channels are detected but not driven here.
 */
class WindowsZipUpdater(
    private val appUrls: AppUrls,
    private val appPathProvider: AppPathProvider,
    private val appLaunchState: DesktopAppLaunchState,
    private val resourcesClient: ResourcesClient,
    platform: Platform,
    private val metadataFetcher: UpdateMetadataFetcher,
    // Test/QA seams. [baseUrlOverride] points metadata + checksum + zip at a single
    // base (e.g. a local server or an OSS test bucket) instead of GitHub/OSS; it
    // defaults to the `crosspaste.update.base.url` system property or the
    // CROSSPASTE_UPDATE_BASE_URL env var in every build except PRODUCTION (which only
    // honors a loopback override). [forcedChannel] lets tests exercise the flow without
    // a real Windows portable install.
    private val baseUrlOverride: String? = resolveDevBaseUrl(),
    forcedChannel: WindowsUpdateChannel? = null,
) {
    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    private val compressUtils = getCompressUtils()

    private val coroutineScope = namedScope(ioDispatcher, "WindowsZipUpdater")

    val channel: WindowsUpdateChannel = forcedChannel ?: detectChannel(platform)

    /**
     * Override-aware "latest release" metadata URL, or null when no test override is
     * active. Exposed so [DesktopAppUpdateService] can drive the "new version available"
     * banner off the same test source ([baseUrlOverride]) as the download, instead of
     * the hardcoded GitHub releases/latest metadata — otherwise a remote test bucket
     * advertising a newer version would never surface the banner that starts the flow.
     */
    val overrideMetadataUrl: String? =
        baseUrlOverride?.let { "${it.trimEnd('/')}/metadata.properties" }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)

    val updateState: StateFlow<UpdateState> = _updateState

    // The version the user dismissed the "update available" prompt for. The blocking
    // dialog re-arms when this differs from the latest version (a newer release appears)
    // or when [resetUpdatePrompt] is called (the user explicitly checks for updates).
    private val _promptDismissedForVersion = MutableStateFlow<String?>(null)

    val promptDismissedForVersion: StateFlow<String?> = _promptDismissedForVersion

    /** Guards the check-and-claim transition at the start of [startDownload]. */
    private val startLock = Any()

    /** Silence the blocking update dialog for [version] until a newer one appears. */
    fun dismissUpdatePrompt(version: String) {
        _promptDismissedForVersion.value = version
    }

    /** Re-arm the update prompt, e.g. when the user explicitly checks for updates. */
    fun resetUpdatePrompt() {
        _promptDismissedForVersion.value = null
    }

    init {
        consumePreviousApplyFailure()
    }

    private fun detectChannel(platform: Platform): WindowsUpdateChannel =
        when {
            !platform.isWindows() -> WindowsUpdateChannel.UNSUPPORTED
            appLaunchState.installFrom == MICROSOFT_STORE -> WindowsUpdateChannel.STORE
            conveyorCanSelfUpdate() -> WindowsUpdateChannel.CONVEYOR_INSTALLER
            isInstallDirWritable() -> WindowsUpdateChannel.PORTABLE_ZIP
            else -> WindowsUpdateChannel.UNSUPPORTED
        }

    private fun conveyorCanSelfUpdate(): Boolean =
        runCatching {
            val controller = SoftwareUpdateController.getInstance() ?: return false
            // canTriggerUpdateCheckUI() is documented to run on the EDT, like the
            // trigger itself in DesktopAppUpdateService.
            var available = false
            val check =
                Runnable {
                    available =
                        controller.canTriggerUpdateCheckUI() ==
                        SoftwareUpdateController.Availability.AVAILABLE
                }
            if (SwingUtilities.isEventDispatchThread()) {
                check.run()
            } else {
                SwingUtilities.invokeAndWait(check)
            }
            available
        }.getOrDefault(false)

    private fun isInstallDirWritable(): Boolean =
        runCatching {
            val exe = appPathProvider.pasteAppExePath.resolve("CrossPaste.exe")
            fileUtils.existFile(exe) &&
                Files.isWritable(appPathProvider.pasteAppPath.toNioPath())
        }.getOrDefault(false)

    /** Kick off download + verify + extract. No-op if already in progress. */
    fun startDownload() {
        if (channel != WindowsUpdateChannel.PORTABLE_ZIP) return
        // Claim the in-progress state atomically. Three UI entry points (dialog, tray,
        // search) can call this concurrently while Idle; without the lock both could
        // pass the guard and run two downloads into the same staging dir, corrupting it.
        synchronized(startLock) {
            when (_updateState.value) {
                is UpdateState.Checking,
                is UpdateState.Downloading,
                is UpdateState.Verifying,
                is UpdateState.Extracting,
                is UpdateState.Applying,
                -> return
                else -> {}
            }
            _updateState.value = UpdateState.Checking
        }
        coroutineScope.launch {
            runCatching { downloadAndStage() }
                .onFailure { e ->
                    logger.error(e) { "Portable zip update failed" }
                    _updateState.value = UpdateState.Failed("update_failed")
                }
        }
    }

    private suspend fun downloadAndStage() {
        // State was already claimed as Checking synchronously in startDownload().
        val release = readLatestRelease()
        if (release == null) {
            _updateState.value = UpdateState.Failed("update_check_failed")
            return
        }

        // Race the two mirrors for checksum.txt; the winner is both our integrity
        // source and the mirror we download the (large) zip from.
        //
        // Trust model (KNOWN LIMITATION): checksum.txt and the zip come from the SAME
        // mirror, and we only verify SHA-256. That guards against corruption / a partial
        // download, NOT against a compromised or MITM'd mirror — whoever can serve the
        // zip can serve a matching checksum and we would extract and run it with the
        // user's privileges. Integrity therefore rests entirely on HTTPS + the GitHub /
        // Aliyun OSS repository ACLs. Proper hardening is to verify a detached signature
        // (e.g. minisign / GPG) over the zip or checksum against a public key baked into
        // the app, before extracting. See doc/en/WindowsZipSelfUpdateTest.md.
        val winner = fetchChecksumFromFastestSource(release)
        if (winner == null) {
            _updateState.value = UpdateState.Failed("update_download_failed")
            return
        }
        val (base, checksumText) = winner
        val expectedHash = parseChecksum(checksumText, release.fileName)
        if (expectedHash == null) {
            _updateState.value = UpdateState.Failed("update_checksum_missing")
            return
        }

        val updateDir = updateDir()
        recreateDir(updateDir)
        val zipPath = updateDir.resolve(release.fileName)

        _updateState.value = UpdateState.Downloading(0)
        val downloaded = downloadFile(base + release.fileName, zipPath)
        if (!downloaded) {
            _updateState.value = UpdateState.Failed("update_download_failed")
            return
        }

        _updateState.value = UpdateState.Verifying
        val actualHash = sha256(zipPath)
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            logger.warn { "Checksum mismatch: expected $expectedHash, got $actualHash" }
            runCatching { fileUtils.deleteFile(zipPath) }
            _updateState.value = UpdateState.Failed("update_checksum_mismatch")
            return
        }

        _updateState.value = UpdateState.Extracting
        val stagingDir = updateDir.resolve("staging")
        recreateDir(stagingDir)
        val unzipped =
            compressUtils
                .unzip(FileSystem.SYSTEM.source(zipPath).buffer(), stagingDir)
                .isSuccess
        if (!unzipped) {
            _updateState.value = UpdateState.Failed("update_extract_failed")
            return
        }

        _updateState.value = UpdateState.ReadyToApply(release.version)
    }

    /**
     * Hand off to the detached batch script and quit. The script waits for this
     * process to exit, mirrors the staged files over the install directory, and
     * relaunches the app.
     */
    fun applyUpdate(exitApplication: () -> Unit) {
        if (_updateState.value !is UpdateState.ReadyToApply) return
        runCatching {
            val updateDir = updateDir()
            val stagingDir = updateDir.resolve("staging")
            val batPath = updateDir.resolve(APPLY_BAT_NAME)
            val logPath = updateDir.resolve("apply-update.log")

            batPath.toFile().writeText(APPLY_UPDATE_BAT)

            val exePath = appPathProvider.pasteAppExePath.resolve("CrossPaste.exe")
            val pid = ProcessHandle.current().pid().toString()

            // Pass paths through the environment, not as cmd arguments: several
            // space-containing paths (e.g. C:\Users\张三\…) trigger cmd's quote-stripping
            // and the .bat would receive truncated values. Running the .bat by its bare
            // name from updateDir avoids quoting the script path too.
            val builder =
                ProcessBuilder("cmd", "/c", APPLY_BAT_NAME)
                    .directory(updateDir.toFile())
                    .redirectOutput(logPath.toFile())
                    .redirectErrorStream(true)
            builder.environment().apply {
                put("CROSSPASTE_UPDATE_SRC", stagingDir.toString())
                put("CROSSPASTE_UPDATE_DST", appPathProvider.pasteAppPath.toString())
                // Backup lives under the (always-writable) update dir, not as a sibling
                // of the install dir: creating a sibling needs write permission on the
                // install dir's PARENT (often denied, e.g. C:\ root), which is what made
                // the move-aside approach fail with "Access is denied".
                put("CROSSPASTE_UPDATE_BAK", updateDir.resolve("backup").toString())
                put("CROSSPASTE_UPDATE_EXE", exePath.toString())
                put("CROSSPASTE_UPDATE_PID", pid)
                put("CROSSPASTE_UPDATE_MARKER", applyFailureMarker().toString())
            }

            logger.info { "Applying portable zip update via $batPath" }
            builder.start()

            _updateState.value = UpdateState.Applying
            exitApplication()
        }.onFailure { e ->
            logger.error(e) { "Failed to apply portable zip update" }
            _updateState.value = UpdateState.Failed("update_apply_failed")
        }
    }

    private fun updateDir(): Path = appPathProvider.pasteUserPath.resolve("update")

    /** Marker the apply script writes when a replace failed and it rolled back. */
    private fun applyFailureMarker(): Path = updateDir().resolve("apply-update.failed")

    /**
     * If the previous apply rolled back (the script left a failure marker), surface it
     * as a failed state so the user sees that the update did not take instead of it
     * silently "succeeding", then clear the marker. Called once at construction.
     */
    private fun consumePreviousApplyFailure() {
        runCatching {
            val marker = applyFailureMarker()
            if (fileUtils.existFile(marker)) {
                logger.warn { "Previous portable zip update failed and was rolled back" }
                _updateState.value = UpdateState.Failed("update_apply_failed")
                fileUtils.deleteFile(marker)
            }
        }
    }

    private fun metadataUrl(): String = overrideMetadataUrl ?: appUrls.checkMetadataUrl

    /** Download mirrors to race, newest-tag aware. Override collapses to a single base. */
    private fun mirrorBases(release: RemoteRelease): List<String> =
        baseUrlOverride?.let { listOf(it.trimEnd('/') + "/") }
            ?: listOf(
                "https://github.com/CrossPaste/crosspaste-desktop/releases/download/${release.tag}/",
                "https://oss.crosspaste.com/${release.tag}/",
            )

    private fun recreateDir(dir: Path) {
        if (fileUtils.existFile(dir)) {
            fileUtils.deleteFile(dir)
        }
        fileUtils.createDir(dir, mustCreate = false)
    }

    private suspend fun readLatestRelease(): RemoteRelease? =
        metadataFetcher
            .fetchLatest(
                metadataPropertiesUrl = metadataUrl(),
                // Under a test override that base is the single source of truth;
                // otherwise fall back to crosspaste.com when GitHub is blocked.
                versionApiUrl = if (baseUrlOverride != null) null else DesktopAppUrls.versionApiUrl,
                // Keep the resolved tag authoritative: it drives the download URLs
                // ([mirrorBases]), so honor what the source reports rather than
                // re-deriving and risking a mismatch.
            )?.let { RemoteRelease(it.version, it.revision, it.tag) }

    /** Returns the winning mirror base (with trailing slash) and its checksum.txt body. */
    private suspend fun fetchChecksumFromFastestSource(release: RemoteRelease): Pair<String, String>? =
        coroutineScope {
            val deferreds =
                mirrorBases(release).map { base ->
                    async {
                        runCatching {
                            val text =
                                resourcesClient
                                    .request(base + "checksum.txt")
                                    .getOrThrow()
                                    .getBodyAsText()
                            base to text
                        }.getOrNull()
                    }
                }
            raceFirstSuccess(deferreds)
        }

    /** Returns the first deferred to complete with a non-null value, cancelling the rest. */
    private suspend fun <T> raceFirstSuccess(deferreds: List<Deferred<T?>>): T? {
        val remaining = deferreds.toMutableList()
        while (remaining.isNotEmpty()) {
            val (completed, value) =
                select<Pair<Deferred<T?>, T?>> {
                    remaining.forEach { deferred ->
                        deferred.onAwait { result -> deferred to result }
                    }
                }
            remaining.remove(completed)
            if (value != null) {
                remaining.forEach { it.cancel() }
                return value
            }
        }
        return null
    }

    private suspend fun downloadFile(
        url: String,
        path: Path,
    ): Boolean {
        val result = CompletableDeferred<Boolean>()
        resourcesClient.download(
            url = url,
            path = path,
            listener =
                object : DownloadProgressListener {
                    override fun onFailure(
                        httpStatusCode: HttpStatusCode,
                        throwable: Throwable?,
                    ) {
                        logger.warn(throwable) { "Update download failed: $url ($httpStatusCode)" }
                        if (!result.isCompleted) result.complete(false)
                    }

                    override fun onSuccess() {
                        if (!result.isCompleted) result.complete(true)
                    }

                    override fun onProgress(
                        bytesRead: Long,
                        contentLength: Long?,
                    ) {
                        val percent =
                            if (contentLength != null && contentLength > 0) {
                                ((bytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                        _updateState.value = UpdateState.Downloading(percent)
                    }
                },
        )
        return result.await()
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        path.toFile().inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        /**
         * Lets QA point the updater at a test release source (serving
         * metadata.properties, checksum.txt and the zip), so the full flow can be tested
         * without publishing a release. The base URL comes from either the
         * `crosspaste.update.base.url` JVM system property or the
         * `CROSSPASTE_UPDATE_BASE_URL` env var, the property taking precedence.
         *
         * Prefer the system property for packaged test builds: a Conveyor-launched app
         * does not reliably inherit a `set CROSSPASTE_UPDATE_BASE_URL=...` from the
         * shell, whereas a `-Dcrosspaste.update.base.url=...` baked into the build's
         * `app.jvm.options` always reaches `System.getProperty`. The env var stays for
         * the local-server flow where it does propagate.
         *
         * Every build except PRODUCTION accepts any URL — in particular a BETA build
         * (the only packaged channel with a real Windows portable path provider) can be
         * pointed at a remote test bucket such as `https://oss.crosspaste.com/test`,
         * so the whole download/verify/replace/restart flow runs without a local server.
         * PRODUCTION accepts only a loopback server, so a stray override can never
         * redirect actual users to a remote update source.
         */
        private fun resolveDevBaseUrl(): String? {
            val url =
                (
                    System.getProperty("crosspaste.update.base.url")
                        ?: System.getenv("CROSSPASTE_UPDATE_BASE_URL")
                )?.takeIf { it.isNotBlank() }
                    ?: return null
            return if (getAppEnvUtils().getCurrentAppEnv() != AppEnv.PRODUCTION || isLoopbackHost(url)) {
                url
            } else {
                null
            }
        }

        fun isLoopbackHost(url: String): Boolean =
            runCatching {
                when (
                    java.net
                        .URI(url)
                        .host
                        ?.lowercase()
                ) {
                    "localhost", "127.0.0.1", "::1", "[::1]" -> true
                    else -> false
                }
            }.getOrDefault(false)

        /**
         * Parses a `shasum -a 256` line for [fileName]. Each line is
         * `<hex-digest>␠␠<filename>`; returns the digest or null if absent.
         */
        fun parseChecksum(
            checksumText: String,
            fileName: String,
        ): String? =
            checksumText
                .lineSequence()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@mapNotNull null
                    val parts = trimmed.split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2) parts[0] to parts[1].trim() else null
                }.firstOrNull { (_, name) -> name == fileName }
                ?.first

        const val APPLY_BAT_NAME: String = "apply-update.bat"

        /**
         * Replaces the install directory with the staged build and relaunches, with
         * rollback. Inputs come from the environment (set by [applyUpdate]) so multiple
         * space-containing paths can't be mangled by cmd's argument quote-stripping.
         *
         * Atomicity without moving the install dir (renaming it needs write permission on
         * its PARENT — often denied, e.g. C:\ root — and fails with "Access is denied"):
         * the old install is first copied into a backup under the writable update dir,
         * then the staged build is `robocopy /MIR`'d OVER the install dir in place (covers
         * root-level files AND purges stale ones the new build dropped, so two versions of
         * a library can't co-exist on the classpath). If the backup fails the install is
         * left untouched; if the in-place apply fails (RC >= 8) the old install is
         * restored from the backup. Either way a failure marker is written and the
         * previous version is relaunched — a half-applied update never looks successful.
         *
         * The script is intentionally verbose: `chcp 65001` makes the redirected log
         * UTF-8 (readable when paths contain non-ASCII, e.g. a Chinese user folder),
         * every resolved variable and exit code is echoed, robocopy output is NOT
         * suppressed (so the failing file / reason is visible), and the failure reason
         * is written to BOTH the log and the marker. All log lines are tagged `[cp-up]`.
         */
        private val APPLY_UPDATE_BAT =
            """
            @echo off
            setlocal enableextensions
            chcp 65001 >nul
            :: Inputs via environment to avoid cmd quote-stripping on spaced paths.
            set "SRC=%CROSSPASTE_UPDATE_SRC%"
            set "DST=%CROSSPASTE_UPDATE_DST%"
            set "EXE=%CROSSPASTE_UPDATE_EXE%"
            set "PID=%CROSSPASTE_UPDATE_PID%"
            set "MARKER=%CROSSPASTE_UPDATE_MARKER%"
            set "BAK=%CROSSPASTE_UPDATE_BAK%"

            echo [cp-up] === apply-update started ===
            echo [cp-up] SRC   =[%SRC%]
            echo [cp-up] DST   =[%DST%]
            echo [cp-up] BAK   =[%BAK%]
            echo [cp-up] EXE   =[%EXE%]
            echo [cp-up] PID   =[%PID%]
            echo [cp-up] MARKER=[%MARKER%]
            if not exist "%SRC%\*" echo [cp-up] WARN: SRC missing or empty
            if not exist "%DST%\*" echo [cp-up] WARN: DST missing

            echo [cp-up] waiting for PID %PID% to exit...
            :CHECK_LOOP
            tasklist /FI "PID eq %PID%" 2>nul | find "%PID%" >nul
            if %ERRORLEVEL% neq 0 goto APPLY
            :: ping, not timeout: timeout needs a console and fails ("input redirection
            :: is not supported") when stdin is redirected, busy-spinning the loop.
            ping -n 2 127.0.0.1 >nul
            goto CHECK_LOOP

            :APPLY
            echo [cp-up] process gone, applying
            if exist "%BAK%" rmdir /S /Q "%BAK%"

            :: Back up the current install into the (writable) update dir, by COPY. We do
            :: NOT rename/move the install dir: that needs write permission on its PARENT
            :: (often denied, e.g. C:\ root) and fails with "Access is denied". Reading the
            :: old files for backup tolerates shared locks.
            echo [cp-up] backing up current install
            robocopy "%DST%" "%BAK%" /E /R:1 /W:1 /NP
            set "BK=%ERRORLEVEL%"
            echo [cp-up] backup exit=%BK%
            if %BK% GEQ 8 goto BACKUP_FAILED

            :: Mirror the new build over the install dir in place. /MIR (= /E + /PURGE)
            :: copies the whole tree incl. root-level files AND removes files the new
            :: build dropped (renamed/deleted jars/dlls) — leaving stale libs behind risks
            :: a startup LinkageError when two versions of one lib land on the classpath.
            :: Safe here: the install dir holds no user data (that lives in ~/.crosspaste),
            :: and staging is already SHA-256-verified + unzip-checked. Only needs the
            :: install dir itself writable, not its parent. Symmetric with the rollback.
            echo [cp-up] mirroring new build over install
            robocopy "%SRC%" "%DST%" /MIR /R:3 /W:2 /NP
            set "RC=%ERRORLEVEL%"
            echo [cp-up] apply exit=%RC%
            if %RC% GEQ 8 goto APPLY_FAILED

            :: Success: drop the backup and staging, then relaunch the new build.
            rmdir /S /Q "%BAK%"
            rmdir /S /Q "%SRC%"
            echo [cp-up] success, starting "%EXE%"
            start "" "%EXE%"
            echo [cp-up] === done: success ===
            exit /b 0

            :BACKUP_FAILED
            echo [cp-up] FAILED: backup error %BK%, install left untouched
            > "%MARKER%" echo apply-update failed: backup code %BK% ^(install untouched^)
            goto START_OLD

            :APPLY_FAILED
            echo [cp-up] FAILED: apply error %RC%, restoring from backup
            robocopy "%BAK%" "%DST%" /MIR /R:3 /W:2 /NP
            set "RB=%ERRORLEVEL%"
            echo [cp-up] rollback exit=%RB%
            if %RB% GEQ 8 goto ROLLBACK_FAILED
            > "%MARKER%" echo apply-update failed: apply code %RC%, rolled back ok
            goto START_OLD

            :ROLLBACK_FAILED
            echo [cp-up] CRITICAL: rollback also failed code %RB%, install may be broken
            > "%MARKER%" echo apply-update failed: apply %RC% AND rollback %RB% - install may be broken
            goto START_OLD

            :START_OLD
            echo [cp-up] starting previous version "%EXE%"
            start "" "%EXE%"
            echo [cp-up] === done: failed ===
            exit /b 1
            """.trimIndent().replace("\n", "\r\n")
    }
}
