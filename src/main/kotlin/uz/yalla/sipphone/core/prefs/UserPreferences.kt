package uz.yalla.sipphone.core.prefs

import kotlinx.coroutines.flow.StateFlow

data class UserPreferencesValues(
    val locale: String,
    val isDarkTheme: Boolean,
)

interface UserPreferences {
    val values: StateFlow<UserPreferencesValues>
    fun current(): UserPreferencesValues
    fun setLocale(locale: String)
    fun setDarkTheme(isDark: Boolean)
}
