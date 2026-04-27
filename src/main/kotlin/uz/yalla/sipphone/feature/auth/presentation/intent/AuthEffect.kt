package uz.yalla.sipphone.feature.auth.presentation.intent

import uz.yalla.sipphone.domain.auth.model.Session

sealed interface AuthEffect {
    data class LoggedIn(val session: Session) : AuthEffect
}
