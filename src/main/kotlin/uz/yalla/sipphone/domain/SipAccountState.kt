package uz.yalla.sipphone.domain

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SipAccountState {
    data object Connected : SipAccountState
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : SipAccountState
    data object Disconnected : SipAccountState
}
