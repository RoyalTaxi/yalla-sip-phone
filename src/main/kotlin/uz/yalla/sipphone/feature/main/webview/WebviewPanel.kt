package uz.yalla.sipphone.feature.main.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import uz.yalla.sipphone.data.jcef.JcefManager
import java.awt.BorderLayout
import javax.swing.JPanel

@Composable
fun WebviewPanel(
    jcefManager: JcefManager,
    dispatcherUrl: String,
    modifier: Modifier = Modifier,
) {
    SwingPanel(
        modifier = modifier,
        factory = {
            val browser = jcefManager.createBrowser(dispatcherUrl)
            val panel = JPanel(BorderLayout())
            panel.add(browser.uiComponent, BorderLayout.CENTER)
            panel
        },
    )
}
