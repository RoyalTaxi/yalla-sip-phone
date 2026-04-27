package uz.yalla.sipphone.domain.usecases.sip

import java.util.concurrent.atomic.AtomicBoolean
import uz.yalla.sipphone.domain.sip.SipAccountManager

class DisconnectAllAccountsUseCase(private val sipAccountManager: SipAccountManager) {
    private val inFlight = AtomicBoolean(false)

    suspend operator fun invoke(): Result<Unit> {
        if (!inFlight.compareAndSet(false, true)) return Result.success(Unit)
        return try {
            sipAccountManager.unregisterAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inFlight.set(false)
        }
    }
}
