package uz.yalla.sipphone.data.pjsip.swig

import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallMediaInfo
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsua_call_media_status

inline fun CallInfo.forEachActiveAudioMedia(action: (index: Int, media: CallMediaInfo) -> Unit) {
    for (i in 0 until media.size) {
        val m = media[i]
        if (m.type != pjmedia_type.PJMEDIA_TYPE_AUDIO) continue
        if (m.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) continue
        action(i, m)
    }
}
