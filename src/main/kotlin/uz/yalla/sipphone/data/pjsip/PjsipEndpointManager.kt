package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pjsip_transport_type_e
import uz.yalla.sipphone.domain.SipConstants

private val logger = KotlinLogging.logger {}

class PjsipEndpointManager(private val pjDispatcher: CoroutineContext) {

    lateinit var endpoint: Endpoint
        private set

    private val scope = CoroutineScope(SupervisorJob() + pjDispatcher)
    private var pollJob: Job? = null
    private var logWriter: PjsipLogWriter? = null

    fun initEndpoint() {
        endpoint = Endpoint()
        endpoint.libCreate()
        EpConfig().use { cfg ->
            cfg.uaConfig.threadCnt = 0
            cfg.uaConfig.mainThreadOnly = false
            cfg.uaConfig.userAgent = SipConstants.USER_AGENT
            logWriter = PjsipLogWriter()
            cfg.logConfig.writer = logWriter
            cfg.logConfig.level = 3
            cfg.logConfig.consoleLevel = 3
            endpoint.libInit(cfg)
        }
    }

    fun createTransports() {
        TransportConfig().use { cfg ->
            cfg.port = 0
            endpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, cfg)
            endpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, cfg)
        }
    }

    fun startLibrary() {
        endpoint.libStart()
        val version = endpoint.libVersion()
        logger.info { "pjsip initialized, version: ${version.full}" }
        version.delete()
        logAudioDevices()
    }

    fun startPolling() {
        pollJob = scope.launch(pjDispatcher) {
            if (!endpoint.libIsThreadRegistered()) {
                endpoint.libRegisterThread("pjsip-poll")
            }
            while (isActive) {
                endpoint.libHandleEvents(SipConstants.POLL_INTERVAL_MS.toLong())
                yield()
            }
        }
    }

    suspend fun stopPolling() {
        pollJob?.cancel()
        pollJob?.join()
        pollJob = null
    }

    fun getPlaybackDevMedia(): AudioMedia = endpoint.audDevManager().playbackDevMedia

    fun getCaptureDevMedia(): AudioMedia = endpoint.audDevManager().captureDevMedia

    fun destroy() {
        scope.cancel()
        // libDestroy still uses logWriter for shutdown logging — don't delete it before libDestroy.
        runCatching {
            System.gc()
            endpoint.libDestroy()
        }.onFailure { logger.warn(it) { "libDestroy failed (may be partially destroyed)" } }
        runCatching { logWriter?.delete() }
            .onFailure { logger.warn(it) { "logWriter.delete failed" } }
        logWriter = null
        runCatching { endpoint.delete() }
            .onFailure { logger.warn(it) { "endpoint.delete failed (libDestroy may have cleaned it)" } }
    }

    private fun logAudioDevices() {
        val adm = endpoint.audDevManager()
        logger.info { "Audio capture device: ${adm.captureDev}, playback device: ${adm.playbackDev}" }
        adm.enumDev2().use { devices ->
            for (j in 0 until devices.size) {
                val dev = devices[j]
                logger.info { "Audio device[$j]: '${dev.name}' in=${dev.inputCount} out=${dev.outputCount}" }
            }
        }
    }
}
