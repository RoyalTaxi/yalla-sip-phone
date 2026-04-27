package uz.yalla.sipphone.domain.sip

import kotlinx.coroutines.flow.StateFlow

interface SipAccountManager {
    val accounts: StateFlow<List<SipAccount>>
    suspend fun registerAll(accounts: List<SipAccountInfo>): Result<Unit>
    suspend fun connect(accountId: String): Result<Unit>
    suspend fun disconnect(accountId: String): Result<Unit>
    suspend fun unregisterAll()
}
