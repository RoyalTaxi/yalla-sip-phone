package uz.yalla.sipphone.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.yalla.sipphone.domain.auth.model.Session

class SessionStore {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun set(session: Session) {
        _session.value = session
    }

    fun clear() {
        _session.value = null
    }
}
