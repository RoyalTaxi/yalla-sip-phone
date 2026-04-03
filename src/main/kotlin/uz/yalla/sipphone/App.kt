package uz.yalla.sipphone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uz.yalla.sipphone.sip.SipClient
import uz.yalla.sipphone.ui.screen.MainScreen

@Composable
fun App(sipClient: SipClient) {
    val connectionState by sipClient.state.collectAsState()
    val scope = rememberCoroutineScope()
    var registerJob: Job? = null

    MainScreen(
        connectionState = connectionState,
        onConnect = { credentials ->
            registerJob?.cancel()
            registerJob = scope.launch(Dispatchers.IO) { sipClient.register(credentials) }
        },
        onDisconnect = { scope.launch(Dispatchers.IO) { sipClient.unregister() } },
        onCancel = { registerJob?.cancel() }
    )
}
