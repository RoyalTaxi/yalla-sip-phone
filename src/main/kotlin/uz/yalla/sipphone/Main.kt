package uz.yalla.sipphone

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.startKoin
import uz.yalla.sipphone.di.appModules
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipStackLifecycle
import uz.yalla.sipphone.navigation.ComponentFactory
import uz.yalla.sipphone.navigation.RootComponent
import uz.yalla.sipphone.navigation.RootContent
import uz.yalla.sipphone.ui.theme.YallaSipPhoneTheme

private val logger = KotlinLogging.logger {}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error(throwable) { "Uncaught exception on ${thread.name}" }
    }

    val koin = startKoin { modules(appModules) }.koin

    val lifecycle: SipStackLifecycle = koin.get()
    val initResult = runBlocking { lifecycle.initialize() }

    if (initResult.isFailure) {
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "Failed to initialize SIP engine:\n${initResult.exceptionOrNull()?.message}",
            "Yalla SIP Phone - Error",
            javax.swing.JOptionPane.ERROR_MESSAGE,
        )
        return
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            withTimeoutOrNull(SipConstants.Timeout.DESTROY_MS) { lifecycle.shutdown() }
        }
    })

    val decomposeLifecycle = LifecycleRegistry()
    val factory: ComponentFactory = koin.get()
    val rootComponent = runOnUiThread {
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = decomposeLifecycle),
            factory = factory,
        )
    }

    application {
        val windowState = rememberWindowState(
            size = DpSize(420.dp, 520.dp),
            position = WindowPosition(Alignment.Center),
        )

        Window(
            onCloseRequest = {
                runBlocking {
                    withTimeoutOrNull(SipConstants.Timeout.DESTROY_MS) { lifecycle.shutdown() }
                }
                exitApplication()
            },
            title = "Yalla SIP Phone",
            state = windowState,
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(380, 180)
            }

            LifecycleController(decomposeLifecycle, windowState)

            YallaSipPhoneTheme {
                RootContent(rootComponent, windowState)
            }
        }
    }
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
