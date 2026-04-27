package uz.yalla.sipphone.domain.update

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Downloading(val release: UpdateRelease, val bytesRead: Long, val total: Long) : UpdateState
    data class Verifying(val release: UpdateRelease) : UpdateState
    data class ReadyToInstall(val release: UpdateRelease, val msiPath: String) : UpdateState
    data class Installing(val release: UpdateRelease) : UpdateState
    data class Failed(val stage: Stage, val reason: String) : UpdateState {
        enum class Stage { CHECK, DOWNLOAD, VERIFY, INSTALL, UNTRUSTED_URL, MALFORMED_MANIFEST, DISK_FULL }
    }
}

enum class UpdateChannel(val value: String) {
    STABLE("stable"),
    BETA("beta");

    companion object {
        fun fromValue(s: String?): UpdateChannel = when (s) {
            BETA.value -> BETA
            else -> STABLE
        }
    }
}
