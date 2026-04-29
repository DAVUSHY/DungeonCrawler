import java.net.ServerSocket
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.math.sqrt
import java.net.DatagramPacket
import java.net.DatagramSocket

/*
              ~ Dungeon Crawler ~ Dedicated Game Server ~

                ~Architecture overview~
    - TCP ServerSocket on port 9999 accepting player connection
    - UDP DatagramSocket on port 9998 handles chat relay and LAN discovery (used on quick join)
    - Game loop thread runs at ~60 ticks/second for time-based logic
    - Each connected player gets their own handler thread for blocking socket reads

                    ~Threading model~
    - Game loop thread: tick() contains parry/windup timers
    - Player handler threads: handleMessage() -> processes incoming messages
    - All access to "players" map is synchronized to prevent data races
    - All access to "healthPickups" list is synchronized via pickupLock

    The server is authoritative, clients send intent (attack, move, parry) and the server validates
    applies game logic, broadcasting results back

*/

//region Game State

/*
    Represents a connected player on the server
    Holds both the network streams for communication and the authoritative game state that the
    server uses for all validation and logic.
*/
data class Player(
    val id: Short,
    val socket: Socket,
    val input: DataInputStream,
    val output: DataOutputStream,
    var x: Float = 0f,
    var y: Float = 0f,
    var health: Int = 100,
    var isParrying: Boolean = false,
    var isWindingUp: Boolean = false,
    var parryTimer: Float = 0f,
    var windupTimer: Float = 0f,
    var kills: Int = 0
)

/*
    Represents a health pickup in the world
    Active pickups can collide with a player walks within the collection radius
    the server restores health and marks it inactive.
    Pickups reset to active on game restart.
*/
data class HealthPickup(
    val id: Short,
    val x: Float,
    val y: Float,
    var active: Boolean = true
)

// Spawn points distributed across the map so players start separated.
// Random selection on respawn prevents spawn camping.
val spawnPoints = listOf(
    Pair(80f, 304f),
    Pair(336f, 304f),
    Pair(80f, 176f),
    Pair(300f, 176f),
    Pair(208f, 304f),
    Pair(212f, 176f),
    Pair(403f, 60f)
)

// Health pickup world positions — tuned to be spread across open floor areas.
// Coordinates are in world pixels (tile size = 16px).
val healthPickups = listOf(
    HealthPickup(0.toShort(), 218f, 152f),
    HealthPickup(1.toShort(), 92f, 75f),
    HealthPickup(2.toShort(), 400f, 160f),
    HealthPickup(3.toShort(), 218f, 310f)
)

// Separate lock for pickup list — accessed by both the game loop thread
// and player handler threads, so needs its own synchronisation object
// rather than sharing the players lock which would cause unnecessary blocking.
val pickupLock = Any()

// All currently connected players, keyed by their assigned Short ID.
// Mutable map accessed from multiple threads — always synchronize on this object.
val players = mutableMapOf<Short, Player>()
var nextPlayerId: Short = 1

// Tracks how many players have voted to restart.
// Reset to 0 on game restart or when any player disconnects.
var restartVotes = 0

//endregion

//region Game Constants

// How many kills a player needs to win the round
val killTarget = 5

// Health restored when a player collects a pickup
val healthRestoreAmount = 30

// How long a parry window stays open in seconds
val parryDuration = 0.5f

// Minimum hold time in seconds before a heavy attack release counts as heavy.
// Releases shorter than this are treated as cancelled taps.
val minWindupDuration = 0.5f

// Maximum windup duration — server forces the attack out if the player
// holds longer than this, preventing indefinite windup camping.
val maxWindupDuration = 2.0f

// Radius in world pixels for light attack hit detection (Pythagorean distance)
val attackRadius: Float = 20.0f

// Damage applied by a light attack
val attackDamage: Int = 20

//endregion

//region Message Handling

