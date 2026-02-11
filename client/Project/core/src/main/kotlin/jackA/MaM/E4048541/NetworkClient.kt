package jackA.MaM.E4048541

import java.net.Socket

class NetworkClient(private val host: String, private val port: Int)
{
    private var socket: Socket? = null
    private var input: java.io.BufferedReader? = null
    private var output: java.io.BufferedWriter? = null

    // Our ID assigned by the server
    var myId: Int = -1

    // Messages that have arrived but haven't been processed yet
    // Think of it as a mailbox — the network thread puts messages in,
    // the game thread takes them out
    private val incomingMessages = mutableListOf<String>()

    fun connect() {
        // Start the network stuff on a separate thread
        Thread {
            try {
                socket = Socket(host, port)
                input = socket!!.getInputStream().bufferedReader()
                output = socket!!.getOutputStream().bufferedWriter()
                println("Connected to server!")

                // Read messages forever in this thread
                while (true) {
                    val message = input!!.readLine() ?: break

                    // If it's the welcome message, grab our ID
                    if (message.startsWith("WELCOME:")) {
                        myId = message.split(":")[1].toInt()
                        println("I am player $myId")
                    }

                    // Put the message in the mailbox for the game thread to process
                    synchronized(incomingMessages) {
                        incomingMessages.add(message)
                    }
                }
            } catch (e: Exception) {
                println("Network error: ${e.message}")
            }
        }.start()
    }

    // Send a message to the server
    fun send(message: String) {
        Thread {
            try {
                println("Attempting to send: $message, output is: $output")
                output?.let {
                    it.write(message)
                    it.newLine()
                    it.flush()
                    println("Sent successfully: $message")
                }
            } catch (e: Exception) {
                println("Send error: ${e.message}")
            }
        }.start()
    }

    // Grab all waiting messages (called by the game thread each frame)
    fun getMessages(): List<String> {
        synchronized(incomingMessages) {
            val copy = incomingMessages.toList()
            incomingMessages.clear()
            return copy
        }
    }
}
