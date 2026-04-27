package uz.yalla.sipphone.domain.usecases.call

import uz.yalla.sipphone.domain.call.CallEngine

class ToggleMuteUseCase(private val callEngine: CallEngine) {
    suspend operator fun invoke(): Result<Unit> = callEngine.toggleMute()
}
