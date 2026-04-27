package uz.yalla.sipphone.core.prefs

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class MultiplatformConfigPreferences(
    private val settings: Settings = Settings(),
) : ConfigPreferences {

    private val _values: MutableStateFlow<ConfigPreferencesValues> = MutableStateFlow(loadInitial())
    override val values: StateFlow<ConfigPreferencesValues> = _values.asStateFlow()

    override fun current(): ConfigPreferencesValues = _values.value

    override fun setBackendUrl(url: String) {
        settings.putString(KEY_BACKEND_URL, url)
        _values.update { it.copy(backendUrl = url) }
    }

    override fun setDispatcherUrl(url: String) {
        settings.putString(KEY_DISPATCHER_URL, url)
        _values.update { it.copy(dispatcherUrl = url) }
    }

    override fun setUpdateChannel(channel: String) {
        settings.putString(KEY_UPDATE_CHANNEL, channel)
        _values.update { it.copy(updateChannel = channel) }
    }

    private fun loadInitial(): ConfigPreferencesValues {
        val installId = settings.getStringOrNull(KEY_INSTALL_ID)
            ?: UUID.randomUUID().toString().also { settings.putString(KEY_INSTALL_ID, it) }
        return ConfigPreferencesValues(
            backendUrl = settings.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL),
            dispatcherUrl = settings.getString(KEY_DISPATCHER_URL, DEFAULT_DISPATCHER_URL),
            updateChannel = settings.getString(KEY_UPDATE_CHANNEL, DEFAULT_CHANNEL),
            installId = installId,
        )
    }

    private companion object {
        const val KEY_BACKEND_URL = "config.backend_url"
        const val KEY_DISPATCHER_URL = "config.dispatcher_url"
        const val KEY_UPDATE_CHANNEL = "config.update_channel"
        const val KEY_INSTALL_ID = "config.install_id"

        const val DEFAULT_BACKEND_URL = "https://tma.royaltaxi.uz/api/v1"
        const val DEFAULT_DISPATCHER_URL = "https://tma.dispatch.royaltaxi.uz"
        const val DEFAULT_CHANNEL = "stable"
    }
}
