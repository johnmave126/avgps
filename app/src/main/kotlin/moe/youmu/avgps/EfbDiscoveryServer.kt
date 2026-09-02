package moe.youmu.avgps

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

@Serializable
data class EFBDiscoveryMessage(
    @SerialName("App")
    val app: String,
    @SerialName("GDL90")
    val gdl90: PortInfo,
) {
    @Serializable
    data class PortInfo(val port: Int) {
        init {
            require(port in 0..65535)
        }
    }
}

class EfbDiscoveryServer(
    private val port: Int = 63093,
    private val onDiscovery: (host: InetAddress, message: EFBDiscoveryMessage) -> Unit,
) {
    companion object {
        private const val TAG = "EfbDiscoveryServer"
        private const val MAX_PACKET_SIZE = 1500
    }

    private var workerThread: Thread? = null
    private var socket: DatagramSocket? = null

    @Synchronized
    fun start() {
        if (workerThread != null && workerThread!!.isAlive) return

        workerThread = object : Thread("EFB-Discovery-Listener") {
            override fun run() {
                val buffer = ByteArray(MAX_PACKET_SIZE)
                try {
                    val serverSocket = DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(port))
                    }
                    socket = serverSocket

                    while (!isInterrupted) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            serverSocket.receive(packet)
                            val jsonString = String(buffer, 0, packet.length)
                            val message: EFBDiscoveryMessage = Json.decodeFromString(jsonString)
                            onDiscovery(packet.address, message)
                        } catch (_: SerializationException) {
                            // Non-EFB datagram; ignore
                        } catch (error: SocketException) {
                            if (!isInterrupted) Log.e(TAG, "Discovery socket failed", error)
                            break
                        } catch (error: IOException) {
                            Log.w(TAG, "Error reading discovery packet", error)
                        }
                    }
                } catch (error: Exception) {
                    if (!isInterrupted) Log.e(TAG, "Could not bind discovery socket to port $port", error)
                } finally {
                    socket?.close()
                    socket = null
                }
            }
        }.also { it.start() }
    }

    @Synchronized
    fun stop() {
        workerThread?.interrupt()
        socket?.close()
        socket = null
        workerThread = null
    }
}
