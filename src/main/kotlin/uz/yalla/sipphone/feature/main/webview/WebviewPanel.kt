package uz.yalla.sipphone.feature.main.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.cef.browser.CefBrowser
import uz.yalla.sipphone.data.jcef.JcefManager

/**
 * Hosts the JCEF Chromium browser inside a Compose [SwingPanel]. JCEF initialization is
 * guaranteed to have completed before this composable mounts — `Main.kt` gates all of
 * `RootContent` behind a splash screen until `jcefManager.isInitialized` is true.
 */
@Composable
fun WebviewPanel(
    jcefManager: JcefManager,
    dispatcherUrl: String,
    modifier: Modifier = Modifier,
) {
    val browser: CefBrowser = remember(dispatcherUrl) {
        jcefManager.createBrowser(dispatcherUrl)
    }

    DisposableEffect(dispatcherUrl) {
        onDispose {
            if (!jcefManager.isClosed()) {
                browser.stopLoad()
                browser.loadURL("about:blank")
            }
        }
    }

    SwingPanel(
        modifier = modifier,
        factory = { browser.uiComponent },
    )
}
