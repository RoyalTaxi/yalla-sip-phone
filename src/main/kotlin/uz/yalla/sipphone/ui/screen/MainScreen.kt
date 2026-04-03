package uz.yalla.sipphone.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.domain.ConnectionState
import uz.yalla.sipphone.domain.SipCredentials
import uz.yalla.sipphone.ui.component.ConnectButton
import uz.yalla.sipphone.ui.component.ConnectionStatusCard
import uz.yalla.sipphone.ui.component.FormErrors
import uz.yalla.sipphone.ui.component.FormState
import uz.yalla.sipphone.ui.component.SipCredentialsForm
import uz.yalla.sipphone.ui.component.validateForm

@Composable
fun MainScreen(
    connectionState: ConnectionState,
    onConnect: (SipCredentials) -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit
) {
    var formState by remember { mutableStateOf(FormState(server = "192.168.0.22", username = "102")) }
    var formErrors by remember { mutableStateOf(FormErrors()) }

    val formEnabled = connectionState is ConnectionState.Idle || connectionState is ConnectionState.Failed
    val formAlpha by animateFloatAsState(
        targetValue = if (formEnabled) 1f else 0.6f, animationSpec = tween(300)
    )

    val submitAction = {
        val errors = validateForm(formState)
        formErrors = errors
        if (!errors.hasErrors) {
            onConnect(SipCredentials(
                server = formState.server.trim(),
                port = formState.port.toIntOrNull() ?: 5060,
                username = formState.username.trim(),
                password = formState.password
            ))
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("SIP Registration", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            SipCredentialsForm(
                formState = formState, errors = formErrors, enabled = formEnabled,
                onFormChange = { formState = it; formErrors = FormErrors() },
                onSubmit = submitAction,
                modifier = Modifier.alpha(formAlpha)
            )
            Spacer(Modifier.height(24.dp))
            ConnectButton(
                state = connectionState,
                onConnect = submitAction,
                onDisconnect = onDisconnect,
                onCancel = onCancel
            )
            Spacer(Modifier.height(16.dp))
            ConnectionStatusCard(state = connectionState)
        }
    }
}
