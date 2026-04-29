package uz.yalla.sipphone.feature.workstation.presentation.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.sip.SipAccountState

private val logger = KotlinLogging.logger {}

internal fun WorkstationComponent.dispatchCall(number: String): Job = scope.launch {
    if (number.isBlank()) return@launch
    callEngine.makeCall(number).onFailure { logger.warn(it) { "makeCall failed" } }
}

internal fun WorkstationComponent.answer(): Job = scope.launch {
    callEngine.answerCall()
}

internal fun WorkstationComponent.hangup(): Job = scope.launch {
    callEngine.hangupCall()
}

internal fun WorkstationComponent.mute(): Job = scope.launch {
    callEngine.toggleMute()
}

internal fun WorkstationComponent.hold(): Job = scope.launch {
    callEngine.toggleHold()
}

internal fun WorkstationComponent.sipChipClick(accountId: String): Job = scope.launch {
    val account = sipAccountManager.accounts.value.firstOrNull { it.id == accountId } ?: return@launch
    if (callEngine.callState.value !is CallState.Idle) return@launch
    if (account.state is SipAccountState.Connected) {
        sipAccountManager.disconnect(accountId)
    } else {
        sipAccountManager.connect(accountId)
    }
}
