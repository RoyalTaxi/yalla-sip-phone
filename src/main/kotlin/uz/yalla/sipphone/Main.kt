package uz.yalla.sipphone

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import uz.yalla.sipphone.sip.SipClient
import uz.yalla.sipphone.sip.SipTransport
import uz.yalla.sipphone.ui.theme.YallaSipPhoneTheme

fun main() {
    val transport = SipTransport()
    val sipClient = SipClient(transport)

    application {
        Window(
            onCloseRequest = {
                runBlocking(Dispatchers.IO) { sipClient.unregister() }
                sipClient.close()
                exitApplication()
            },
            title = "Yalla SIP Phone",
            state = rememberWindowState(
                size = DpSize(420.dp, 600.dp),
                position = WindowPosition(Alignment.Center)
            )
        ) {
            YallaSipPhoneTheme {
                App(sipClient)
            }
        }
    }
}
