package uz.yalla.sipphone.core.prefs

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MultiplatformUserPreferences(
    private val settings: Settings = Settings(),
) : UserPreferences {

    private val _values: MutableStateFlow<UserPreferencesValues> = MutableStateFlow(loadInitial())
    override val values: StateFlow<UserPreferencesValues> = _values.asStateFlow()

    override fun current(): UserPreferencesValues = _values.value

    override fun setLocale(locale: String) {
        settings.putString(KEY_LOCALE, locale)
        _values.update { it.copy(locale = locale) }
    }

    override fun setDarkTheme(isDark: Boolean) {
        settings.putBoolean(KEY_DARK, isDark)
        _values.update { it.copy(isDarkTheme = isDark) }
    }

    private fun loadInitial() = UserPreferencesValues(
        locale = settings.getString(KEY_LOCALE, DEFAULT_LOCALE),
        isDarkTheme = settings.getBoolean(KEY_DARK, DEFAULT_DARK),
    )

    private companion object {
        const val KEY_LOCALE = "user.locale"
        const val KEY_DARK = "user.dark_theme"
        const val DEFAULT_LOCALE = "uz"
        const val DEFAULT_DARK = true
    }
}
