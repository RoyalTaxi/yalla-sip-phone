package uz.yalla.sipphone.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.domain.RegistrationState

@Composable
fun ConnectButton(
    state: RegistrationState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        when (state) {
            is RegistrationState.Idle -> {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Connect") }
            }
            is RegistrationState.Registering -> {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {}, enabled = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting...")
                    }
                }
            }
            is RegistrationState.Registered -> {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
            }
            is RegistrationState.Failed -> {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
            }
        }
    }
}
