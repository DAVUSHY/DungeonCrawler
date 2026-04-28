import java.net.ServerSocket
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.math.sqrt
import java.net.DatagramPacket
import java.net.DatagramSocket

// --- GAME STATE ---
// what the server "knows" about the world

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

val spawnPoints = listOf(
    Pair(80f, 304f),
    Pair(336f, 304f),
    Pair(80f, 176f),
    Pair(300f, 176f),
    Pair(208f, 304f),
    Pair(212f, 176f),
    Pair(403f, 60f)
)

data class HealthPickup(
    val id: Short,
    val x: Float,
    val y: Float,
    var active: Boolean = true,
)

val pickupLock = Any()

val healthRestoreAmount = 30

val healthPickups = listOf(
    HealthPickup(0.toShort(), 218f, 152f),
    HealthPickup(1.toShort(), 92f, 75f),
    HealthPickup(2.toShort(), 400f, 160f),
    HealthPickup(3.toShort(), 218f, 310f)
)

// How many kills to win
val killTarget = 5

var restartVotes = 0

val parryDuration = 0.5f

val minWindupDuration = 0.5f
val maxWindupDuration = 2.0f

val attackRadius: Float = 20.0f
val attackDamage: Int = 20

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
                        broadcast(GameMessage.AttackResult(player.id, newHealth).toBytes())
                        broadcast(GameMessage.PickupCollected(pickup.id).toBytes())
                        println("Player ${player.id} picked up health, now at $newHealth")
                    }
                }
            }

            // Broadcast to everyone with this player's ID attached
            val broadcastMsg = GameMessage.Move(player.id, message.x, message.y)
            broadcast(broadcastMsg.toBytes())
        }

        // The server shouldn't receive these — it sends them
        is GameMessage.Welcome -> {}
        is GameMessage.PlayerJoined -> {}
        is GameMessage.PlayerLeft -> {}

        is GameMessage.AttackResult -> {}

        is GameMessage.AttackLight -> {
            println("Light melee attack from player ${player.id}")
            broadcast(GameMessage.AttackLight(player.id).toBytes())

            // Elvis operator: will return here if the attacker we retrieve is null
            val attacker = players[player.id] ?: return

            for (target in players.values)
            {
                if (target != attacker)
                {
                    val dx = target.x - attacker.x
                    val dy = target.y - attacker.y

                    // pythag to calc dist in radius
                    val dist = sqrt((dx * dx) + (dy * dy))

                    // check if the target is within the attack radius
                    if (dist <= attackRadius)
                    {
                        if (target.isParrying) {
                            target.isParrying = false
                            target.parryTimer = 0f
                            sendToPlayer(target, GameMessage.ParryResult(target.id, true).toBytes())
                            println("Player ${target.id} parried a light attack!")
                            continue
                        }

                        // .coerceAtLeast(0): clamps the health to stop it from achieving negative values
                        val newHealth = (target.health - attackDamage).coerceAtLeast(0)
                        // apply the new health on the servers version of the target
                        target.health = newHealth

                        // then tell everyone else
                        val attackMessage = GameMessage.AttackResult(target.id, newHealth)
                        broadcast(attackMessage.toBytes())

                        if (newHealth <= 0) {
                            respawnPlayer(target, attacker)
                        }
                    }
                }
            }
        }

        is GameMessage.AttackHeavyWindup -> {
            val attacker = players[player.id] ?: return
            attacker.isWindingUp = true
            attacker.windupTimer = 0.0f
            println("Heavy melee windup from player ${player.id}")
            broadcast(GameMessage.AttackHeavyWindup(player.id).toBytes())
        }

        is GameMessage.AttackHeavy -> {
            val attacker = players[player.id] ?: return

            if (attacker.windupTimer >= minWindupDuration && attacker.isWindingUp) {
                attacker.isWindingUp = false
                attacker.windupTimer = 0f
                println("Heavy melee attack from player ${player.id}")
                broadcast(GameMessage.AttackHeavy(player.id).toBytes())
                processHeavyAttack(attacker)
            } else {
                // Either too short or already forced out — just broadcast to clean up visuals
                // Only send the cleanup if they were actually winding up to begin with
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
            // Tell all clients so they can show the visual
            broadcast(GameMessage.ParryStart(player.id).toBytes())
        }

        is GameMessage.ParryResult -> {}

        is GameMessage.ChatMessage -> {}

        is GameMessage.KillUpdate -> {}

        is GameMessage.GameOver -> {}

        is GameMessage.RestartRequest -> {
            restartVotes++
            if (restartVotes >= players.size) {
                restartVotes = 0
                for (p in players.values) {
                    p.kills = 0
                    respawnPlayer(p, null)
                }

                // Spawn in health packs on restart
                synchronized(pickupLock) {
                    for (pickup in healthPickups) {
                        pickup.active = true
                    }
                }

                broadcast(GameMessage.GameRestart().toBytes())
            }
        }

        is GameMessage.GameRestart -> {}

        is GameMessage.PickupCollected -> {}
        is GameMessage.PickupSpawned -> {
            println("Health pickup spawned!")
        }

    }
}

