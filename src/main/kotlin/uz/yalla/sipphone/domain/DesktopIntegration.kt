package uz.yalla.sipphone.domain

interface DesktopIntegration {
    fun showNotification(title: String, message: String)
    fun setTrayIcon(state: TrayState)
    fun registerGlobalHotkey(key: String, action: () -> Unit)
    fun setAlwaysOnTop(enabled: Boolean)
}

enum class TrayState { IDLE, REGISTERED, IN_CALL, INCOMING_CALL }
