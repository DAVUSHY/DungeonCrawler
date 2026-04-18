package jackA.MaM.E4048541

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpClient(private val host: String, private val udpPort: Int = 9998) {

    private val socket = DatagramSocket()
    private val incomingMessages = mutableListOf<String>()

    fun sendChat(message: String) {
        Thread {
            try {
                val data = "CHAT:$message".toByteArray()
                val packet = DatagramPacket(data, data.size, InetAddress.getByName(host), udpPort)
                socket.send(packet)
            } catch (e: Exception) {
                println("UDP send error: ${e.message}")
            }
        }.start()
    }

    fun discoverServer(): String? {
        return try {
            socket.broadcast = true
            val data = "DISCOVER".toByteArray()
            // 255.255.255.255 is the general broadcast address
            val packet = DatagramPacket(
                data, data.size,
                InetAddress.getByName("255.255.255.255"),
                udpPort
            )
            socket.send(packet)
            println("Discovery broadcast sent...")

            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.soTimeout = 3000
            socket.receive(responsePacket)

            val response = String(responsePacket.data, 0, responsePacket.length)
            println("Discovery response: $response")

            // Response is "SERVER_HERE:9999" — we just want the IP
            responsePacket.address.hostAddress
        } catch (e: Exception) {
            println("Discovery failed: ${e.message}")
            null
        }
    }
}

