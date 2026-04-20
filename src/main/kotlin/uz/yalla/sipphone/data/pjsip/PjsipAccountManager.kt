package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

import uz.yalla.sipphone.domain.SipConstants
import uz.yalla.sipphone.domain.SipCredentials
import uz.yalla.sipphone.domain.SipError

private val logger = KotlinLogging.logger {}

interface AccountProvider {
    fun getAccount(accountId: String): PjsipAccount?
    fun getFirstConnectedAccount(): PjsipAccount?
}

class PjsipAccountManager(
    private val isDestroyed: () -> Boolean,
) : AccountProvider {

    private val _accountStates = ConcurrentHashMap<String, MutableStateFlow<PjsipRegistrationState>>()
    private val accounts: MutableMap<String, PjsipAccount> = ConcurrentHashMap()
    private val rateLimiter = RegisterRateLimiter()

    // Per-account mutex serializing register + unregister so they can't race.
    private val accountLocks = ConcurrentHashMap<String, Mutex>()

    var incomingCallHandler: ((accountId: String, callId: Int) -> Unit)? = null

    private val _registrationEvents = MutableSharedFlow<Pair<String, PjsipRegistrationState>>(
        extraBufferCapacity = 64,
    )
    val registrationEvents: SharedFlow<Pair<String, PjsipRegistrationState>> =
        _registrationEvents.asSharedFlow()

    fun updateRegistrationState(accountId: String, state: PjsipRegistrationState) {
        stateFlowFor(accountId).value = state
        if (!_registrationEvents.tryEmit(accountId to state)) {
            logger.warn { "[$accountId] registration event buffer full — state=$state lost" }
        }
    }

    private fun lockFor(accountId: String): Mutex =
        accountLocks.getOrPut(accountId) { Mutex() }

    fun isAccountDestroyed(): Boolean = isDestroyed()

    fun handleIncomingCall(accountId: String, callId: Int) {
        incomingCallHandler?.invoke(accountId, callId)
    }

    override fun getAccount(accountId: String): PjsipAccount? = accounts[accountId]

    override fun getFirstConnectedAccount(): PjsipAccount? =
        accounts.entries.firstOrNull { (id, _) ->
            _accountStates[id]?.value is PjsipRegistrationState.Registered
        }?.value

    suspend fun register(accountId: String, credentials: SipCredentials): Result<Unit> =
        lockFor(accountId).withLock {
            val stateFlow = stateFlowFor(accountId)

            if (stateFlow.value is PjsipRegistrationState.Registering) {
                return@withLock Result.failure(
                    IllegalStateException("Registration already in progress for $accountId"),
                )
            }

            rateLimiter.awaitSlot(accountId)

            val wasRegistered = stateFlow.value is PjsipRegistrationState.Registered
            stateFlow.value = PjsipRegistrationState.Registering

            accounts[accountId]?.let { prev ->
                teardownPrevious(accountId, prev, stateFlow, wasRegistered)
                // Re-assert Registering since teardown may have observed Idle.
                stateFlow.value = PjsipRegistrationState.Registering
            }

            AccountConfigBuilder.build(credentials).use { config ->
                runCatching {
                    val account = PjsipAccount(accountId, credentials.server, this).apply {
                        create(config, true)
                    }
                    accounts[accountId] = account
                    logger.info { "[$accountId] Account created, awaiting registration callback" }
                }.onFailure { e ->
                    logger.error(e) { "[$accountId] Registration failed" }
                    stateFlow.value = PjsipRegistrationState.Failed(SipError.fromException(e))
                }
            }
        }

    suspend fun unregister(accountId: String) = lockFor(accountId).withLock {
        val acc = accounts[accountId] ?: return@withLock
        val stateFlow = _accountStates[accountId] ?: return@withLock
        // Only send an UNREGISTER to pjsip if the account is actually registered. Calling
        // setRegistration(false) on an account that never completed registration (e.g. a 403
        // Forbidden previously) throws PJ_EINVALIDOP from native — harmless but noisy.
        if (stateFlow.value is PjsipRegistrationState.Registered) {
            runCatching {
                acc.setRegistration(false)
                withTimeoutOrNull(SipConstants.Timeout.UNREGISTER_MS) {
                    stateFlow.first { it is PjsipRegistrationState.Idle }
                }
            }.onFailure { logger.warn(it) { "[$accountId] Unregister error" } }
        }
        acc.safeDelete()
        accounts.remove(accountId)
        stateFlow.value = PjsipRegistrationState.Idle
    }

    suspend fun unregisterAll() {
        accounts.keys.toList().forEach { unregister(it) }
    }

    suspend fun destroy() {
        // Only fire setRegistration(false) for accounts that are actually registered — same
        // reason as unregister() above (avoids PJ_EINVALIDOP noise on teardown).
        accounts.entries.forEach { (id, acc) ->
            if (_accountStates[id]?.value is PjsipRegistrationState.Registered) {
                runCatching { acc.setRegistration(false) }
                    .onFailure { logger.warn(it) { "setRegistration(false) failed during destroy" } }
            }
        }
        // Wait for PJSIP to report Idle on every account, bounded by DESTROY_MS.
        withTimeoutOrNull(SipConstants.Timeout.DESTROY_MS) {
            coroutineScope {
                _accountStates.values
                    .map { flow -> async { flow.first { it is PjsipRegistrationState.Idle } } }
                    .awaitAll()
            }
        } ?: logger.warn { "destroy: timed out waiting for Idle" }
        accounts.values.forEach { it.safeDelete() }
        accounts.clear()
        _accountStates.values.forEach { it.value = PjsipRegistrationState.Idle }
        _accountStates.clear()
        accountLocks.clear()
        rateLimiter.clear()
    }

    private fun stateFlowFor(accountId: String): MutableStateFlow<PjsipRegistrationState> =
        _accountStates.getOrPut(accountId) { MutableStateFlow(PjsipRegistrationState.Idle) }

    private suspend fun teardownPrevious(
        accountId: String,
        prev: PjsipAccount,
        stateFlow: MutableStateFlow<PjsipRegistrationState>,
        wasRegistered: Boolean,
    ) {
        runCatching { prev.setRegistration(false) }
            .onFailure { logger.warn(it) { "[$accountId] setRegistration(false) threw — continuing teardown" } }
        if (wasRegistered) {
            withTimeoutOrNull(SipConstants.Timeout.UNREGISTER_BEFORE_REREGISTER_MS) {
                stateFlow.first { it is PjsipRegistrationState.Idle }
            }
        }
        prev.safeDelete()
        accounts.remove(accountId)
    }

}
