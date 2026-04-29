package uz.yalla.sipphone.feature.auth.presentation.view.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import uz.yalla.sipphone.feature.auth.presentation.intent.ManualAccountEntry
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

private const val DEFAULT_SIP_PORT = 5060

@Composable
fun ManualConnectionSheet(
    isLoading: Boolean,
    onConnect: (
        accounts: List<ManualAccountEntry>,
        dispatcherUrl: String,
        backendUrl: String,
        pin: String,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current
    val colors = LocalYallaColors.current

    var accounts by remember { mutableStateOf(listOf<ManualAccountEntry>()) }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(DEFAULT_SIP_PORT.toString()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var dispatcherUrl by remember { mutableStateOf("") }
    var backendUrl by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var duplicateWarning by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val canAdd = server.isNotBlank() && username.isNotBlank() && !isLoading
    val canConnect = accounts.isNotEmpty() && !isLoading

    fun addAccount() {
        val entry = ManualAccountEntry(
            server = server,
            port = port.toIntOrNull() ?: DEFAULT_SIP_PORT,
            username = username,
            password = password,
        )
        if (accounts.any { it.displayKey == entry.displayKey }) {
            duplicateWarning = true
            return
        }
        accounts = accounts + entry
        username = ""
        password = ""
        duplicateWarning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.loginManualConnection) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PinField(
                    value = pin,
                    onValueChange = { pin = it },
                    isLoading = isLoading,
                    tokens = tokens,
                    strings = strings,
                    subtleColor = colors.textSubtle,
                )

                Spacer(Modifier.height(4.dp))

                AccountList(
                    accounts = accounts,
                    isLoading = isLoading,
                    onRemove = { index ->
                        accounts = accounts.filterIndexed { i, _ -> i != index }
                        duplicateWarning = false
                    },
                    emptyText = strings.manualNoAccounts,
                    subtleColor = colors.textSubtle,
                )

                ServerPortRow(
                    server = server,
                    port = port,
                    onServerChange = {
                        server = it
                        duplicateWarning = false
                    },
                    onPortChange = { port = it.filter(Char::isDigit).take(5) },
                    isLoading = isLoading,
                    tokens = tokens,
                    strings = strings,
                    subtleColor = colors.textSubtle,
                )

                UsernamePasswordRow(
                    username = username,
                    password = password,
                    onUsernameChange = {
                        username = it
                        duplicateWarning = false
                    },
                    onPasswordChange = { password = it },
                    isLoading = isLoading,
                    tokens = tokens,
                    strings = strings,
                    subtleColor = colors.textSubtle,
                )

                if (duplicateWarning) {
                    Text(
                        strings.manualDuplicateAccount,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.statusWarning,
                    )
                }

                TextButton(
                    onClick = ::addAccount,
                    enabled = canAdd,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(strings.manualAddAccount) }

                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(
                        strings.manualAdvancedSettings,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSubtle,
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = dispatcherUrl,
                        onValueChange = { dispatcherUrl = it },
                        label = { Text(strings.labelDispatcherUrl) },
                        placeholder = {
                            Text(
                                strings.placeholderDispatcherUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSubtle.copy(alpha = tokens.alphaDisabled),
                            )
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = tokens.shapeMedium,
                    )
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text(strings.labelBackendUrl) },
                        placeholder = {
                            Text(
                                strings.placeholderBackendUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSubtle.copy(alpha = tokens.alphaDisabled),
                            )
                        },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = tokens.shapeMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(accounts, dispatcherUrl, backendUrl, pin) },
                enabled = canConnect,
                shape = tokens.shapeMedium,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(tokens.iconDefault),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(tokens.spacingSm))
                }
                Text(strings.manualConnectAll)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.buttonCancel) }
        },
    )
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    tokens: uz.yalla.sipphone.ui.theme.AppTokens,
    strings: uz.yalla.sipphone.ui.strings.StringResources,
    subtleColor: Color,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(strings.labelPin) },
        placeholder = {
            Text(
                strings.placeholderPin,
                style = MaterialTheme.typography.bodySmall,
                color = subtleColor.copy(alpha = tokens.alphaDisabled),
            )
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        singleLine = true,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.shapeMedium,
    )
}

@Composable
private fun AccountList(
    accounts: List<ManualAccountEntry>,
    isLoading: Boolean,
    onRemove: (index: Int) -> Unit,
    emptyText: String,
    subtleColor: Color,
) {
    if (accounts.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = subtleColor,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(accounts, key = { _, e -> e.displayKey }) { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.displayKey,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.size(20.dp),
                    enabled = !isLoading,
                ) {
                    Text("×", style = MaterialTheme.typography.bodySmall, color = subtleColor)
                }
            }
        }
    }
}

@Composable
private fun ServerPortRow(
    server: String,
    port: String,
    onServerChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    isLoading: Boolean,
    tokens: uz.yalla.sipphone.ui.theme.AppTokens,
    strings: uz.yalla.sipphone.ui.strings.StringResources,
    subtleColor: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
        OutlinedTextField(
            value = server,
            onValueChange = onServerChange,
            label = { Text(strings.labelServer) },
            placeholder = {
                Text(
                    strings.placeholderServer,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor.copy(alpha = tokens.alphaDisabled),
                )
            },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
            shape = tokens.shapeMedium,
        )
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text(strings.labelPort) },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.width(90.dp),
            shape = tokens.shapeMedium,
        )
    }
}

@Composable
private fun UsernamePasswordRow(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    tokens: uz.yalla.sipphone.ui.theme.AppTokens,
    strings: uz.yalla.sipphone.ui.strings.StringResources,
    subtleColor: Color,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(strings.labelUsername) },
            placeholder = {
                Text(
                    strings.placeholderUsername,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor.copy(alpha = tokens.alphaDisabled),
                )
            },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
            shape = tokens.shapeMedium,
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(strings.labelPassword) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
            shape = tokens.shapeMedium,
        )
    }
}
