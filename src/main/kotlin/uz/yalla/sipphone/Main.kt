package uz.yalla.sipphone

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.startKoin
import uz.yalla.sipphone.data.settings.AppSettings
import uz.yalla.sipphone.data.auth.AuthEventBus
import uz.yalla.sipphone.data.auth.LogoutOrchestrator
import uz.yalla.sipphone.data.jcef.BridgeEventEmitter
import uz.yalla.sipphone.data.jcef.JcefManager
import uz.yalla.sipphone.data.jcef.KeyShortcutRegistry
import uz.yalla.sipphone.data.update.UpdateManager
import uz.yalla.sipphone.di.appModules
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipStackLifecycle
import uz.yalla.sipphone.navigation.ComponentFactory
import uz.yalla.sipphone.navigation.RootComponent
import uz.yalla.sipphone.navigation.RootContent
import uz.yalla.sipphone.ui.component.SplashScreen
import uz.yalla.sipphone.ui.strings.Strings
import uz.yalla.sipphone.ui.theme.YallaSipPhoneTheme

private val logger = KotlinLogging.logger {}

private const val WINDOW_WIDTH = 1280
private const val WINDOW_HEIGHT = 720

fun main() {
    System.setProperty("compose.interop.blending", "true")
    System.setProperty("compose.layers.type", "WINDOW")

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error(throwable) { "Uncaught exception on ${thread.name}" }
    }

    val koin = startKoin { modules(appModules) }.koin

    val updateManager: UpdateManager = koin.get()
    updateManager.start()

    val lifecycle: SipStackLifecycle = koin.get()
    val initResult = runBlocking { lifecycle.initialize() }

    if (initResult.isFailure) {
        javax.swing.JOptionPane.showMessageDialog(
            null,
            Strings.errorInitMessage(initResult.exceptionOrNull()?.message),
            Strings.ERROR_INIT_TITLE,
            javax.swing.JOptionPane.ERROR_MESSAGE,
        )
        return
    }

    val jcefManager: JcefManager = koin.get()
    // NOTE: JCEF is intentionally NOT initialized here.
    // Per JetBrains/compose-multiplatform#2939, initializing JCEF before the Compose Window
    // exists causes (on macOS) the Chromium NSView to attach with broken geometry — the
    // webview then renders blank until the user triggers a resize. Init is moved into a
    // LaunchedEffect inside the Window composable below, so JCEF attaches to a live window.

    // gracefulShutdown can fire from three sources concurrently:
    //   (a) AWT window close (onCloseRequest)
    //   (b) Runtime shutdown hook (Ctrl+C / SIGTERM)
    //   (c) UpdateManager.onBeforeExit (before MSI installer launches)
    // A single gate ensures each step runs exactly once.
    val shutdownDone = AtomicBoolean(false)

    fun gracefulShutdown() {
        if (!shutdownDone.compareAndSet(false, true)) return
        runCatching { updateManager.stop() }
            .onFailure { logger.warn(it) { "updateManager.stop() failed" } }
        runCatching {
            runBlocking {
                withTimeoutOrNull(SipConstants.Timeout.DESTROY_MS) { lifecycle.shutdown() }
            }
        }.onFailure { logger.warn(it) { "lifecycle.shutdown() failed" } }
        runCatching { jcefManager.shutdown() }
            .onFailure { logger.warn(it) { "jcefManager.shutdown() failed" } }
    }

    Runtime.getRuntime().addShutdownHook(Thread(::gracefulShutdown))
    updateManager.onBeforeExit = ::gracefulShutdown

    val decomposeLifecycle = LifecycleRegistry()
    val factory: ComponentFactory = koin.get()
    val authEventBus: AuthEventBus = koin.get()
    val logoutOrchestrator: LogoutOrchestrator = koin.get()
    val rootComponent = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = decomposeLifecycle),
            factory = factory,
            authEventBus = authEventBus,
            logoutOrchestrator = logoutOrchestrator,
        )
    }

    val appSettings = koin.get<AppSettings>()

    application {
        var isDarkTheme by remember { mutableStateOf(appSettings.isDarkTheme) }
        var locale by remember { mutableStateOf(appSettings.locale) }

        val childStack by rootComponent.childStack.subscribeAsState()
        val isMainScreen = childStack.active.instance is RootComponent.Child.Main

        val windowState = rememberWindowState(
            size = DpSize(WINDOW_WIDTH.dp, WINDOW_HEIGHT.dp),
            position = WindowPosition(Alignment.Center),
        )

        val agentName = (childStack.active.instance as? RootComponent.Child.Main)
            ?.component?.agentInfo?.name.orEmpty()
        val windowTitle = if (isMainScreen) {
            "${Strings.APP_TITLE} \u2014 $agentName"
        } else {
            Strings.APP_TITLE
        }

        Window(
            onCloseRequest = {
                gracefulShutdown()
                exitApplication()
            },
            title = windowTitle,
            state = windowState,
            alwaysOnTop = false,
            resizable = isMainScreen,
        ) {
            LaunchedEffect(isMainScreen) {
                javax.swing.SwingUtilities.invokeLater {
                    window.minimumSize = java.awt.Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)
                }
            }

            // Fixes compose-multiplatform#2939 on macOS: if JCEF is initialized before the
            // Compose Window exists, the Chromium NSView attaches with broken geometry and
            // the webview stays blank until a manual resize. Init here, after the Window is
            // live, then block navigation behind a splash until it finishes so downstream
            // components (MainComponent's bridge router, WebviewPanel's createBrowser) are
            // guaranteed a ready JcefManager.
            //
            // Offloaded to Dispatchers.IO because JcefManager.initialize uses
            // SwingUtilities.invokeAndWait internally — calling it from the AWT EDT
            // (Compose Desktop's Dispatchers.Main) would deadlock.
            var jcefReady by remember { mutableStateOf(jcefManager.isInitialized) }
            LaunchedEffect(Unit) {
                if (!jcefManager.isInitialized) {
                    withContext(Dispatchers.IO) {
                        runCatching { jcefManager.initialize(debugPort = 9222) }
                            .onFailure { logger.error(it) { "JCEF initialization failed" } }
                    }
                }
                jcefReady = true
            }

            // AWT-level shortcuts — bypasses Compose/JCEF focus issues
            DisposableEffect(Unit) {
                val listener = java.awt.event.AWTEventListener { event ->
                    if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED) {
                        handleKeyboardShortcut(event, rootComponent)
                    }
                }
                java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
                onDispose {
                    java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
                }
            }

            LifecycleController(decomposeLifecycle, windowState)

            YallaSipPhoneTheme(isDark = isDarkTheme, locale = locale) {
                if (!jcefReady) {
                    SplashScreen()
                } else {
                    RootContent(
                        root = rootComponent,
                        isDarkTheme = isDarkTheme,
                        locale = locale,
                        onThemeToggle = {
                            isDarkTheme = !isDarkTheme
                            appSettings.isDarkTheme = isDarkTheme
                        },
                        onLocaleChange = { newLocale ->
                            locale = newLocale
                            appSettings.locale = newLocale
                        },
                    )
                }
            }
        }
    }
}