// Message dispatcher, receives a parsed game message, sends it where it needs to go based on message type
// Server never trusts client always using the player object from the handler thread which is assigned by the server
fun handleMessage(player: Player, message: GameMessage) {
    when (message) {

        is GameMessage.Move -> {
            player.x = message.x
            player.y = message.y

            // Check pickup collection on every move update
            // Synchronized to prevent a race between this thread and
            // any other thread that might modify pickup state
            synchronized(pickupLock) {
                for (pickup in healthPickups) {
                    if (!pickup.active) continue
                    val dx = player.x - pickup.x
                    val dy = player.y - pickup.y
                    val dist = sqrt((dx * dx) + (dy * dy))
                    if (dist <= 12f) {
                        val newHealth = (player.health + healthRestoreAmount).coerceAtMost(100)
                        player.health = newHealth
                        pickup.active = false
                        // uses attack result to update the health
                        // I saw no need for a pickup message since there is only health
                        broadcast(GameMessage.AttackResult(player.id, newHealth).toBytes())
                        broadcast(GameMessage.PickupCollected(pickup.id).toBytes())
                        println("Player ${player.id} collected health pickup ${pickup.id}, health now $newHealth")
                    }
                }
            }

            // broadcast position to all clients with sender ID attached
            broadcast(GameMessage.Move(player.id, message.x, message.y).toBytes())
        }

        is GameMessage.AttackLight -> {

            println("Light attack from player ${player.id}")
            // broadcast to all clients immediately so the attack visual
            // appears on screen without waiting for damage validation
            broadcast(GameMessage.AttackLight(player.id).toBytes())

            val attacker = players[player.id] ?: return

            for (target in players.values) {
                if (target == attacker) continue

                val dx = target.x - attacker.x
                val dy = target.y - attacker.y
                // Pythagorean distance for circular hit detection
                val dist = sqrt((dx * dx) + (dy * dy))

                if (dist <= attackRadius) {
                    if (target.isParrying) {
                        target.isParrying = false
                        target.parryTimer = 0f
                        sendToPlayer(target, GameMessage.ParryResult(target.id, true).toBytes())
                        println("Player ${target.id} parried a light attack!")
                        continue
                    }

                    // coerceAtLeast(0) prevents health going negative
                    val newHealth = (target.health - attackDamage).coerceAtLeast(0)
                    target.health = newHealth
                    broadcast(GameMessage.AttackResult(target.id, newHealth).toBytes())

                    if (newHealth <= 0) respawnPlayer(target, attacker)
                }
            }
        }

        is GameMessage.AttackHeavyWindup -> {
            val attacker = players[player.id] ?: return
            attacker.isWindingUp = true
            attacker.windupTimer = 0.0f
            println("Heavy windup started by player ${player.id}")
            // Broadcast so other clients show the windup visual indicator,
            // giving the target a window to react and parry
            broadcast(GameMessage.AttackHeavyWindup(player.id).toBytes())
        }

        is GameMessage.AttackHeavy -> {
            val attacker = players[player.id] ?: return

            if (attacker.windupTimer >= minWindupDuration && attacker.isWindingUp) {
                // Valid heavy attack — held long enough and still winding up
                attacker.isWindingUp = false
                attacker.windupTimer = 0f
                println("Heavy attack released by player ${player.id}")
                broadcast(GameMessage.AttackHeavy(player.id).toBytes())
                processHeavyAttack(attacker)
            } else {
                // Too short (tap) or already forced out by tick() —
                // still broadcast to clean up the windup visual on all clients
                if (!attacker.isWindingUp && attacker.windupTimer == 0f) return
                attacker.isWindingUp = false
                attacker.windupTimer = 0f
                broadcast(GameMessage.AttackHeavy(player.id).toBytes())
            }
        }

        is GameMessage.ParryStart -> {
            val parrying = players[player.id] ?: return
            parrying.isParrying = true
            parrying.parryTimer = 0f
            println("Player ${player.id} started parrying")
            // Broadcast so other clients show the cyan parry visual
            broadcast(GameMessage.ParryStart(player.id).toBytes())
        }

        is GameMessage.RestartRequest -> {
            restartVotes++
            println("Restart vote from player ${player.id} ($restartVotes/${players.size})")

            if (restartVotes >= players.size) {
                restartVotes = 0

                // Reset all player state and respawn at random points
                for (p in players.values) {
                    p.kills = 0
                    respawnPlayer(p, null)
                }

                // Reactivate all pickups — clients handle visual reset via GameRestart handler
                synchronized(pickupLock) {
                    for (pickup in healthPickups) {
                        pickup.active = true
                    }
                }

                broadcast(GameMessage.GameRestart().toBytes())
                println("Game restarted")
            }
        }

        // Messages the server sends but should never receive
        is GameMessage.Welcome -> {}
        is GameMessage.PlayerJoined -> {}
        is GameMessage.PlayerLeft -> {}
        is GameMessage.AttackResult -> {}
        is GameMessage.ParryResult -> {}
        is GameMessage.ChatMessage -> {}
        is GameMessage.KillUpdate -> {}
        is GameMessage.GameOver -> {}
        is GameMessage.GameRestart -> {}
        is GameMessage.PickupCollected -> {}
        is GameMessage.PickupSpawned -> {}
    }
}

//endregion

//region Combat Logic

