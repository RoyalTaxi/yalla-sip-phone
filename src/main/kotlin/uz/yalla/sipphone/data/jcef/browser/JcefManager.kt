package uz.yalla.sipphone.data.jcef.browser

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import java.io.File
import javax.swing.SwingUtilities

private val logger = KotlinLogging.logger {}

class JcefManager {
    private var cefApp: CefApp? = null
    private var cefClient: CefClient? = null
    private var browser: CefBrowser? = null
    private var lifeSpanHandler: CefLifeSpanHandlerAdapter? = null
    private var loadHandler: CefLoadHandler? = null
    private val shutdownDone = AtomicBoolean(false)

    @Volatile
    private var isBrowserClosed = false

    val isInitialized: Boolean get() = cefApp != null

    fun initialize(debugPort: Int = 0) {
        if (cefApp != null) return
        logger.info { "Initializing JCEF..." }

        SwingUtilities.invokeAndWait {
            val builder = CefAppBuilder()

            val resourcesDir = System.getProperty("compose.application.resources.dir")
            val jcefDir = if (resourcesDir != null) {
                val bundledDir = File(resourcesDir, "jcef-bundle")
                if (bundledDir.canWrite()) {
                    bundledDir
                } else {

                    val userDir = File(System.getProperty("user.home"), ".yalla-sip-phone/jcef-bundle")
                    if (!userDir.exists() && bundledDir.exists()) {
                        logger.info { "Copying JCEF bundle to writable location: ${userDir.absolutePath}" }
                        bundledDir.copyRecursively(userDir, overwrite = true)
                    }
                    userDir
                }
            } else {
                File("jcef-bundle")
            }
            builder.setInstallDir(jcefDir)

            builder.cefSettings.apply {
                windowless_rendering_enabled = false
                log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
                remote_debugging_port = if (debugPort > 0) debugPort else 0
            }

            builder.setAppHandler(object : MavenCefAppHandlerAdapter() {})

            val app = builder.build()
            val client = app.createClient()
            val handler = object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean = true
            }
            client.addLifeSpanHandler(handler)
            cefApp = app
            cefClient = client
            lifeSpanHandler = handler
        }
    }

    fun createBrowser(url: String): CefBrowser {
        val client = checkNotNull(cefClient) { "JCEF not initialized — call initialize() first" }
        val create = Runnable {
            val previous = browser
            browser = client.createBrowser(url, false, false).also { it.createImmediately() }
            isBrowserClosed = false
            previous?.close(true)
        }
        if (SwingUtilities.isEventDispatchThread()) create.run() else SwingUtilities.invokeAndWait(create)
        return checkNotNull(browser) { "Browser creation failed" }
    }

    fun setupBridge(
        installMessageRouter: (CefClient) -> Unit,
        onPageLoadEnd: (CefBrowser) -> Unit,
        onPageLoadStart: () -> Unit,
    ) {
        val client = cefClient ?: throw IllegalStateException("JCEF not initialized — call initialize() first")

        installMessageRouter(client)

        val handler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) onPageLoadEnd(browser)
            }

            override fun onLoadStart(
                browser: CefBrowser,
                frame: CefFrame,
                transitionType: CefRequest.TransitionType,
            ) {
                if (frame.isMain) onPageLoadStart()
            }
        }
        loadHandler = handler
        client.addLoadHandler(handler)
    }

    fun teardownBridge() {
        cefClient?.removeLoadHandler()
        loadHandler = null
    }

    fun isClosed(): Boolean = isBrowserClosed

    fun shutdown() {
        if (!shutdownDone.compareAndSet(false, true)) return
        val app = cefApp ?: return
        val shutdownWork = Runnable {
            try {
                browser?.let { b ->
                    b.stopLoad()
                    b.close(true)
                    isBrowserClosed = true
                }
                browser = null

                loadHandler?.let { cefClient?.removeLoadHandler() }
                loadHandler = null
                lifeSpanHandler?.let { cefClient?.removeLifeSpanHandler() }
                lifeSpanHandler = null

                cefClient?.dispose()
                cefClient = null
                app.dispose()
                cefApp = null
            } catch (e: Exception) {
                logger.warn(e) { "JCEF shutdown error (non-fatal)" }
                cefApp = null
                cefClient = null
                browser = null
            }
        }

        if (SwingUtilities.isEventDispatchThread()) {
            shutdownWork.run()
        } else {
            try {
                SwingUtilities.invokeAndWait(shutdownWork)
            } catch (e: Exception) {
                logger.warn(e) { "JCEF shutdown via EDT failed (non-fatal)" }
                cefApp = null
                cefClient = null
                browser = null
            }
        }

    }
}
