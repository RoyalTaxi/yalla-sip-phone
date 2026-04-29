package uz.yalla.sipphone.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uz.yalla.sipphone.domain.sip.SipAccount
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipAccountManager
import uz.yalla.sipphone.domain.sip.SipAccountState

class FakeSipAccountManager : SipAccountManager {

    private val _accounts = MutableStateFlow<List<SipAccount>>(emptyList())
    override val accounts: StateFlow<List<SipAccount>> = _accounts.asStateFlow()

    var registerAllResult: Result<Unit> = Result.success(Unit)
    var connectResult: Result<Unit> = Result.success(Unit)
    var disconnectResult: Result<Unit> = Result.success(Unit)

    /**
     * When true (default), [registerAll] immediately publishes all accounts as
     * [SipAccountState.Connected]. Set to false to simulate registration timeout — the
     * accounts publish as [SipAccountState.Disconnected] and never advance, so any
     * `accounts.first { it is Connected }` consumer hits its own timeout.
     */
    var autoConnectOnRegister: Boolean = true

    var registerAllCallCount = 0; private set
    var unregisterAllCallCount = 0; private set
    var lastRegisteredAccounts: List<SipAccountInfo> = emptyList(); private set

    override suspend fun registerAll(accounts: List<SipAccountInfo>): Result<Unit> {
        registerAllCallCount++
        lastRegisteredAccounts = accounts
        return registerAllResult.onSuccess {
            val state = if (autoConnectOnRegister) {
                SipAccountState.Connected
            } else {
                SipAccountState.Disconnected
            }
            _accounts.value = accounts.map { info ->
                SipAccount(info.id, info.name, info.credentials, state)
            }
        }
    }

    override suspend fun connect(accountId: String): Result<Unit> =
        connectResult.onSuccess { updateAccountState(accountId, SipAccountState.Connected) }

    override suspend fun disconnect(accountId: String): Result<Unit> =
        disconnectResult.onSuccess { updateAccountState(accountId, SipAccountState.Disconnected) }

    override suspend fun unregisterAll() {
        unregisterAllCallCount++
        _accounts.value = emptyList()
    }

    fun simulateAccountState(accountId: String, state: SipAccountState) {
        updateAccountState(accountId, state)
    }

    fun seedAccounts(accounts: List<SipAccount>) {
        _accounts.value = accounts
    }

    private fun updateAccountState(accountId: String, state: SipAccountState) {
        _accounts.update { list ->
            list.map { if (it.id == accountId) it.copy(state = state) else it }
        }
    }
}