// --- NETWORKING (same as before, barely changed) ---

fun handlePlayer(player: Player) {
    // Gives player a random spawn point instead of stacking
    respawnPlayer(player, null)

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

    // Sync pickup states for the new player
    synchronized(pickupLock) {
        for (pickup in healthPickups) {
            if (!pickup.active) {
                sendToPlayer(player, GameMessage.PickupCollected(pickup.id).toBytes())
            }
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
    restartVotes = 0  // reset so remaining players aren't stuck

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
                    // LAN discovery — respond with server info
                    val response = "SERVER_HERE:9999"
                    val responseBytes = response.toByteArray()
                    val responsePacket = DatagramPacket(responseBytes, responseBytes.size, senderAddress, senderPort)
                    udpSocket.send(responsePacket)
                    println("Discovery request from $senderAddress — responded")
                }
                message.startsWith("CHAT:") -> {
                    // Extract the message and broadcast to all TCP clients
                    val chatContent = message.removePrefix("CHAT:")
                    println("Chat received: $chatContent")
                    // Broadcast as TCP message to all connected players
                    broadcast(GameMessage.ChatMessage(chatContent).toBytes())
                }
            }
        }
    }.start()
}

// --- Core Logic ---

fun startGameLoop() {
    Thread {
        var lastTime = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val delta = (now - lastTime) / 1000f
            lastTime = now

            tick(delta)
            Thread.sleep(16) // ~60 ticks per second
        }
    }.start()
}

fun tick(delta: Float) {
    synchronized(players) {
        for (player in players.values) {
            if (player.isParrying) {
                player.parryTimer += delta
                if (player.parryTimer >= parryDuration) {
                    player.isParrying = false
                    player.parryTimer = 0f
                    // Tell the client their parry window closed with no hit
                    sendToPlayer(player, GameMessage.ParryResult(player.id, false).toBytes())
                }
            }

            if (player.isWindingUp) {
                player.windupTimer += delta
                if (player.windupTimer >= maxWindupDuration) {
                    println("Forced heavy melee attack from player ${player.id}")
                    player.isWindingUp = false
                    player.windupTimer = 0f
                    // Force the heavy attack out
                    processHeavyAttack(player)
                }
            }
        }
    }
}

fun processHeavyAttack(attacker: Player) {
    val heavyRadius = 35.0f
    val heavyDamage = 40

    for (target in players.values) {
        if (target != attacker) {
            val dx = target.x - attacker.x
            val dy = target.y - attacker.y
            val dist = sqrt((dx * dx) + (dy * dy))

            if (dist <= heavyRadius) {
                // Check if target is parrying — if so, negate the hit
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

                if (newHealth <= 0) {
                    respawnPlayer(target, attacker)
                }
            }
        }
    }
}

fun respawnPlayer(target: Player, killer: Player?) {
    killer?.kills = (killer?.kills ?: 0) + 1
    killer?.let {
        println("Player ${it.id} now has ${it.kills} kills")
        broadcast(GameMessage.KillUpdate(it.id, it.kills).toBytes())
        if (it.kills >= killTarget) {
            broadcast(GameMessage.GameOver(it.id).toBytes())
        }
    }

    val spawnPoint = spawnPoints.random()
    target.health = 100
    target.x = spawnPoint.first
    target.y = spawnPoint.second
    println("Player ${target.id} respawned at ${spawnPoint.first}, ${spawnPoint.second}")
    broadcast(GameMessage.AttackResult(target.id, 100).toBytes())
    broadcast(GameMessage.Move(target.id, target.x, target.y).toBytes())
}

fun main()
{
    val serverSocket = ServerSocket(9999)
    println("Dungeon server started on port 9999")

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
        println("Player $id connected")

        Thread { handlePlayer(player) }.start()
    }
}