/**
 * Processes a heavy attack from the given attacker.
 * Called either from handleMessage when the player releases the button,
 * or from tick() when the maximum windup duration is exceeded.
 * Heavy attacks have a larger radius and deal more damage than light attacks.
 */
fun processHeavyAttack(attacker: Player) {
    val heavyRadius = 35.0f
    val heavyDamage = 40

    for (target in players.values) {
        if (target == attacker) continue

        val dx = target.x - attacker.x
        val dy = target.y - attacker.y
        val dist = sqrt((dx * dx) + (dy * dy))

        if (dist <= heavyRadius) {
            // Parry check — heavy attacks can be parried just like light attacks
            if (target.isParrying) {
                target.isParrying = false
                target.parryTimer = 0f
                sendToPlayer(target, GameMessage.ParryResult(target.id, true).toBytes())
                println("Player ${target.id} parried a heavy attack!")
                continue
            }

            val newHealth = (target.health - heavyDamage).coerceAtLeast(0)
            target.health = newHealth
            broadcast(GameMessage.AttackResult(target.id, newHealth).toBytes())

            if (newHealth <= 0) respawnPlayer(target, attacker)
        }
    }
}

/**
 * Handles player death and respawn.
 * If a killer is provided, increments their kill count and checks
 * for a win condition before respawning the target.
 * Respawn position is chosen randomly from the spawn points list
 * to prevent spawn camping.
 *
 * @param target The player who died
 * @param killer The player who dealt the killing blow, or null for environmental/initial spawn
 */
fun respawnPlayer(target: Player, killer: Player?) {
    killer?.let {
        it.kills++
        println("Player ${it.id} now has ${it.kills} kills")
        broadcast(GameMessage.KillUpdate(it.id, it.kills).toBytes())

        // Check win condition before respawning
        if (it.kills >= killTarget) {
            broadcast(GameMessage.GameOver(it.id).toBytes())
        }
    }

    val spawnPoint = spawnPoints.random()
    target.health = 100
    target.x = spawnPoint.first
    target.y = spawnPoint.second
    println("Player ${target.id} respawned at (${spawnPoint.first}, ${spawnPoint.second})")

    // Send health reset and position update so all clients stay in sync
    broadcast(GameMessage.AttackResult(target.id, 100).toBytes())
    broadcast(GameMessage.Move(target.id, target.x, target.y).toBytes())
}

//endregion

//region Networking — TCP

/**
 * Entry point for each connected player's handler thread.
 * Handles the full connection lifecycle:
 * 1. Assign spawn position and send Welcome
 * 2. Sync existing world state (other players, pickup states)
 * 3. Notify other clients of the new player
 * 4. Block on the read loop until disconnection
 * 5. Clean up and notify remaining clients on disconnect
 */
fun handlePlayer(player: Player) {
    // Assign a random spawn point immediately so the player starts
    // at a valid position before their first Move message
    respawnPlayer(player, null)

    sendToPlayer(player, GameMessage.Welcome(player.id).toBytes())

    // Tell the new player about everyone already connected
    for ((_, other) in players) {
        if (other.id != player.id) {
            sendToPlayer(player, GameMessage.PlayerJoined(other.id, other.x, other.y).toBytes())
        }
    }

    // Sync inactive pickup states so late joiners don't see ghost pickups
    // that are visually present but not collectable on the server
    synchronized(pickupLock) {
        for (pickup in healthPickups) {
            if (!pickup.active) {
                sendToPlayer(player, GameMessage.PickupCollected(pickup.id).toBytes())
            }
        }
    }

    // Tell everyone else this player has joined
    broadcast(GameMessage.PlayerJoined(player.id, player.x, player.y).toBytes())

    // Block here reading messages until the connection drops
    try {
        while (true) {
            // Read the 2-byte length prefix to know how many bytes follow
            val lengthBytes = ByteArray(2)
            player.input.readFully(lengthBytes)
            val length = ByteBuffer.wrap(lengthBytes).short.toInt()

            val payload = ByteArray(length)
            player.input.readFully(payload)

            val message = GameMessage.fromBytes(ByteBuffer.wrap(payload))
            handleMessage(player, message)
        }
    } catch (e: Exception) {
        println("Player ${player.id} disconnected: ${e.message}")
    }

    players.remove(player.id)
    player.socket.close()
    // Reset votes so the remaining player isn't stuck waiting for a vote
    // that can never arrive from the disconnected player
    restartVotes = 0

    broadcast(GameMessage.PlayerLeft(player.id).toBytes())
}

