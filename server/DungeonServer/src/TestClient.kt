import java.net.Socket

fun main() {
    val socket = Socket("localhost", 9999)
    val input = socket.getInputStream().bufferedReader()
    val output = socket.getOutputStream().bufferedWriter()

    // Read welcome
    val welcome = input.readLine()
    println("Server says: $welcome")

    // Start a thread to read incoming messages (so we don't block)
    Thread {
        try {
            while (true) {
                val message = input.readLine() ?: break
                println("Received: $message")
            }
        } catch (e: Exception) {
            println("Read error: ${e.message}")
        }
    }.start()

    // Simulate a player walking in a straight line
    var x = 100f
    var y = 100f

    for (i in 1..50) {
        x += 10f
        y += 5f
        output.write("MOVE:$x:$y")
        output.newLine()
        output.flush()
        println("Sent position: $x, $y")
        Thread.sleep(500) // Move every half second
    }

    socket.close()
    println("Test player disconnected")
}