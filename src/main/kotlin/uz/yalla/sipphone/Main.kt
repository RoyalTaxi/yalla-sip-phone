package uz.yalla.sipphone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.startKoin
import uz.yalla.sipphone.core.auth.SessionExpiredSignal
import uz.yalla.sipphone.core.auth.SessionStore
import uz.yalla.sipphone.core.prefs.ConfigPreferences
import uz.yalla.sipphone.core.prefs.UserPreferences
import uz.yalla.sipphone.data.jcef.browser.JcefManager
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.data.jcef.keys.KeyShortcutRegistry
import uz.yalla.sipphone.data.update.manager.UpdateManager
import uz.yalla.sipphone.di.appModule
import uz.yalla.sipphone.domain.auth.usecase.LogoutUseCase
import uz.yalla.sipphone.domain.sip.SipConstants
import uz.yalla.sipphone.domain.sip.SipStackLifecycle
import uz.yalla.sipphone.navigation.ComponentFactory
import uz.yalla.sipphone.navigation.RootComponent
import uz.yalla.sipphone.navigation.RootContent
import uz.yalla.sipphone.ui.component.SplashScreen
import uz.yalla.sipphone.ui.strings.RuStrings
import uz.yalla.sipphone.ui.strings.StringResources
import uz.yalla.sipphone.ui.strings.UzStrings
import uz.yalla.sipphone.ui.theme.YallaSipPhoneTheme

private val logger = KotlinLogging.logger {}

private const val WINDOW_WIDTH = 1280
private const val WINDOW_HEIGHT = 720
private const val JCEF_DEBUG_PORT = 9222
private const val CHANNEL_BETA = "beta"
private const val CHANNEL_STABLE = "stable"

fun main() {
    System.setProperty("compose.interop.blending", "true")
    System.setProperty("compose.layers.type", "WINDOW")

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error(throwable) { "Uncaught exception on ${thread.name}" }
    }

    val koin = startKoin { modules(appModule) }.koin

    val updateManager: UpdateManager = koin.get()
    updateManager.start()

    val lifecycle: SipStackLifecycle = koin.get()
    val initResult = runBlocking { lifecycle.initialize() }
    if (initResult.isFailure) {
        val strings = stringsForLocale(koin.get<uz.yalla.sipphone.core.prefs.UserPreferences>().current().locale)
        showFatalDialog(strings, initResult.exceptionOrNull()?.message)
        return
    }

    val jcefManager: JcefManager = koin.get()
    val shutdownDone = AtomicBoolean(false)

    /**
     * Runs the actual teardown. MUST NOT be called from the AWT EDT — `runBlocking` here
     * blocks the calling thread until PJSIP and JCEF finish, and JCEF disposal pumps EDT
     * messages internally, so calling on EDT is a deadlock setup.
     *
     * Window-close, JVM shutdown hook, and the auto-update path all funnel through here,
     * but each indirection makes sure the actual call lands off-EDT.
     */
    fun gracefulShutdown() {
        if (!shutdownDone.compareAndSet(false, true)) return
        check(!SwingUtilities.isEventDispatchThread()) {
            "gracefulShutdown() must not run on EDT — JCEF dispose pumps EDT and would deadlock"
        }
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
    val sessionStore: SessionStore = koin.get()
    val sessionExpired: SessionExpiredSignal = koin.get()
    val logoutUseCase: LogoutUseCase = koin.get()
    val userPreferences: UserPreferences = koin.get()
    val configPreferences: ConfigPreferences = koin.get()

    val rootComponent = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = decomposeLifecycle),
            factory = factory,
            sessionStore = sessionStore,
            sessionExpired = sessionExpired,
            logoutUseCase = logoutUseCase,
        )
    }

    application {
        val userPrefs by userPreferences.values.collectAsState(initial = userPreferences.current())
        val childStack by rootComponent.childStack.subscribeAsState()
        val isWorkstation = childStack.active.instance is RootComponent.Child.Workstation

        val windowState = rememberWindowState(
            size = DpSize(WINDOW_WIDTH.dp, WINDOW_HEIGHT.dp),
            position = WindowPosition(Alignment.Center),
        )

        val strings = stringsForLocale(userPrefs.locale)
        val agentName = (childStack.active.instance as? RootComponent.Child.Workstation)
            ?.component?.container?.stateFlow?.value?.agentInfo?.name.orEmpty()
        val windowTitle = if (isWorkstation) "${strings.appTitle} — $agentName" else strings.appTitle

        Window(
            onCloseRequest = {
                // Don't run shutdown on the EDT — exit the Compose application here, then let
                // the JVM shutdown hook fire `gracefulShutdown` on its own non-EDT thread.
                exitApplication()
            },
            title = windowTitle,
            state = windowState,
            alwaysOnTop = false,
            resizable = isWorkstation,
        ) {
            LaunchedEffect(isWorkstation) {
                SwingUtilities.invokeLater {
                    window.minimumSize = Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)
                }
            }

            val jcefReady = rememberJcefReady(jcefManager)

            val keyRegistry: KeyShortcutRegistry = remember { koin.get() }
            val bridgeEmitter: BridgeEventEmitter = remember { koin.get() }
            DisposableEffect(Unit) {
                val listener = AWTEventListener { event ->
                    if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED) {
                        handleKeyboardShortcut(
                            event = event,
                            rootComponent = rootComponent,
                            configPreferences = configPreferences,
                            updateManager = updateManager,
                            keyRegistry = keyRegistry,
                            emitter = bridgeEmitter,
                        )
                    }
                }
                Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
                onDispose {
                    Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
                }
            }

            LifecycleController(decomposeLifecycle, windowState)

            YallaSipPhoneTheme(isDark = userPrefs.isDarkTheme, locale = userPrefs.locale) {
                if (!jcefReady) {
                    SplashScreen()
                } else {
                    RootContent(root = rootComponent)
                }
            }
        }
    }
}

