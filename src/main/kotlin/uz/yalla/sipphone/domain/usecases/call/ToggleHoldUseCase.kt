package uz.yalla.sipphone.domain.usecases.call

import uz.yalla.sipphone.domain.call.CallEngine

class ToggleHoldUseCase(private val callEngine: CallEngine) {
    suspend operator fun invoke(): Result<Unit> = callEngine.toggleHold()
}
