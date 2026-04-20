package uz.yalla.sipphone.showcase

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Component showcase entry point.
 *
 * Run with:
 *   ./gradlew showcase
 *
 * Mostly no real dependencies — fakes stand in for SIP and backend. The only exception is
 * the "Interop / Heavyweight" case, which spins up a real JCEF browser to validate that
 * tooltips / dropdowns / the settings panel render correctly above Chromium content.
 */
fun main() {
    // CRITICAL: these must be set BEFORE any Compose code runs. Setting them inside
    // `application { }` is too late — Compose has already initialized its rendering pipeline
    // and these flags are ignored.
    //   - compose.interop.blending=true: lets Compose draw above some Swing content
    //   - compose.layers.type=WINDOW: Popup/Dialog use native OS windows, which is what
    //     lets tooltips / dropdowns / dialogs render above heavyweight AWT (like JCEF).
    System.setProperty("compose.interop.blending", "true")
    System.setProperty("compose.layers.type", "WINDOW")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Yalla SIP — Component Showcase",
            state = rememberWindowState(
                size = DpSize(1400.dp, 900.dp),
                position = WindowPosition(Alignment.Center),
            ),
            alwaysOnTop = false,
            resizable = true,
        ) {
            ShowcaseApp()
        }
    }
}
