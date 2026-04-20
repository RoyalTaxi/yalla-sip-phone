package uz.yalla.sipphone.data.jcef

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
            // Packaged app on Windows: Program Files is read-only, use user-writable dir
            // Dev mode: fall back to project-relative jcef-bundle/
            val resourcesDir = System.getProperty("compose.application.resources.dir")
            val jcefDir = if (resourcesDir != null) {
                val bundledDir = File(resourcesDir, "jcef-bundle")
                if (bundledDir.canWrite()) {
                    bundledDir
                } else {
                    // Copy to writable location if needed (Windows Program Files is read-only)
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

            cefApp = builder.build()
            cefClient = cefApp!!.createClient()

            // Block all popup windows — dispatcher UI must stay in our single browser
            val handler = object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean = true
            }
            lifeSpanHandler = handler
            cefClient!!.addLifeSpanHandler(handler)
        }
    }

    fun createBrowser(url: String): CefBrowser {
        val client = cefClient ?: throw IllegalStateException("JCEF not initialized — call initialize() first")

        val create: () -> Unit = {
            val oldBrowser = browser
            browser = client.createBrowser(url, false, false).also { it.createImmediately() }
            isBrowserClosed = false
            oldBrowser?.close(true)
        }
        if (SwingUtilities.isEventDispatchThread()) create() else SwingUtilities.invokeAndWait(create)
        return browser!!
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

    fun getBrowser(): CefBrowser? = browser

    fun isClosed(): Boolean = isBrowserClosed

    /**
     * Kicks the browser's native component into performing its first paint.
     *
     * On macOS, Compose's SwingPanel + JCEF's heavyweight Canvas interact badly on first
     * layout: the NSView is attached but ignores Compose-layer size updates until it
     * receives a componentResized event *directly*. Window-level resizes don't always
     * propagate down. This method hits the `uiComponent` directly: force-re-set its bounds,
     * invalidate, repaint, and hand it focus. Idempotent — safe to call many times.
     *
     * Returns true if the browser was ready and got nudged; false if there's nothing to
     * nudge yet (caller should poll).
     */
    fun nudge(): Boolean {
        val c = browser?.uiComponent ?: return false
        if (c.width == 0 || c.height == 0) return false
        val work = Runnable {
            // Re-assert bounds so CEF's native side sees a "new" size and triggers paint.
            val w = c.width; val h = c.height
            c.setBounds(c.x, c.y, w + 1, h)
            c.setBounds(c.x, c.y, w, h)
            c.invalidate()
            c.validate()
            c.repaint()
            runCatching { browser?.setFocus(true) }
        }
        if (SwingUtilities.isEventDispatchThread()) work.run() else SwingUtilities.invokeLater(work)
        return true
    }

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

                // Remove handlers so they can't keep references to this manager.
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
