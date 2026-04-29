package uz.yalla.sipphone.feature.auth.presentation.view.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import uz.yalla.sipphone.feature.auth.presentation.intent.ManualAccountEntry
import uz.yalla.sipphone.ui.component.PasswordTextField
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.theme.LocalAppTokens
import uz.yalla.sipphone.ui.theme.LocalYallaColors

private const val DEFAULT_SIP_PORT = 5060
private const val MAX_PORT_DIGITS = 5

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
    val strings = LocalStrings.current
    val tokens = LocalAppTokens.current

    val form = rememberManualConnectionForm()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.loginManualConnection) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(tokens.spacingXs),
            ) {
                PasswordTextField(
                    value = form.pin,
                    onValueChange = { form.pin = it },
                    label = strings.labelPin,
                    placeholder = strings.placeholderPin,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(tokens.spacingXs))

                AccountList(
                    accounts = form.accounts,
                    isLoading = isLoading,
                    onRemove = form::removeAt,
                )

                ServerPortRow(
                    server = form.server,
                    port = form.port,
                    onServerChange = form::updateServer,
                    onPortChange = form::updatePort,
                    isLoading = isLoading,
                )

                UsernamePasswordRow(
                    username = form.username,
                    password = form.password,
                    onUsernameChange = form::updateUsername,
                    onPasswordChange = { form.password = it },
                    isLoading = isLoading,
                )

                if (form.duplicateWarning) {
                    Text(
                        strings.manualDuplicateAccount,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalYallaColors.current.statusWarning,
                    )
                }

                TextButton(
                    onClick = form::addAccount,
                    enabled = form.canAdd && !isLoading,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(strings.manualAddAccount) }

                AdvancedSettings(
                    expanded = form.showAdvanced,
                    onToggle = { form.showAdvanced = !form.showAdvanced },
                    dispatcherUrl = form.dispatcherUrl,
                    backendUrl = form.backendUrl,
                    onDispatcherUrlChange = { form.dispatcherUrl = it },
                    onBackendUrlChange = { form.backendUrl = it },
                    isLoading = isLoading,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(form.accounts, form.dispatcherUrl, form.backendUrl, form.pin) },
                enabled = form.canConnect && !isLoading,
                shape = tokens.shapeMedium,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(tokens.iconDefault),
                        strokeWidth = tokens.progressStrokeSmall,
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
private fun AccountList(
    accounts: List<ManualAccountEntry>,
    isLoading: Boolean,
    onRemove: (Int) -> Unit,
) {
    val tokens = LocalAppTokens.current
    val colors = LocalYallaColors.current
    val strings = LocalStrings.current

    if (accounts.isEmpty()) {
        Text(
            strings.manualNoAccounts,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSubtle,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = tokens.loginManualAccountListMaxHeight),
        verticalArrangement = Arrangement.spacedBy(tokens.spacingXs / 2),
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
                    modifier = Modifier.size(tokens.loginManualRemoveIconSize),
                    enabled = !isLoading,
                ) {
                    Text("×", style = MaterialTheme.typography.bodySmall, color = colors.textSubtle)
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
) {
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current

    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
        OutlinedTextField(
            value = server,
            onValueChange = onServerChange,
            label = { Text(strings.labelServer) },
            placeholder = { SubtlePlaceholder(strings.placeholderServer) },
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
            modifier = Modifier.width(tokens.loginManualPortFieldWidth),
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
) {
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current

    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(strings.labelUsername) },
            placeholder = { SubtlePlaceholder(strings.placeholderUsername) },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
            shape = tokens.shapeMedium,
        )
        PasswordTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = strings.labelPassword,
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
            shape = tokens.shapeMedium,
        )
    }
}

@Composable
private fun AdvancedSettings(
    expanded: Boolean,
    onToggle: () -> Unit,
    dispatcherUrl: String,
    backendUrl: String,
    onDispatcherUrlChange: (String) -> Unit,
    onBackendUrlChange: (String) -> Unit,
    isLoading: Boolean,
) {
    val tokens = LocalAppTokens.current
    val strings = LocalStrings.current
    val colors = LocalYallaColors.current

    TextButton(onClick = onToggle) {
        Text(
            strings.manualAdvancedSettings,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSubtle,
        )
    }
    if (!expanded) return

    OutlinedTextField(
        value = dispatcherUrl,
        onValueChange = onDispatcherUrlChange,
        label = { Text(strings.labelDispatcherUrl) },
        placeholder = { SubtlePlaceholder(strings.placeholderDispatcherUrl) },
        singleLine = true,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.shapeMedium,
    )
    OutlinedTextField(
        value = backendUrl,
        onValueChange = onBackendUrlChange,
        label = { Text(strings.labelBackendUrl) },
        placeholder = { SubtlePlaceholder(strings.placeholderBackendUrl) },
        singleLine = true,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.shapeMedium,
    )
}

@Composable
private fun SubtlePlaceholder(text: String) {
    val colors = LocalYallaColors.current
    val tokens = LocalAppTokens.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSubtle.copy(alpha = tokens.alphaDisabled),
    )
}

private class ManualConnectionFormState {
    var pin by mutableStateOf("")
    var server by mutableStateOf("")
    var port by mutableStateOf(DEFAULT_SIP_PORT.toString())
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var dispatcherUrl by mutableStateOf("")
    var backendUrl by mutableStateOf("")
    var showAdvanced by mutableStateOf(false)
    var duplicateWarning by mutableStateOf(false)
    var accounts by mutableStateOf(emptyList<ManualAccountEntry>())
        private set

    val canAdd: Boolean get() = server.isNotBlank() && username.isNotBlank()
    val canConnect: Boolean get() = accounts.isNotEmpty()

    fun updateServer(value: String) {
        server = value
        duplicateWarning = false
    }

    fun updateUsername(value: String) {
        username = value
        duplicateWarning = false
    }

    fun updatePort(value: String) {
        port = value.filter(Char::isDigit).take(MAX_PORT_DIGITS)
    }

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

    fun removeAt(index: Int) {
        accounts = accounts.filterIndexed { i, _ -> i != index }
        duplicateWarning = false
    }
}

@Composable
private fun rememberManualConnectionForm(): ManualConnectionFormState =
    remember { ManualConnectionFormState() }
