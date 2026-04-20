package uz.yalla.sipphone.data.settings

import com.russhwolf.settings.Settings
import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipCredentials

class AppSettings {

    private val settings = Settings()
    // Guards compound reads/writes (credentials, installId lazy-gen) so a concurrent
    // saveCredentials can't leave sip_port out of sync with sip_server.
    private val credentialsLock = Any()

    var locale: String
        get() = settings.getString("locale", "uz")
        set(value) = settings.putString("locale", value)

    var isDarkTheme: Boolean
        get() = settings.getBoolean("dark_theme", true) // dark by default
        set(value) = settings.putBoolean("dark_theme", value)

    fun saveCredentials(credentials: SipCredentials) = synchronized(credentialsLock) {
        settings.putString("sip_server", credentials.server)
        settings.putInt("sip_port", credentials.port)
        settings.putString("sip_username", credentials.username)
    }

    fun loadCredentials(): SipCredentials? = synchronized(credentialsLock) {
        val server = settings.getStringOrNull("sip_server") ?: return@synchronized null
        val username = settings.getStringOrNull("sip_username") ?: return@synchronized null
        SipCredentials(
            server = server,
            port = settings.getInt("sip_port", SipConstants.DEFAULT_PORT),
            username = username,
            password = "",
        )
    }

    var backendUrl: String
        get() = settings.getString("backend_url", "https://tma.royaltaxi.uz/api/v1")
        set(value) = settings.putString("backend_url", value)

    var dispatcherUrl: String
        get() = settings.getString("dispatcher_url", "https://tma.dispatch.royaltaxi.uz")
        set(value) = settings.putString("dispatcher_url", value)

    var updateChannel: String
        get() = settings.getString("update_channel", "stable")
        set(value) = settings.putString("update_channel", value)

    /** Stable anonymous per-machine UUID, lazily generated on first access (spec Q6). */
    val installId: String
        get() = synchronized(credentialsLock) {
            // Check-then-act without locking would let two concurrent first-access callers
            // each generate a different UUID and overwrite each other.
            val existing = settings.getStringOrNull("install_id")
            if (existing != null) return@synchronized existing
            val fresh = java.util.UUID.randomUUID().toString()
            settings.putString("install_id", fresh)
            fresh
        }
}
