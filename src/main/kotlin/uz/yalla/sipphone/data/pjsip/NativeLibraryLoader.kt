package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import uz.yalla.sipphone.domain.SipConstants
import java.io.File

private val logger = KotlinLogging.logger {}

object NativeLibraryLoader {

    /**
     * Windows pjsip build links against OpenSSL 3 (libcrypto, libssl) for TLS
     * transport. These DLLs must be loaded into the process BEFORE pjsua2.dll
     * so the Windows loader can resolve pjsua2's imports via already-loaded
     * modules — otherwise it searches the exe's directory + PATH and fails.
     *
     * Order matters: libcrypto first (libssl depends on it), then libssl,
     * then pjsua2.
     */
    private val WINDOWS_DEPS = listOf("libcrypto-3-x64.dll", "libssl-3-x64.dll")

    fun load() {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val libName = when {
            osName.contains("mac") || osName.contains("darwin") -> SipConstants.NativeLib.MAC
            isWindows -> SipConstants.NativeLib.WINDOWS
            else -> SipConstants.NativeLib.LINUX
        }

        val devDir = System.getProperty("pjsip.library.path")
        if (devDir != null) {
            val devLib = File("$devDir/$libName")
            if (devLib.exists()) {
                if (isWindows) preloadWindowsDeps(devDir)
                System.load(devLib.absolutePath)
                logger.info { "Loaded native library from dev path: ${devLib.absolutePath}" }
                return
            }
        }

        val resourcesDir = System.getProperty("compose.application.resources.dir")
        if (resourcesDir != null) {
            val packagedLib = File("$resourcesDir/$libName")
            if (packagedLib.exists()) {
                if (isWindows) preloadWindowsDeps(resourcesDir)
                System.load(packagedLib.absolutePath)
                logger.info { "Loaded native library from resources: ${packagedLib.absolutePath}" }
                return
            }
        }

        System.loadLibrary(SipConstants.NativeLib.FALLBACK)
        logger.info { "Loaded native library from system path: ${SipConstants.NativeLib.FALLBACK}" }
    }

    private fun preloadWindowsDeps(dir: String) {
        for (dep in WINDOWS_DEPS) {
            val depFile = File(dir, dep)
            if (depFile.exists()) {
                runCatching {
                    System.load(depFile.absolutePath)
                    logger.info { "Preloaded Windows dep: ${depFile.absolutePath}" }
                }.onFailure { t ->
                    logger.warn(t) { "Failed to preload $dep (pjsua2 may still load if Windows finds it elsewhere)" }
                }
            } else {
                logger.warn { "Windows dep not found: ${depFile.absolutePath}" }
            }
        }
    }
}