@Composable
private fun rememberJcefReady(jcefManager: JcefManager): Boolean {
    var ready by remember { mutableStateOf(jcefManager.isInitialized) }
    LaunchedEffect(Unit) {
        if (!jcefManager.isInitialized) {
            withContext(Dispatchers.IO) {
                runCatching { jcefManager.initialize(debugPort = JCEF_DEBUG_PORT) }
                    .onFailure { logger.error(it) { "JCEF initialization failed" } }
            }
        }
        ready = true
    }
    return ready
}

private fun handleKeyboardShortcut(
    event: KeyEvent,
    rootComponent: RootComponent,
    configPreferences: ConfigPreferences,
    updateManager: UpdateManager,
    keyRegistry: KeyShortcutRegistry,
    emitter: BridgeEventEmitter,
) {
    val modifiers = event.isControlDown && event.isShiftDown && event.isAltDown
    when {
        modifiers && event.keyCode == KeyEvent.VK_B -> {
            val current = configPreferences.current().updateChannel
            configPreferences.setUpdateChannel(if (current == CHANNEL_BETA) CHANNEL_STABLE else CHANNEL_BETA)
            event.consume()
            return
        }
        modifiers && event.keyCode == KeyEvent.VK_D -> {
            updateManager.toggleDiagnostics()
            event.consume()
            return
        }
    }

    if (rootComponent.childStack.value.active.instance !is RootComponent.Child.Workstation) return
    val matched = keyRegistry.match(event) ?: return
    emitter.emitKeyPressed(matched)
    event.consume()
}

private fun showFatalDialog(strings: StringResources, reason: String?) {
    JOptionPane.showMessageDialog(
        null,
        strings.errorInitMessage(reason),
        strings.errorInitTitle,
        JOptionPane.ERROR_MESSAGE,
    )
}

private fun stringsForLocale(locale: String): StringResources =
    if (locale.equals("ru", ignoreCase = true)) RuStrings else UzStrings

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
