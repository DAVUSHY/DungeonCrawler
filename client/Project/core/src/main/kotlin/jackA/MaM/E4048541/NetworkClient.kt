package jackA.MaM.E4048541

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer

class NetworkClient(var host: String, private val port: Int) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    var myId: Short = -1

    // Messages that have arrived but haven't been processed yet
    // Its kind of like a letter box the network thread puts messages in,
    // the game thread takes them out (recipient)
    private val incomingMessages = mutableListOf<GameMessage>()

    fun connect() {
        Thread {
            try {
                socket = Socket(host, port)
                input = DataInputStream(socket!!.getInputStream())
                output = DataOutputStream(socket!!.getOutputStream())
                println("Connected to server!")

                while (true) {
                    // Read the 2-byte length prefix
                    val lengthBytes = ByteArray(2)
                    input!!.readFully(lengthBytes)
                    val length = ByteBuffer.wrap(lengthBytes).short.toInt()

                    // Read exactly that many bytes
                    val payload = ByteArray(length)
                    input!!.readFully(payload)

                    // Parse into a GameMessage
                    val message = GameMessage.fromBytes(ByteBuffer.wrap(payload))

                    // If it's the welcome message, grab our ID
                    if (message is GameMessage.Welcome) {
                        myId = message.playerID
                        println("I am player $myId")
                    }

                    synchronized(incomingMessages) {
                        incomingMessages.add(message)
                    }
                }
            } catch (e: Exception) {
                println("Network error: ${e.message}")
            }
        }.start()
    }

    // Send raw bytes to the server
    fun send(bytes: ByteArray) {
        Thread {
            try {
                output?.let {
                    synchronized(it) {
                        it.write(bytes)
                        it.flush()
                    }
                }
            } catch (e: Exception) {
                println("Send error: ${e.message}")
            }
        }.start()
    }

    fun getMessages(): List<GameMessage> {
        synchronized(incomingMessages) {
            val copy = incomingMessages.toList()
            incomingMessages.clear()
            return copy
        }
    }
}
