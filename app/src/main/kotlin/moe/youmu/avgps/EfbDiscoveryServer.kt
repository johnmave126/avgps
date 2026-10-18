package moe.youmu.avgps

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

data class EFBDiscoveryMessage(
    val app: String,
    val gdl90Port: Int,
    val version: String? = null,
    val deviceName: String? = null,
) {
    init {
        require(gdl90Port in 1..65535) { "Invalid GDL90 port: $gdl90Port" }
    }
}

class EfbDiscoveryServer(
    private val listenPort: Int = 63093,
    private val onDiscovery: (host: InetAddress, message: EFBDiscoveryMessage) -> Unit,
) {
    companion object {
        private const val TAG = "EfbDiscoveryServer"
        private const val MAX_PACKET_SIZE = 1500

        internal fun decodeMessage(raw: String): EFBDiscoveryMessage {
            val root = JSONObject(raw)
            val gdl90 = root.getJSONObject("GDL90")
            val port = gdl90.getInt("port")
            val app = root.optNonBlankString("App") ?: "EFB"
            val version = root.optNonBlankString("Version")
            val deviceName = root.optNonBlankString("DeviceName")
            return EFBDiscoveryMessage(
                app = app,
                gdl90Port = port,
                version = version,
                deviceName = deviceName,
            )
        }

        private fun JSONObject.optNonBlankString(key: String): String? =
            if (isNull(key)) null else optString(key, "").trim().ifEmpty { null }
    }

    private var workerThread: Thread? = null
    private var socket: DatagramSocket? = null

    @Synchronized
    fun start() {
        if (workerThread != null && workerThread!!.isAlive) return

        workerThread = object : Thread("EFB-Discovery-Listener") {
            override fun run() {
                val buffer = ByteArray(MAX_PACKET_SIZE)
                var serverSocket: DatagramSocket? = null
                try {
                    serverSocket = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(listenPort))
                    }
                    socket = serverSocket

                    while (!isInterrupted) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            serverSocket.receive(packet)
                            val jsonString = String(buffer, 0, packet.length, Charsets.UTF_8)
                            val message = decodeMessage(jsonString)
                            onDiscovery(packet.address, message)
                        } catch (error: JSONException) {
                            Log.d(TAG, "Ignoring non-EFB datagram", error)
                        } catch (error: IllegalArgumentException) {
                            Log.d(TAG, "Ignoring datagram with invalid port", error)
                        } catch (error: SocketException) {
                            if (!isInterrupted) Log.e(TAG, "Discovery socket failed", error)
                            break
                        } catch (error: IOException) {
                            Log.w(TAG, "Error reading discovery packet", error)
                        }
                    }
                } catch (error: Exception) {
                    if (!isInterrupted) Log.e(TAG, "Could not bind discovery socket to port $listenPort", error)
                } finally {
                    serverSocket?.close()
                    synchronized(this@EfbDiscoveryServer) {
                        if (socket === serverSocket) {
                            socket = null
                        }
                    }
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
