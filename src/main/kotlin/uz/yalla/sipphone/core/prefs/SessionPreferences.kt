package uz.yalla.sipphone.core.prefs

import kotlinx.coroutines.flow.StateFlow

interface SessionPreferences {
    val accessToken: StateFlow<String?>
    fun setAccessToken(token: String?)
    fun clear()
}