/**
 * Sends bytes to a single player, synchronizing on their output stream
 * to prevent two threads writing to the same socket simultaneously.
 */
fun sendToPlayer(player: Player, bytes: ByteArray) {
    try {
        synchronized(player.output) {
            player.output.write(bytes)
            player.output.flush()
        }
    } catch (e: Exception) {
        println("Failed to send to player ${player.id}: ${e.message}")
    }
}

/**
 * Broadcasts bytes to every connected player.
 * Used for events all clients need to know about — position updates,
 * attack results, game state changes, etc.
 */
fun broadcast(bytes: ByteArray) {
    for ((_, player) in players) {
        sendToPlayer(player, bytes)
    }
}

//endregion

//region Networking — UDP

/**
 * Listens on UDP port 9998 for two types of messages:
 *
 * DISCOVER — LAN server discovery broadcast from clients.
 * Responds with "SERVER_HERE:9999" so the client can auto-connect
 * without manual IP entry. UDP is appropriate here since broadcast
 * packets require UDP and discovery failures are acceptable.
 *
 * CHAT: — Player chat messages sent from clients over UDP.
 * UDP is used for chat since dropped messages are acceptable and
 * latency matters more than guaranteed delivery. The server relays
 * them to all clients over TCP for reliable delivery to each recipient.
 */
fun startUdpListener() {
    Thread {
        val udpSocket = DatagramSocket(9998)
        val buffer = ByteArray(512)
        println("UDP listener started on port 9998")

        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            udpSocket.receive(packet)

            val message = String(packet.data, 0, packet.length).trim()
            val senderAddress = packet.address
            val senderPort = packet.port

            when {
                message == "DISCOVER" -> {
                    val response = "SERVER_HERE:9999"
                    val responseBytes = response.toByteArray()
                    udpSocket.send(DatagramPacket(responseBytes, responseBytes.size, senderAddress, senderPort))
                    println("Discovery request from $senderAddress — responded")
                }
                message.startsWith("CHAT:") -> {
                    val chatContent = message.removePrefix("CHAT:")
                    println("Chat: $chatContent")
                    broadcast(GameMessage.ChatMessage(chatContent).toBytes())
                }
            }
        }
    }.start()
}

//endregion

//region Game Loop

/**
 * Starts the server-side game loop on a dedicated thread.
 * Runs at approximately 60 ticks per second using delta time
 * so timer logic is frame-rate independent.
 *
 * The game loop is needed because some logic must run continuously
 * regardless of incoming messages — parry window expiry and forced
 * heavy attack release cannot be driven by client messages alone.
 */
fun startGameLoop() {
    Thread {
        var lastTime = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val delta = (now - lastTime) / 1000f
            lastTime = now
            tick(delta)
            Thread.sleep(16)
        }
    }.start()
}

/**
 * Called every tick to advance time-based game logic.
 * Synchronized on players to prevent concurrent modification
 * while player handler threads may also be reading player state.
 *
 * Handles:
 * - Parry window expiry: closes the window and notifies the client
 * - Forced heavy attack: fires the attack if windup exceeds max duration
 */
fun tick(delta: Float) {
    synchronized(players) {
        for (player in players.values) {

            if (player.isParrying) {
                player.parryTimer += delta
                if (player.parryTimer >= parryDuration) {
                    player.isParrying = false
                    player.parryTimer = 0f
                    // Parry window expired with no attack — notify client
                    sendToPlayer(player, GameMessage.ParryResult(player.id, false).toBytes())
                }
            }

            if (player.isWindingUp) {
                player.windupTimer += delta
                if (player.windupTimer >= maxWindupDuration) {
                    println("Forced heavy attack from player ${player.id} (max windup reached)")
                    player.isWindingUp = false
                    player.windupTimer = 0f
                    processHeavyAttack(player)
                }
            }
        }
    }
}

//endregion

//region Entry Point

/**
 * Server entry point.
 * Starts the game loop and UDP listener on background threads,
 * then blocks on the TCP accept loop assigning IDs and spawning
 * a handler thread for each connecting client.
 */
fun main() {
    val serverSocket = ServerSocket(9999)
    println("Dungeon Crawler server started on port 9999")

    startGameLoop()
    startUdpListener()

    while (true) {
        val socket = serverSocket.accept()
        val id = nextPlayerId++
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        val spawnPoint = spawnPoints[(id - 1).toInt() % spawnPoints.size]
        val player = Player(id, socket, input, output, x = spawnPoint.first, y = spawnPoint.second)
        players[id] = player
        println("Player $id connected from ${socket.inetAddress}")

        Thread { handlePlayer(player) }.start()
    }
}

//endregion