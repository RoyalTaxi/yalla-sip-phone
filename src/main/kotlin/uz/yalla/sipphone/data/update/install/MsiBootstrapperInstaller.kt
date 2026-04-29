package uz.yalla.sipphone.data.update.install

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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

    private fun stripMarkOfTheWeb(msiPath: Path) {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        runCatching {
            val ads = File("$msiPath:Zone.Identifier")
            if (ads.exists()) ads.delete()
        }.onFailure { logger.warn(it) { "Failed to strip Zone.Identifier (non-fatal)" } }
    }

    companion object {

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
