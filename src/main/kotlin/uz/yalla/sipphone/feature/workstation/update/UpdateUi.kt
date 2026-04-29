package uz.yalla.sipphone.feature.workstation.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.update.UpdateRelease
import uz.yalla.sipphone.domain.update.UpdateState
import uz.yalla.sipphone.ui.strings.LocalStrings
import uz.yalla.sipphone.ui.strings.StringResources
import uz.yalla.sipphone.ui.theme.LocalAppTokens

@Composable
fun UpdateDialog(
    state: UpdateState,
    callState: CallState,
    dismissed: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is UpdateState.Idle || state is UpdateState.Checking || dismissed) return

    val strings = LocalStrings.current
    val tokens = LocalAppTokens.current
    val release = state.release()
    val callIsIdle = callState is CallState.Idle
    val canInstall = state is UpdateState.ReadyToInstall && callIsIdle

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.updateAvailableDialogTitle + (release?.version?.let { " — v$it" } ?: "")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = tokens.updateDialogMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(tokens.spacingSm),
            ) {
                StatusLine(state, callIsIdle, strings)
                DownloadProgress(state)
                ReleaseNotes(release, strings)
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = canInstall) {
                Text(strings.updateInstallButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.updateLaterButton) }
        },
    )
}

@Composable
private fun StatusLine(state: UpdateState, callIsIdle: Boolean, strings: StringResources) {
    val text = state.statusText(callIsIdle, strings)
    if (text.isEmpty()) return
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DownloadProgress(state: UpdateState) {
    val downloading = state as? UpdateState.Downloading ?: return
    LinearProgressIndicator(
        progress = { downloading.progressFraction() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReleaseNotes(release: UpdateRelease?, strings: StringResources) {
    val tokens = LocalAppTokens.current
    if (release == null || release.releaseNotes.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingXs)) {
        Text(strings.updateReleaseNotesHeader, style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Text(release.releaseNotes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun UpdateDiagnosticsDialog(
    visible: Boolean,
    snapshot: UpdateDiagnosticsSnapshot,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val strings = LocalStrings.current
    val tokens = LocalAppTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.updateDiagnosticsTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = tokens.updateDiagnosticsMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(tokens.spacingSm),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingXs)) {
                    Text("${strings.updateCurrentVersion}: ${snapshot.currentVersion}")
                    Text("${strings.updateDiagnosticsInstallId}: ${snapshot.installId}")
                    Text("${strings.updateDiagnosticsChannel}: ${snapshot.channel}")
                    Text("${strings.updateDiagnosticsState}: ${snapshot.stateText}")
                    Text("${strings.updateDiagnosticsLastCheck}: ${snapshot.lastCheckText}")
                    Text("${strings.updateDiagnosticsLastError}: ${snapshot.lastErrorText}")
                }
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spacingXs)) {
                    Text(strings.updateDiagnosticsLogTail, style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Text(snapshot.logTail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text(strings.updateDiagnosticsCopy) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.updateDiagnosticsClose) } },
    )
}

data class UpdateDiagnosticsSnapshot(
    val installId: String,
    val channel: String,
    val currentVersion: String,
    val stateText: String,
    val lastCheckText: String,
    val lastErrorText: String,
    val logTail: String,
)

private fun UpdateState.release(): UpdateRelease? = when (this) {
    is UpdateState.Downloading -> release
    is UpdateState.Verifying -> release
    is UpdateState.ReadyToInstall -> release
    is UpdateState.Installing -> release
    else -> null
}

private fun UpdateState.statusText(callIsIdle: Boolean, strings: StringResources): String = when (this) {
    is UpdateState.Downloading -> "${strings.updateDownloadingMessage} (${percentOf(bytesRead, total)}%)"
    is UpdateState.Verifying -> strings.updateVerifyingMessage
    is UpdateState.Installing -> strings.updateInstallingMessage
    is UpdateState.Failed -> failureText(this, strings)
    is UpdateState.ReadyToInstall -> if (!callIsIdle) strings.updateWaitingForCallMessage else ""
    else -> ""
}

private fun UpdateState.Downloading.progressFraction(): Float =
    if (total > 0) bytesRead.toFloat() / total else 0f

private fun percentOf(read: Long, total: Long): Int =
    if (total <= 0) 0 else ((read.toDouble() / total) * 100).toInt().coerceIn(0, 100)

private fun failureText(failed: UpdateState.Failed, s: StringResources): String = when (failed.stage) {
    UpdateState.Failed.Stage.VERIFY -> s.updateFailedVerify
    UpdateState.Failed.Stage.DOWNLOAD -> s.updateFailedDownload
    UpdateState.Failed.Stage.INSTALL -> s.updateFailedInstall
    UpdateState.Failed.Stage.CHECK -> s.updateFailedCheck
    UpdateState.Failed.Stage.DISK_FULL -> s.updateFailedDisk
    UpdateState.Failed.Stage.UNTRUSTED_URL -> s.updateFailedUntrustedUrl
    UpdateState.Failed.Stage.MALFORMED_MANIFEST -> s.updateFailedMalformedManifest
}
