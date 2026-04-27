package uz.yalla.sipphone.core.prefs

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MultiplatformSessionPreferences(
    private val settings: Settings = Settings(),
) : SessionPreferences {

    private val _accessToken: MutableStateFlow<String?> =
        MutableStateFlow(settings.getStringOrNull(KEY_TOKEN))

    override val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    override fun setAccessToken(token: String?) {
        if (token.isNullOrBlank()) {
            settings.remove(KEY_TOKEN)
            _accessToken.value = null
        } else {
            settings.putString(KEY_TOKEN, token)
            _accessToken.value = token
        }
    }

    override fun clear() {
        settings.remove(KEY_TOKEN)
        _accessToken.value = null
    }

    private companion object {
        const val KEY_TOKEN = "session.access_token"
    }
}
