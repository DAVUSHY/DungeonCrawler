import java.net.ServerSocket
import java.net.Socket

// --- GAME STATE ---
// This is what the server "knows" about the world

data class Player(
    val id: Int,
    val socket: Socket,
    var x: Float = 0f,
    var y: Float = 0f,
    var health: Int = 100
)

// All connected players
val players = mutableMapOf<Int, Player>()
var nextPlayerId = 1

// --- MESSAGE HANDLING ---
// This is the "brain" — it decides what to do with each message

fun handleMessage(player: Player, message: String) {
    // Split "MOVE:5.0:3.0" into ["MOVE", "5.0", "3.0"]
    val parts = message.split(":")
    // Splits message wherever there is a :
    // Leaves you with:
    //parts[0] = "MOVE"
    //parts[1] = "5.0"
    //parts[2] = "3.0"

    when (parts[0]) {
        "MOVE" -> {
            val x = parts[1].toFloat()
            val y = parts[2].toFloat()

            // Update this player's position on the server
            player.x = x
            player.y = y

            // Tell everyone where this player moved
            println("Player ${player.id} moved to $x, $y")
            broadcast("MOVE:${player.id}:$x:$y")
        }

        "ATTACK" -> {
            val targetId = parts[1].toInt()
            val target = players[targetId]

            if (target != null) {
                // Simple damage calculation
                target.health -= 10
                broadcast("DAMAGE:${target.id}:${target.health}")

                if (target.health <= 0) {
                    broadcast("DIED:${target.id}")
                }
            }
        }

        // You just keep adding more commands here as your game grows
        // "INTERACT", "PICKUP", "USE_ITEM", "CHAT", whatever you need

        else -> {
            println("Unknown message from player ${player.id}: $message")
        }
    }
}

// --- NETWORKING (same as before, barely changed) ---

fun handlePlayer(player: Player) {
    val input = player.socket.getInputStream().bufferedReader()
    val output = player.socket.getOutputStream().bufferedWriter()

    // Tell this player their ID
    output.write("WELCOME:${player.id}")
    output.newLine()
    output.flush()

    // Also tell them about every player already in the game
    for ((_, other) in players) {
        if (other.id != player.id) {
            output.write("PLAYER_JOINED:${other.id}:${other.x}:${other.y}:${other.health}")
            output.newLine()
            output.flush()
        }
    }

    // Tell everyone else that this player joined
    broadcast("PLAYER_JOINED:${player.id}:${player.x}:${player.y}:${player.health}")

    try {
        while (true) {
            val message = input.readLine() ?: break
            handleMessage(player, message)
        }
    } catch (e: Exception) {
        println("Player ${player.id} error: ${e.message}")
    }

    println("Player ${player.id} disconnected")
    players.remove(player.id)
    player.socket.close()
    broadcast("PLAYER_LEFT:${player.id}")
}

fun broadcast(message: String) {
    for ((_, player) in players) {
        try {
            val output = player.socket.getOutputStream().bufferedWriter()
            output.write(message)
            output.newLine()
            output.flush()
        } catch (e: Exception) {
            println("Failed to send to player ${player.id}")
        }
    }
}

fun main() {
    val serverSocket = ServerSocket(9999)
    println("Dungeon server started on port 9999")

    while (true) {
        val socket = serverSocket.accept()
        val id = nextPlayerId++
        val player = Player(id, socket)
        players[id] = player
        println("Player $id connected")

        Thread { handlePlayer(player) }.start()
    }
}