private fun handleKeyboardShortcut(event: KeyEvent, rootComponent: RootComponent) {
    val ctrl = event.isControlDown
    val shift = event.isShiftDown

    // Two native-only debug shortcuts stay hardcoded — they're app-level debug surfaces that
    // the web team shouldn't own or be able to override through the bridge. Everything else
    // is frontend-driven: the web panel calls `registerKeyListeners` on handshake and native
    // only listens for the combos it's explicitly asked to.
    when {
        ctrl && shift && event.isAltDown && event.keyCode == KeyEvent.VK_B -> {
            val settings: AppSettings =
                org.koin.java.KoinJavaComponent.get(AppSettings::class.java)
            settings.updateChannel = if (settings.updateChannel == "beta") "stable" else "beta"
            logger.info { "Update channel toggled: ${settings.updateChannel}" }
            event.consume()
            return
        }
        ctrl && shift && event.isAltDown && event.keyCode == KeyEvent.VK_D -> {
            val um: UpdateManager =
                org.koin.java.KoinJavaComponent.get(UpdateManager::class.java)
            um.toggleDiagnostics()
            event.consume()
            return
        }
    }

    // Only dispatch registered shortcuts once the main screen is showing — on the login
    // screen there's no bridge to emit events to.
    val currentChild = rootComponent.childStack.value.active.instance
    if (currentChild !is RootComponent.Child.Main) return

    val registry: KeyShortcutRegistry =
        org.koin.java.KoinJavaComponent.get(KeyShortcutRegistry::class.java)
    val matched = registry.match(event) ?: return
    val emitter: BridgeEventEmitter =
        org.koin.java.KoinJavaComponent.get(BridgeEventEmitter::class.java)
    emitter.emitKeyPressed(matched)
    // Consume the event so JCEF / Compose don't process it a second time — the frontend
    // is solely responsible for acting on registered shortcuts from here.
    event.consume()
}

private fun <T> runOnUiThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()

    var error: Throwable? = null
    var result: T? = null

    SwingUtilities.invokeAndWait {
        try {
            result = block()
        } catch (e: Throwable) {
            error = e
        }
    }

    error?.let { throw it }

    @Suppress("UNCHECKED_CAST")
    return result as T
}
