package uz.yalla.sipphone.core.prefs

import kotlinx.coroutines.flow.StateFlow

data class ConfigPreferencesValues(
    val backendUrl: String,
    val dispatcherUrl: String,
    val updateChannel: String,
    val installId: String,
)

interface ConfigPreferences {
    val values: StateFlow<ConfigPreferencesValues>
    fun current(): ConfigPreferencesValues
    fun setBackendUrl(url: String)
    fun setDispatcherUrl(url: String)
    fun setUpdateChannel(channel: String)
}
