package uz.yalla.sipphone.sip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class SipTransport {
    private var socket: DatagramSocket? = null
    private var _localAddress: String = "127.0.0.1"

    val localPort: Int get() = socket?.localPort ?: 0
    val localAddress: String get() = _localAddress

    suspend fun open(port: Int = 0, targetHost: String = "8.8.8.8") = withContext(Dispatchers.IO) {
        close()
        socket = DatagramSocket(port)
        _localAddress = resolveLocalAddress(targetHost)
    }

    suspend fun send(message: String, host: String, port: Int) = withContext(Dispatchers.IO) {
        val data = message.toByteArray(Charsets.UTF_8)
        val address = InetAddress.getByName(host)
        val packet = DatagramPacket(data, data.size, address, port)
        socket?.send(packet) ?: error("Socket not opened. Call open() first.")
    }

    suspend fun receive(timeoutMs: Long = 5000): String? = withContext(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        val packet = DatagramPacket(buffer, buffer.size)
        socket?.let { sock ->
            sock.soTimeout = timeoutMs.toInt()
            try {
                sock.receive(packet)
                String(packet.data, 0, packet.length, Charsets.UTF_8)
            } catch (_: SocketTimeoutException) {
                null
            }
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }

    private fun resolveLocalAddress(targetHost: String): String = try {
        DatagramSocket().use { probe ->
            probe.connect(InetAddress.getByName(targetHost), 5060)
            probe.localAddress.hostAddress
        }
    } catch (_: Exception) {
        InetAddress.getLocalHost().hostAddress
    }
}
