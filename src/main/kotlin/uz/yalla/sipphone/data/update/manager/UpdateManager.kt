package uz.yalla.sipphone.data.update.manager

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.yalla.sipphone.data.update.api.UpdateApiContract
import uz.yalla.sipphone.data.update.api.UpdateCheckResult
import uz.yalla.sipphone.data.update.downloader.DownloadResult
import uz.yalla.sipphone.data.update.downloader.UpdateDownloaderContract
import uz.yalla.sipphone.data.update.install.InstallerContract
import uz.yalla.sipphone.data.update.storage.UpdatePaths
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.update.Semver
import uz.yalla.sipphone.domain.update.UpdateChannel
import uz.yalla.sipphone.domain.update.UpdateRelease
import uz.yalla.sipphone.domain.update.UpdateState
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

class UpdateManager(
    private val scope: CoroutineScope,
    private val api: UpdateApiContract,
    private val downloader: UpdateDownloaderContract,
    private val installer: InstallerContract,
    private val paths: UpdatePaths,
    private val callState: StateFlow<CallState>,
    private val currentVersion: String,
    private val channelProvider: () -> UpdateChannel,
    private val installIdProvider: () -> String,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MS,
    private val exitProcess: (Int) -> Unit = { code -> kotlin.system.exitProcess(code) },
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _diagnosticsVisible = MutableStateFlow(false)
    val diagnosticsVisible: StateFlow<Boolean> = _diagnosticsVisible.asStateFlow()

    private val _dialogDismissed = MutableStateFlow(false)
    val dialogDismissed: StateFlow<Boolean> = _dialogDismissed.asStateFlow()

    fun toggleDiagnostics() {
        _diagnosticsVisible.value = !_diagnosticsVisible.value
    }

    fun hideDiagnostics() {
        _diagnosticsVisible.value = false
    }

    private val running = AtomicBoolean(false)
    private var loopJob: Job? = null
    private var verifyFailureCount: Int = 0
    private var blacklistedVersion: String? = null
    private var lastCheckEpochMillis: Long = 0
    private var lastError: String? = null
    @Volatile
    private var installInProgress: Boolean = false

    var onBeforeExit: (() -> Unit)? = null

    fun lastCheckMillis(): Long = lastCheckEpochMillis
    fun lastErrorMessage(): String? = lastError
    fun isInstallInProgress(): Boolean = installInProgress

    fun start() {
        if (!running.compareAndSet(false, true)) return
        paths.cleanupPartials()
        loopJob = scope.launch {
            while (isActive) {
                runCheckCycle()
                delay(jitterDelay())
            }
        }
    }

    fun stop() {
        running.set(false)
        loopJob?.cancel()
        loopJob = null
    }

    fun checkNow() {
        scope.launch { runCheckCycle() }
    }

    fun confirmInstall() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: run {
            logger.warn { "confirmInstall ignored: state is ${_state.value::class.simpleName}, expected ReadyToInstall" }
            return
        }

        installInProgress = true
        scope.launch {
            callState.first { it is CallState.Idle }
            _state.value = UpdateState.Installing(ready.release)
            runCatching {
                // Order matters: tear down PJSIP and JCEF FIRST so the audio device, JCEF
                // process, and any open file handles are released before the bootstrapper
                // starts replacing files on disk. The bootstrapper's `--parent-pid` wait
                // is only a courtesy — file locks on Windows don't respect it.
                onBeforeExit?.invoke()
                installer.install(
                    msiPath = Path.of(ready.msiPath),
                    expectedSha256 = ready.release.installer.sha256,
                    logPath = paths.installLogPath(),
                )
                exitProcess(0)
            }.onFailure { t ->
                logger.error(t) { "Installer failed to launch" }
                installInProgress = false
                lastError = t.message
                _state.value = UpdateState.Failed(
                    UpdateState.Failed.Stage.INSTALL,
                    t.message ?: "install failed",
                )
            }
        }
    }

    fun dismiss() {
        _dialogDismissed.value = true
    }

    fun showDialog() {
        _dialogDismissed.value = false
    }

    private suspend fun runCheckCycle() {
        if (callState.value !is CallState.Idle) {
            logger.debug { "Skipping update check: call not idle" }
            return
        }
        if (installInProgress) return

        lastCheckEpochMillis = System.currentTimeMillis()
        _dialogDismissed.value = false
        _state.value = UpdateState.Checking

        val result = runCatching {
            api.check(
                channel = channelProvider(),
                currentVersion = currentVersion,
                installId = installIdProvider(),
            )
        }.getOrElse { t ->
            logger.warn(t) { "api.check() threw unexpectedly" }
            UpdateCheckResult.Error(t)
        }

        when (result) {
            is UpdateCheckResult.NoUpdate -> _state.value = UpdateState.Idle
            is UpdateCheckResult.Malformed -> {
                lastError = result.reason
                _state.value = UpdateState.Failed(
                    UpdateState.Failed.Stage.MALFORMED_MANIFEST,
                    result.reason,
                )
                delay(TRANSIENT_STATE_DISPLAY_MS)
                if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
            }
            is UpdateCheckResult.Error -> {
                lastError = result.cause?.message ?: "network error"
                _state.value = UpdateState.Idle
            }
            is UpdateCheckResult.Available -> handleAvailable(result.release)
        }
    }

    private suspend fun handleAvailable(release: UpdateRelease) {
        val current = Semver.parseOrNull(currentVersion)
        val incoming = Semver.parseOrNull(release.version)
        if (current != null && incoming != null && incoming <= current) {
            _state.value = UpdateState.Idle
            return
        }
        if (blacklistedVersion == release.version) {
            logger.warn { "Version ${release.version} is blacklisted, skipping" }
            _state.value = UpdateState.Idle
            return
        }

        if (!hasEnoughDisk(release.installer.size * 2)) {
            lastError = "insufficient disk space for ${release.installer.size * 2} bytes"
            logger.warn { "Update skipped: $lastError" }
            _state.value = UpdateState.Failed(
                UpdateState.Failed.Stage.DISK_FULL,
                "size * 2 = ${release.installer.size * 2} bytes required",
            )
            delay(TRANSIENT_STATE_DISPLAY_MS)
            if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
            return
        }

        _state.value = UpdateState.Downloading(release, 0, release.installer.size)
        when (val dl = downloader.download(release)) {
            is DownloadResult.Success -> {
                _state.value = UpdateState.Verifying(release)
                verifyFailureCount = 0
                _state.value = UpdateState.ReadyToInstall(release, dl.msiFile.toString())
            }
            is DownloadResult.VerifyFailed -> {
                verifyFailureCount++
                lastError = "sha256 mismatch"
                if (verifyFailureCount >= MAX_VERIFY_FAILURES_BEFORE_BLACKLIST) {
                    blacklistedVersion = release.version
                    logger.warn { "Blacklisting ${release.version} after $verifyFailureCount verify failures" }
                }
                _state.value = UpdateState.Failed(UpdateState.Failed.Stage.VERIFY, "sha256 mismatch")
                delay(TRANSIENT_STATE_DISPLAY_MS)
                if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
            }
            is DownloadResult.Failed -> {
                lastError = dl.cause?.message ?: "download failed"
                _state.value = UpdateState.Failed(
                    UpdateState.Failed.Stage.DOWNLOAD,
                    lastError ?: "download failed",
                )
                delay(TRANSIENT_STATE_DISPLAY_MS)
                if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
            }
        }
    }

    private fun jitterDelay(): Long = pollIntervalMillis + (0 until JITTER_WINDOW_MS).random()

    private fun hasEnoughDisk(neededBytes: Long): Boolean = runCatching {
        val store: FileStore = Files.getFileStore(paths.updatesDir)
        store.usableSpace >= neededBytes
    }.getOrDefault(true)

    companion object {
        private const val TRANSIENT_STATE_DISPLAY_MS = 1_500L
        private const val DEFAULT_POLL_INTERVAL_MS = 60L * 60L * 1000L
        private const val JITTER_WINDOW_MS = 600_000L
        private const val MAX_VERIFY_FAILURES_BEFORE_BLACKLIST = 3
    }
}
