package com.crosspaste.app

import com.crosspaste.net.ResourceRequestLimits
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
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
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
import java.io.StringReader
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
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

    /**
     * [manual] is true when the failed attempt was started by the user (or is a failed
     * apply they must see); background attempts fail quietly and retry on the next check.
     */
    data class Failed(
        val reasonKey: String,
        val manual: Boolean = true,
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
 * The download runs through [ResumableUpdateDownloader], so it survives restarts and
 * dropped connections, and a background download (started by the periodic update
 * check, see [startBackgroundDownload]) is throttled to [BACKGROUND_LIMIT_BYTES_PER_SECOND]
 * until the user asks for it explicitly. A verified, extracted update is remembered in
 * [READY_MARKER] so the restart prompt comes straight back after a relaunch.
 *
 * Store and Conveyor-installer channels are detected but not driven here.
 */
class WindowsZipUpdater(
    appInfo: AppInfo,
    private val appUrls: AppUrls,
    private val appPathProvider: AppPathProvider,
    private val appLaunchState: DesktopAppLaunchState,
    private val resourcesClient: ResourcesClient,
    private val downloader: ResumableUpdateDownloader,
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

    private val currentVersion: Version? = appInfo.appVersion.toVersionOrNull()

    // True while the in-flight download was requested by the user: it then runs
    // unthrottled, and a failure is surfaced in the dialog instead of retried quietly.
    @Volatile
    private var manualAttempt: Boolean = false

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
        restoreStagedUpdate()
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

    /**
     * Kick off download + verify + extract at the user's request: unthrottled, and a
     * failure is shown. If a background download is already running it is promoted to
     * full speed instead of being restarted.
     */
    fun startDownload() = start(manual = true)

    /**
     * Same pipeline started by the periodic update check: throttled, and a failure
     * stays quiet (the next check retries). No-op when an update is already staged.
     */
    fun startBackgroundDownload() = start(manual = false)

    private fun start(manual: Boolean) {
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
                -> {
                    // A user click on top of a background download lifts the throttle.
                    if (manual) manualAttempt = true
                    return
                }
                is UpdateState.Applying,
                is UpdateState.ReadyToApply,
                -> return
                else -> {}
            }
            manualAttempt = manual
            _updateState.value = UpdateState.Checking
        }
        coroutineScope.launch {
            runCatching { downloadAndStage() }
                .onFailure { e ->
                    logger.error(e) { "Portable zip update failed" }
                    fail("update_failed")
                }
        }
    }

    private fun fail(reasonKey: String) {
        _updateState.value = UpdateState.Failed(reasonKey, manual = manualAttempt)
    }

    private suspend fun downloadAndStage() {
        // State was already claimed as Checking synchronously in startDownload().
        val release = readLatestRelease()
        if (release == null) {
            fail("update_check_failed")
            return
        }

        val updateDir = updateDir()
        fileUtils.createDir(updateDir, mustCreate = false)
        if (readReadyMarker()?.version == release.version && fileUtils.existFile(stagingDir())) {
            // Already downloaded, verified and extracted (possibly in an earlier
            // session); nothing to fetch again.
            _updateState.value = UpdateState.ReadyToApply(release.version)
            return
        }
        clearStaging()
        dropFilesNotFor(release)

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
        val candidateBases = mirrorBases(release)
        val savedSource = readSavedSource(release)
        val hasPartialDownload = hasPartialDownload(release)

        // If we already have a partial download from a specific mirror, reuse that mirror
        // so the server's ETag matches and HTTP 206 Range resume succeeds.
        val preferredBase = savedSource?.takeIf { hasPartialDownload && it in candidateBases }

        val checksumResult: Pair<String, String>? =
            if (preferredBase != null) {
                fetchChecksumFromSource(preferredBase)?.let { preferredBase to it }
                    ?: fetchChecksumFromFastestSource(release)
            } else {
                fetchChecksumFromFastestSource(release)
            }

        if (checksumResult == null) {
            fail("update_download_failed")
            return
        }

        val (primaryBase, checksumText) = checksumResult
        val expectedHash = parseChecksum(checksumText, release.fileName)
        if (expectedHash == null) {
            fail("update_checksum_missing")
            return
        }

        val zipPath = updateDir.resolve(release.fileName)

        _updateState.value = UpdateState.Downloading(0)
        // If the checksum race selected a different mirror than the previous partial
        // download came from, drop that mirror's ETag so the download resumes with a bare
        // Range on the new mirror instead of failing an If-Range match and restarting from 0.
        if (primaryBase != savedSource) {
            forgetMirrorEtag(release)
        }
        saveSource(release, primaryBase)
        var downloaded = downloadFile(primaryBase + release.fileName, zipPath)

        // Both mirrors serve the same bytes, so a failed transfer continues on the other
        // one from where it stopped. Only the ETag is dropped: it belongs to the mirror
        // that failed, and the final SHA-256 covers the stitched file.
        if (!downloaded) {
            val fallbackBases = candidateBases.filter { it != primaryBase }
            for (fallbackBase in fallbackBases) {
                logger.info { "Download failed from $primaryBase, continuing on fallback mirror $fallbackBase" }
                forgetMirrorEtag(release)
                saveSource(release, fallbackBase)
                downloaded = downloadFile(fallbackBase + release.fileName, zipPath)
                if (downloaded) break
            }
        }

        if (!downloaded) {
            fail("update_download_failed")
            return
        }

        runCatching { fileUtils.deleteFile(sourceFile(release)) }

        _updateState.value = UpdateState.Verifying
        val actualHash = sha256(zipPath)
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            logger.warn { "Checksum mismatch: expected $expectedHash, got $actualHash" }
            runCatching { fileUtils.deleteFile(zipPath) }
            fail("update_checksum_mismatch")
            return
        }

        _updateState.value = UpdateState.Extracting
        val stagingDir = stagingDir()
        recreateDir(stagingDir)
        val unzipped =
            compressUtils
                .unzip(FileSystem.SYSTEM.source(zipPath).buffer(), stagingDir)
                .isSuccess
        if (!unzipped) {
            fail("update_extract_failed")
            return
        }
        // The extracted tree is what gets applied; the zip only costs disk now.
        runCatching { fileUtils.deleteFile(zipPath) }
        writeReadyMarker(release)
        // A fresh, verified staging supersedes whatever an earlier apply left behind.
        runCatching { fileUtils.deleteFile(applyFailureMarker()) }

        _updateState.value = UpdateState.ReadyToApply(release.version)
    }

    private fun stagingDir(): Path = updateDir().resolve("staging")

    private fun readyMarker(): Path = updateDir().resolve(READY_MARKER)

    private fun clearStaging() {
        runCatching { fileUtils.deleteFile(readyMarker()) }
        val staging = stagingDir()
        if (fileUtils.existFile(staging)) {
            runCatching { fileUtils.deleteFile(staging) }
        }
    }

    private fun sourceFile(release: RemoteRelease): Path = updateDir().resolve("${release.fileName}.source")

    /** True when a resumable partial download (at least one byte) of [release] is on disk. */
    private fun hasPartialDownload(release: RemoteRelease): Boolean {
        val part = ResumableUpdateDownloader.partFile(updateDir().resolve(release.fileName))
        return part.isFile && part.length() > 0L
    }

    private fun readSavedSource(release: RemoteRelease): String? =
        runCatching {
            val file = sourceFile(release).toFile()
            if (file.isFile) file.readText().trim().takeIf { it.isNotEmpty() } else null
        }.getOrNull()

    private fun saveSource(
        release: RemoteRelease,
        sourceBase: String,
    ) {
        runCatching {
            sourceFile(release).toFile().writeText(sourceBase)
        }
    }

    /** Drops the ETag of the mirror we are leaving so the partial resumes with a bare Range. */
    private fun forgetMirrorEtag(release: RemoteRelease) {
        runCatching { fileUtils.deleteFile(updateDir().resolve("${release.fileName}.etag")) }
    }

    /**
     * Drops zips and partial downloads of any other release so a superseded download is
     * not resumed, while keeping this release's `.part` + `.etag` + `.source` for the resume.
     */
    private fun dropFilesNotFor(release: RemoteRelease) {
        val keep =
            setOf(
                release.fileName,
                "${release.fileName}.part",
                "${release.fileName}.etag",
                "${release.fileName}.source",
            )
        updateDir()
            .toFile()
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.name !in keep &&
                    (
                        it.name.endsWith(".zip") ||
                            it.name.endsWith(".part") ||
                            it.name.endsWith(".etag") ||
                            it.name.endsWith(".source")
                    )
            }.forEach { stale ->
                runCatching { stale.delete() }
            }
    }

    private fun writeReadyMarker(release: RemoteRelease) {
        runCatching {
            readyMarker().toFile().writeText("version=${release.version}\nrevision=${release.revision}\n")
        }.onFailure { e -> logger.warn(e) { "Could not record the staged update" } }
    }

    private fun readReadyMarker(): RemoteRelease? =
        runCatching {
            val marker = readyMarker().toFile()
            if (!marker.isFile) return null
            val properties = Properties().apply { load(StringReader(marker.readText())) }
            val version = properties.getProperty("version") ?: return null
            val revision = properties.getProperty("revision") ?: return null
            RemoteRelease(version, revision)
        }.getOrNull()

    /**
     * Resurface a staged update from an earlier session as [UpdateState.ReadyToApply], or
     * drop it when it is no longer newer than what is running (the apply succeeded, or the
     * user updated some other way). Runs once at construction.
     */
    private fun restoreStagedUpdate() {
        if (channel != WindowsUpdateChannel.PORTABLE_ZIP) return
        val staged = readReadyMarker()
        if (staged == null) {
            // No record of what the staging dir holds (e.g. an apply that rolled back):
            // it is never applied without the marker, so stop it occupying disk.
            if (fileUtils.existFile(stagingDir())) clearStaging()
            return
        }
        val stagedVersion = staged.version.toVersionOrNull()
        val current = currentVersion
        if (stagedVersion != null && current != null && stagedVersion > current && fileUtils.existFile(stagingDir())) {
            logger.info { "Staged update ${staged.version} is ready to apply" }
            _updateState.value = UpdateState.ReadyToApply(staged.version)
        } else {
            clearStaging()
        }
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
            val stagingDir = stagingDir()
            val exePath = appPathProvider.pasteAppExePath.resolve("CrossPaste.exe")
            val stagedExePath = stagingDir.resolve(exePath.relativeTo(appPathProvider.pasteAppPath))

            // A ReadyToApply state can outlive its staging by days (restored across
            // sessions), during which AV quarantine or a cleanup tool may strip it.
            // Mirroring an incomplete staging would purge the install, so re-download.
            if (!fileUtils.existFile(stagedExePath)) {
                logger.warn { "Staged update is missing $stagedExePath, discarding it" }
                clearStaging()
                _updateState.value = UpdateState.Failed("update_apply_failed")
                return
            }

            val batPath = updateDir.resolve(APPLY_BAT_NAME)
            val logPath = updateDir.resolve("apply-update.log")

            batPath.toFile().writeText(APPLY_UPDATE_BAT)

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
                put("CROSSPASTE_UPDATE_SRC_EXE", stagedExePath.toString())
                put("CROSSPASTE_UPDATE_PID", pid)
                put("CROSSPASTE_UPDATE_MARKER", applyFailureMarker().toString())
            }

            // Once the script runs the staging dir is consumed (or rolled back and the
            // failure marker set), so the "ready" record must not outlive this attempt.
            runCatching { fileUtils.deleteFile(readyMarker()) }

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
                fileUtils.deleteFile(marker)
                // A newer update staged since then (restoreStagedUpdate ran first) is
                // what the user should act on, not a failure that is already history.
                if (_updateState.value is UpdateState.ReadyToApply) return
                logger.warn { "Previous portable zip update failed and was rolled back" }
                _updateState.value = UpdateState.Failed("update_apply_failed")
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

    private suspend fun fetchChecksumFromSource(base: String): String? =
        runCatching {
            resourcesClient
                .request(base + "checksum.txt", ResourceRequestLimits.METADATA)
                .getOrThrow()
                .getBodyAsText()
        }.getOrNull()

    /** Returns the winning mirror base (with trailing slash) and its checksum.txt body. */
    private suspend fun fetchChecksumFromFastestSource(release: RemoteRelease): Pair<String, String>? =
        coroutineScope {
            val deferreds =
                mirrorBases(release).map { base ->
                    async {
                        fetchChecksumFromSource(base)?.let { base to it }
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
        var lastPercent = Int.MIN_VALUE
        val result =
            downloader.download(
                url = url,
                target = path,
                limitBytesPerSecond = { if (manualAttempt) 0L else BACKGROUND_LIMIT_BYTES_PER_SECOND },
                onProgress = { bytesRead, contentLength ->
                    val percent =
                        if (contentLength != null && contentLength > 0) {
                            ((bytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _updateState.value = UpdateState.Downloading(percent)
                    }
                },
            )
        return when (result) {
            is UpdateDownloadResult.Success -> true
            is UpdateDownloadResult.Failed -> {
                logger.warn(result.cause) { "Update download failed: $url (${result.status})" }
                false
            }
        }
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
        /** Rate cap for downloads the periodic check starts on its own. */
        const val BACKGROUND_LIMIT_BYTES_PER_SECOND: Long = 1024L * 1024

        /** Records the release whose verified, extracted files sit in `update/staging`. */
        const val READY_MARKER: String = "ready.properties"

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
         * restored from the backup. The staged exe is re-checked before anything is
         * touched, and the installed exe after the mirror (a short staging mirrors with
         * RC < 8 yet purges files), rolling back while the backup still exists. Either
         * way a failure marker is written and the previous version is relaunched — a
         * half-applied update never looks successful.
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
            set "SRC_EXE=%CROSSPASTE_UPDATE_SRC_EXE%"
            set "PID=%CROSSPASTE_UPDATE_PID%"
            set "MARKER=%CROSSPASTE_UPDATE_MARKER%"
            set "BAK=%CROSSPASTE_UPDATE_BAK%"

            echo [cp-up] === apply-update started ===
            echo [cp-up] SRC   =[%SRC%]
            echo [cp-up] DST   =[%DST%]
            echo [cp-up] BAK   =[%BAK%]
            echo [cp-up] EXE   =[%EXE%]
            echo [cp-up] SRCEXE=[%SRC_EXE%]
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
            :: Staging may have been stripped (AV quarantine, cleanup) since it was
            :: verified; /MIR from it would purge the install, so refuse it outright.
            if not exist "%SRC_EXE%" goto STAGING_INVALID
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
            :: RC 1-3 also covers purged files: never trust it without the exe in place.
            if not exist "%EXE%" (
                set "RC=%RC% but exe missing"
                goto APPLY_FAILED
            )

            :: Success: drop the backup and staging, then relaunch the new build.
            rmdir /S /Q "%BAK%"
            rmdir /S /Q "%SRC%"
            echo [cp-up] success, starting "%EXE%"
            start "" /D "%DST%" "%EXE%"
            echo [cp-up] === done: success ===
            exit /b 0

            :STAGING_INVALID
            echo [cp-up] FAILED: staged exe missing, install left untouched
            > "%MARKER%" echo apply-update failed: staging incomplete ^(install untouched^)
            goto START_OLD

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
            start "" /D "%DST%" "%EXE%"
            echo [cp-up] === done: failed ===
            exit /b 1
            """.trimIndent().replace("\n", "\r\n")
    }
}
