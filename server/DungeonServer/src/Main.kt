import java.net.ServerSocket
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

// --- GAME STATE ---
// what the server "knows" about the world

data class Player(
    val id: Short,
    val socket: Socket,
    val input: DataInputStream,
    val output: DataOutputStream,
    var x: Float = 0f,
    var y: Float = 0f,
    var health: Int = 100
)

// All connected players
val players = mutableMapOf<Short, Player>()
var nextPlayerId: Short = 1

// --- MESSAGE HANDLING ---
// This is the "brain", deciding what to do with each message

fun handleMessage(player: Player, message: GameMessage) {
    when (message) {
        is GameMessage.Move -> {
            player.x = message.x
            player.y = message.y
            println("Player ${player.id} moved to ${message.x}, ${message.y}")

            // Broadcast to everyone with this player's ID attached
            val broadcastMsg = GameMessage.Move(player.id, message.x, message.y)
            broadcast(broadcastMsg.toBytes())
        }

        is GameMessage.AttackMelee -> {
            println("Melee attack from player ${player.id}")
            // TODO: check nearby enemies, calculate damage, broadcast results
        }

        // The server shouldn't receive these — it sends them
        is GameMessage.Welcome -> {}
        is GameMessage.PlayerJoined -> {}
        is GameMessage.PlayerLeft -> {}
    }
}

// --- NETWORKING (same as before, barely changed) ---

fun handlePlayer(player: Player) {
    // Send welcome with their assigned ID
    val welcomeMsg = GameMessage.Welcome(player.id)
    sendToPlayer(player, welcomeMsg.toBytes())

    // Tell this player about everyone already in the game
    for ((_, other) in players) {
        if (other.id != player.id) {
            val joinedMsg = GameMessage.PlayerJoined(other.id, other.x, other.y)
            sendToPlayer(player, joinedMsg.toBytes())
        }
    }

    // Tell everyone else this player joined
    val newPlayerMsg = GameMessage.PlayerJoined(player.id, player.x, player.y)
    broadcast(newPlayerMsg.toBytes())

    // Main read loop
    try {
        while (true) {
            // Read the 2-byte length prefix
            val lengthBytes = ByteArray(2)
            player.input.readFully(lengthBytes)
            val length = ByteBuffer.wrap(lengthBytes).short.toInt()

            // Read exactly that many bytes for the payload
            val payload = ByteArray(length)
            player.input.readFully(payload)

            // Parse the message
            val message = GameMessage.fromBytes(ByteBuffer.wrap(payload))
            handleMessage(player, message)
        }
    } catch (e: Exception) {
        println("Player ${player.id} error: ${e.message}")
    }

    println("Player ${player.id} disconnected")
    players.remove(player.id)
    player.socket.close()

    val leftMsg = GameMessage.PlayerLeft(player.id)
    broadcast(leftMsg.toBytes())
}

fun sendToPlayer(player: Player, bytes: ByteArray) {
    try {
        // synchronized so two threads can't write to the same player at once
        synchronized(player.output) {
            player.output.write(bytes)
            player.output.flush()
        }
    } catch (e: Exception) {
        println("Failed to send to player ${player.id}")
    }
}

fun broadcast(bytes: ByteArray) {
    for ((_, player) in players) {
        sendToPlayer(player, bytes)
    }
}

fun main() {
    val serverSocket = ServerSocket(9999)
    println("Dungeon server started on port 9999")

    while (true) {
        val socket = serverSocket.accept()
        val id = nextPlayerId++
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val player = Player(id, socket, input, output)
        players[id] = player
        println("Player $id connected")

        Thread { handlePlayer(player) }.start()
    }
}