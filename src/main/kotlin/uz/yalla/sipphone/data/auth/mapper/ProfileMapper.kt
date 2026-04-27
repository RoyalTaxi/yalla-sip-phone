package uz.yalla.sipphone.data.auth.mapper

import uz.yalla.sipphone.data.auth.remote.response.ProfileResponse
import uz.yalla.sipphone.domain.auth.model.Profile

internal object ProfileMapper {
    fun map(remote: ProfileResponse?): Profile = Profile(
        id = remote?.id?.toString().orEmpty(),
        fullName = remote?.fullName.orEmpty(),
        sipAccounts = remote?.sips
            .orEmpty()
            .filter { it.isActive }
            .map(SipConnectionMapper::map),
        panelUrl = remote?.panelPath?.takeIf { it.isNotBlank() },
    )
}
