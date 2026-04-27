package uz.yalla.sipphone.feature.auth.presentation.view.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.feature.auth.presentation.intent.ManualAccountEntry
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens

@Composable
fun ManualConnectionSheet(
    loading: Boolean,
    onConnect: (List<ManualAccountEntry>) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val tokens = LocalAppTokens.current

    val entries = remember { mutableStateListOf<ManualAccountEntry>() }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5060") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(strings.loginManualConnection) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text(strings.labelServer) },
                    placeholder = { Text(strings.placeholderServer) },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text(strings.labelPort) },
                        singleLine = true,
                        enabled = !loading,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(strings.labelUsername) },
                        placeholder = { Text(strings.placeholderUsername) },
                        singleLine = true,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.labelPassword) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val entry = ManualAccountEntry(
                            server = server.trim(),
                            port = port.toIntOrNull() ?: 5060,
                            username = username.trim(),
                            password = password,
                        )
                        if (entries.none { it.displayKey == entry.displayKey }) {
                            entries += entry
                        }
                        server = ""
                        username = ""
                        password = ""
                    },
                    enabled = !loading && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.manualAddAccount)
                }
                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacingSm))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(entries) { _, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = entry.displayKey, modifier = Modifier.weight(1f))
                                TextButton(onClick = { entries.remove(entry) }, enabled = !loading) {
                                    Text("×")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(entries.toList()) },
                enabled = !loading && entries.isNotEmpty(),
            ) { Text(strings.manualConnectAll) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(strings.buttonCancel)
            }
        },
    )
}
