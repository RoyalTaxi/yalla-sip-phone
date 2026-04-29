package uz.yalla.sipphone.data.update.install

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

private val logger = KotlinLogging.logger {}

class MsiBootstrapperInstaller(
    private val bootstrapperPathOverride: Path? = null,
    private val installDirOverride: Path? = null,
    private val processLauncher: ProcessLauncher = RealProcessLauncher(),
) {

    private val bootstrapperPath: Path
        get() = bootstrapperPathOverride ?: defaultBootstrapperPath()

    private val installDir: Path
        get() = installDirOverride ?: defaultInstallDir()

    fun buildCommand(
        binary: Path,
        msiPath: Path,
        expectedSha256: String,
        logPath: Path,
        parentPid: Long = currentPid(),
    ): List<String> = listOf(
        binary.toString(),
        "--msi", msiPath.toString(),
        "--install-dir", installDir.toString(),
        "--parent-pid", parentPid.toString(),
        "--expected-sha256", expectedSha256,
        "--log", logPath.toString(),
    )

    fun install(msiPath: Path, expectedSha256: String, logPath: Path) {
        check(bootstrapperPath.exists()) { "Bootstrapper not found: $bootstrapperPath" }
        check(msiPath.exists()) { "MSI missing: $msiPath" }
        stripMarkOfTheWeb(msiPath)

        val tempBootstrapper = stageBootstrapperOnTemp()
        processLauncher.launch(buildCommand(tempBootstrapper, msiPath, expectedSha256, logPath))
    }

    private fun stageBootstrapperOnTemp(): Path {
        val tempDir = Path.of(System.getProperty("java.io.tmpdir"), "yalla-update")
        Files.createDirectories(tempDir)
        val target = tempDir.resolve("yalla-update-bootstrap.exe")
        Files.copy(bootstrapperPath, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    /**
     * Removes the NTFS Zone.Identifier alternate data stream so msiexec doesn't show the
     * "are you sure you want to run this?" SmartScreen warning at install time.
     *
     * Two strategies tried in order, because JDK behavior on ADS is inconsistent across
     * Windows builds:
     *  1. PowerShell `Unblock-File` — the canonical method, supported since PS 3.0 / Win 8+.
     *  2. NIO `Files.deleteIfExists("$msiPath:Zone.Identifier")` — fallback if PowerShell
     *     is unavailable or blocked by execution policy.
     *
     * Failure here is non-fatal: SmartScreen warning is annoying but not blocking, and we
     * don't want to abort an install over a missing Zone.Identifier stream.
     */
    private fun stripMarkOfTheWeb(msiPath: Path) {
        if (!System.getProperty("os.name").lowercase().contains("win")) return

        val viaPowerShell = runCatching {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command",
                "Unblock-File -LiteralPath \"$msiPath\"",
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(POWERSHELL_TIMEOUT_S, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                logger.warn { "Unblock-File timed out after ${POWERSHELL_TIMEOUT_S}s" }
                false
            } else {
                process.exitValue() == 0
            }
        }.onFailure { logger.warn(it) { "Unblock-File invocation failed" } }
            .getOrDefault(false)

        if (viaPowerShell) return

        runCatching {
            Files.deleteIfExists(Path.of("$msiPath:Zone.Identifier"))
        }.onFailure { logger.warn(it) { "Fallback ADS delete failed (non-fatal)" } }
    }

    companion object {
        private const val POWERSHELL_TIMEOUT_S = 5L

        private fun defaultBootstrapperPath(): Path {
            val resourcesDir = System.getProperty("compose.application.resources.dir")
            if (resourcesDir != null) {
                val packaged = Path.of(resourcesDir, "yalla-update-bootstrap.exe")
                if (packaged.exists()) return packaged
            }
            return Path.of(
                System.getProperty("user.dir"),
                "app-resources",
                "windows-x64",
                "yalla-update-bootstrap.exe",
            )
        }

        private fun defaultInstallDir(): Path {
            val os = System.getProperty("os.name").lowercase()
            return if (os.contains("win")) {
                val local = System.getenv("LOCALAPPDATA")
                    ?: (System.getProperty("user.home") + "\\AppData\\Local")
                Path.of(local, "YallaSipPhone")
            } else {
                Path.of(System.getProperty("user.dir"))
            }
        }

        private fun currentPid(): Long = ProcessHandle.current().pid()
    }
}
