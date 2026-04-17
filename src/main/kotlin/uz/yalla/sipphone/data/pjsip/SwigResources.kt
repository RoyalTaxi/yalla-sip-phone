package uz.yalla.sipphone.data.pjsip

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AccountInfo
import org.pjsip.pjsua2.AudioDevInfoVector2
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.StreamInfo
import org.pjsip.pjsua2.TransportConfig

@PublishedApi
internal val resourceLogger = KotlinLogging.logger("pjsip.resources")

inline fun <R> AccountInfo.use(block: (AccountInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> CallInfo.use(block: (CallInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> AccountConfig.use(block: (AccountConfig) -> R): R = try { block(this) } finally { delete() }
inline fun <R> AuthCredInfo.use(block: (AuthCredInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> CallOpParam.use(block: (CallOpParam) -> R): R = try { block(this) } finally { delete() }
inline fun <R> EpConfig.use(block: (EpConfig) -> R): R = try { block(this) } finally { delete() }
inline fun <R> TransportConfig.use(block: (TransportConfig) -> R): R = try { block(this) } finally { delete() }
inline fun <R> StreamInfo.use(block: (StreamInfo) -> R): R = try { block(this) } finally { delete() }
inline fun <R> AudioDevInfoVector2.use(block: (AudioDevInfoVector2) -> R): R = try { block(this) } finally { delete() }

inline fun deleteOnce(flag: AtomicBoolean, tag: String, delete: () -> Unit) {
    if (!flag.compareAndSet(false, true)) return
    runCatching { delete() }.onFailure { e ->
        resourceLogger.warn(e) { "[$tag] delete failed" }
    }
}

// useDefaultCallSetting=true ensures opt.audioCount=1 — without it, reinvite() emits audioCount=0 SDP
// and the peer rejects with 488.
inline fun <R> withCallOpParam(statusCode: Int = 200, block: (CallOpParam) -> R): R {
    val prm = CallOpParam(true).apply { this.statusCode = statusCode }
    return prm.use(block)
}
