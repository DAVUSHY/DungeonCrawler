import java.nio.ByteBuffer

/*
               ~ GameMessage ~ Binary Protocol Definition ~

    All client-server communication uses a custom binary protocol over TCP.
    Every message follows this wire format:

        [2 bytes: payload length] [1 byte: type ID] [N bytes: payload fields]

    The 2-byte length prefix allows the receiver to know exactly how many
    bytes to read before attempting to parse. The type byte is read first
    to determine which subclass handles the remaining payload.

    All IDs use Short (2 bytes) for consistency and network efficiency.
    Strings (ChatMessage) use a 2-byte length prefix followed by UTF-8 bytes.
    Booleans (ParryResult) are encoded as a single byte: 1 = true, 0 = false,
    since Java/Kotlin ByteBuffer has no putBoolean method.
 */

sealed class GameMessage {

    companion object {

        //region Message Type ID Registry
        // Each message type has a unique byte ID used to route incoming messages.
        // IDs are grouped by category for readability. Never reuse or reorder IDs
        // as this would break protocol compatibility between client and server.

        // Connection lifecycle
        const val TYPE_MOVE: Byte = 1
        const val TYPE_PLAYER_JOINED: Byte = 2
        const val TYPE_PLAYER_LEFT: Byte = 3
        const val TYPE_WELCOME: Byte = 4

        // Combat — attacks
        const val TYPE_ATTACK_LIGHT: Byte = 5
        const val TYPE_ATTACK_RESULT: Byte = 6
        const val TYPE_ATTACK_HEAVY_WINDUP: Byte = 7
        const val TYPE_ATTACK_HEAVY: Byte = 8

        // Combat — parry
        const val TYPE_PARRY_START: Byte = 9
        const val TYPE_PARRY_RESULT: Byte = 10

        // Communication
        const val TYPE_CHAT_MESSAGE: Byte = 11

        // Game state
        const val TYPE_KILL_UPDATE: Byte = 12
        const val TYPE_GAME_OVER: Byte = 13
        const val TYPE_RESTART_REQUEST: Byte = 14
        const val TYPE_GAME_RESTART: Byte = 15

        // World — pickups
        const val TYPE_PICKUP_COLLECTED: Byte = 16
        const val TYPE_PICKUP_SPAWNED: Byte = 17

        //endregion

        //region Message Deserialisation Router
        /**
         * Reads the type byte from the buffer and delegates parsing
         * to the appropriate subclass. Called once per received message
         * after the 2-byte length prefix has already been consumed.
         */
        fun fromBytes(buffer: ByteBuffer): GameMessage {
            val type = buffer.get()
            return when (type) {
                // Connection
                TYPE_MOVE -> Move.fromBytes(buffer)
                TYPE_PLAYER_JOINED -> PlayerJoined.fromBytes(buffer)
                TYPE_PLAYER_LEFT -> PlayerLeft.fromBytes(buffer)
                TYPE_WELCOME -> Welcome.fromBytes(buffer)

                // Combat
                TYPE_ATTACK_LIGHT -> AttackLight.fromBytes(buffer)
                TYPE_ATTACK_RESULT -> AttackResult.fromBytes(buffer)
                TYPE_ATTACK_HEAVY_WINDUP -> AttackHeavyWindup.fromBytes(buffer)
                TYPE_ATTACK_HEAVY -> AttackHeavy.fromBytes(buffer)

                // Parry
                TYPE_PARRY_START -> ParryStart.fromBytes(buffer)
                TYPE_PARRY_RESULT -> ParryResult.fromBytes(buffer)

                // Communication
                TYPE_CHAT_MESSAGE -> ChatMessage.fromBytes(buffer)

                // Game state
                TYPE_KILL_UPDATE -> KillUpdate.fromBytes(buffer)
                TYPE_GAME_OVER -> GameOver.fromBytes(buffer)
                TYPE_RESTART_REQUEST -> RestartRequest.fromBytes(buffer)
                TYPE_GAME_RESTART -> GameRestart.fromBytes(buffer)

                // World
                TYPE_PICKUP_COLLECTED -> PickupCollected.fromBytes(buffer)
                TYPE_PICKUP_SPAWNED -> PickupSpawned.fromBytes(buffer)

                else -> throw IllegalArgumentException("Unknown message type: $type")
            }
        }
        //endregion
    }

    /** Every message subclass must be able to serialise itself to bytes for transmission. */
    abstract fun toBytes(): ByteArray

    /**
     * Wraps a payload with the 2-byte length prefix required by the protocol.
     * Every subclass calls this as the final step of toBytes() so the framing
     * is always consistent — the receiver reads 2 bytes for length, then
     * reads exactly that many bytes for the payload.
     */
    protected fun wrapWithLength(payload: ByteArray): ByteArray {
        val length = payload.size.toShort()
        return ByteBuffer.allocate(2 + payload.size)
            .putShort(length)
            .put(payload)
            .array()
    }

    //region Connection Messages

    /**
     * Sent by the client every frame while moving.
     * The server updates its authoritative position and broadcasts
     * to all other clients with the sender's ID attached.
     * Payload: [Short playerID] [Float x] [Float y] = 11 bytes
     */
    data class Move(
        val playerID: Short = 0,
        val x: Float,
        val y: Float
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2 + 4 + 4)
                .put(TYPE_MOVE)
                .putShort(playerID)
                .putFloat(x)
                .putFloat(y)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): Move {
                val playerID = buffer.short
                val x = buffer.float
                val y = buffer.float
                return Move(playerID, x, y)
            }
        }
    }

    /**
     * Broadcast by the server when a new player connects.
     * Includes spawn position so receiving clients can place
     * the new OtherPlayer at the correct location immediately,
     * avoiding the position-snap that would occur if only a
     * Move message followed later.
     * Payload: [Short playerID] [Float x] [Float y] = 11 bytes
     */
    data class PlayerJoined(
        val playerID: Short,
        val x: Float,
        val y: Float
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2 + 4 + 4)
                .put(TYPE_PLAYER_JOINED)
                .putShort(playerID)
                .putFloat(x)
                .putFloat(y)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): PlayerJoined {
                val playerID = buffer.short
                val x = buffer.float
                val y = buffer.float
                return PlayerJoined(playerID, x, y)
            }
        }
    }

    /**
     * Broadcast by the server when a player disconnects.
     * Clients remove the corresponding OtherPlayer from their map.
     * Payload: [Short playerID] = 3 bytes
     */
    data class PlayerLeft(
        val playerID: Short
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_PLAYER_LEFT)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): PlayerLeft {
                val playerID = buffer.short
                return PlayerLeft(playerID)
            }
        }
    }

    /**
     * Sent by the server immediately after a client connects.
     * Assigns the client their unique player ID for this session.
     * The client waits for this message before transitioning from
     * the loading screen to the game screen.
     * Payload: [Short playerID] = 3 bytes
     */
    data class Welcome(
        val playerID: Short
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_WELCOME)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): Welcome {
                val playerID = buffer.short
                return Welcome(playerID)
            }
        }
    }

    //endregion

    //region Combat Messages

    /**
     * Sent by the client when the light attack button is tapped.
     * The server validates the attack, checks for parry, applies damage
     * if appropriate, and responds with AttackResult. Also broadcast
     * to other clients to trigger the attack visual.
     * Payload: [Short playerID] = 3 bytes
     */
    data class AttackLight(
        val playerID: Short = 0
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_ATTACK_LIGHT)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): AttackLight {
                val playerID = buffer.short
                return AttackLight(playerID)
            }
        }
    }

    /**
     * Sent by the server as the authoritative result of any attack
     * (light, heavy, or health pickup). Carries the target's new health
     * so all clients stay in sync with the server's ground truth.
     * Also used for respawn — server sends this with health = 100
     * after a player dies, followed by a Move message for new position.
     * Payload: [Short targetID] [Int newHealth] = 7 bytes
     */
    data class AttackResult(
        val targetID: Short,
        val newHealth: Int
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2 + 4)
                .put(TYPE_ATTACK_RESULT)
                .putShort(targetID)
                .putInt(newHealth)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): AttackResult {
                val targetID = buffer.short
                val newHealth = buffer.int
                return AttackResult(targetID, newHealth)
            }
        }
    }

    /**
     * Sent by the client when the heavy attack button is pressed down.
     * The server starts the windup timer. Broadcast to other clients
     * so they can show the blue windup visual indicator on the attacker,
     * giving the target a chance to react and parry.
     * Payload: [Short playerID] = 3 bytes
     */
    data class AttackHeavyWindup(
        val playerID: Short = 0
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_ATTACK_HEAVY_WINDUP)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): AttackHeavyWindup {
                val playerID = buffer.short
                return AttackHeavyWindup(playerID)
            }
        }
    }

    /**
     * Sent by the client when the heavy attack button is released.
     * The server checks windupTimer >= minWindupDuration to determine
     * if it counts as a heavy attack or a cancelled tap. If the server
     * already forced the attack out via tick(), isWindingUp will be false
     * and the message is ignored to prevent a double hit.
     * Payload: [Short playerID] = 3 bytes
     */
    data class AttackHeavy(
        val playerID: Short = 0
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_ATTACK_HEAVY)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): AttackHeavy {
                val playerID = buffer.short
                return AttackHeavy(playerID)
            }
        }
    }

    //endregion

    //region Parry Messages

    /**
     * Sent by the client when the parry button is tapped.
     * The server sets isParrying = true and starts the parry timer.
     * Also broadcast to all clients so they can show the cyan
     * parry visual on the parrying player.
     * The server owns the parry window duration — the client just
     * signals intent and waits for ParryResult.
     * Payload: [Short playerID] = 3 bytes
     */
    data class ParryStart(
        val playerID: Short = 0
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_PARRY_START)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): ParryStart {
                val playerID = buffer.short
                return ParryStart(playerID)
            }
        }
    }

    /**
     * Sent by the server to the parrying client only when the parry
     * window closes — either because an attack was successfully blocked
     * (success = true) or because the timer expired with no attack
     * landing (success = false).
     * Boolean is encoded as 1 byte since ByteBuffer has no putBoolean.
     * Payload: [Short playerID] [Byte success] = 4 bytes
     */
    data class ParryResult(
        val playerID: Short = 0,
        val success: Boolean
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2 + 1)
                .put(TYPE_PARRY_RESULT)
                .putShort(playerID)
                .put(if (success) 1.toByte() else 0.toByte())
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): ParryResult {
                val playerID = buffer.short
                val success = buffer.get() == 1.toByte()
                return ParryResult(playerID, success)
            }
        }
    }

    //endregion

    //region Communication Messages

    /**
     * Player chat sent over UDP from client to server, then relayed
     * to all clients over TCP. UDP is used for the initial send since
     * dropped chat messages are acceptable — low latency matters more
     * than guaranteed delivery for chat.
     * String uses a 2-byte length prefix followed by UTF-8 bytes
     * since string length is variable and ByteBuffer needs to know
     * how many bytes to read.
     * Payload: [Short messageLength] [N bytes UTF-8 message]
     */
    data class ChatMessage(
        val message: String
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val payload = ByteBuffer.allocate(1 + 2 + messageBytes.size)
                .put(TYPE_CHAT_MESSAGE)
                .putShort(messageBytes.size.toShort())
                .put(messageBytes)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): ChatMessage {
                val length = buffer.short.toInt()
                val messageBytes = ByteArray(length)
                buffer.get(messageBytes)
                return ChatMessage(String(messageBytes, Charsets.UTF_8))
            }
        }
    }

    //endregion

    //region Game State Messages

    /**
     * Broadcast by the server whenever a kill is confirmed.
     * Carries the killer's total kill count so all clients can
     * update the scoreboard display simultaneously.
     * Payload: [Short killerID] [Int kills] = 7 bytes
     */
    data class KillUpdate(
        val killerID: Short,
        val kills: Int
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2 + 4)
                .put(TYPE_KILL_UPDATE)
                .putShort(killerID)
                .putInt(kills)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): KillUpdate {
                val killerID = buffer.short
                val kills = buffer.int
                return KillUpdate(killerID, kills)
            }
        }
    }

    /**
     * Broadcast by the server when a player reaches the kill target.
     * Clients freeze input, display the winner label, and show
     * the restart/quit buttons.
     * Payload: [Short winnerID] = 3 bytes
     */
    data class GameOver(
        val winnerID: Short
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_GAME_OVER)
                .putShort(winnerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): GameOver {
                val winnerID = buffer.short
                return GameOver(winnerID)
            }
        }
    }

    /**
     * Sent by a client when they press the restart button.
     * The server counts votes — when all connected players have voted,
     * it resets all state and broadcasts GameRestart.
     * Payload: [Short playerID] = 3 bytes
     */
    data class RestartRequest(
        val playerID: Short
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_RESTART_REQUEST)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): RestartRequest {
                val playerID = buffer.short
                return RestartRequest(playerID)
            }
        }
    }

    /**
     * Broadcast by the server when all players have voted to restart.
     * Clients reset all game state — scores, UI, pickup states —
     * and resume play from fresh spawn points.
     * Carries no payload since no data needs to accompany the signal.
     * Payload: none = 1 byte (type only)
     */
    class GameRestart : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1)
                .put(TYPE_GAME_RESTART)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): GameRestart {
                return GameRestart()
            }
        }
    }

    //endregion

    //region World Messages

    /**
     * Broadcast by the server when a player walks over an active pickup.
     * The pickup ID identifies which cross to remove from the client's
     * render. A special ID of -1 is not used here — that signal is
     * handled implicitly by GameRestart resetting all pickup states.
     * Payload: [Short pickupID] = 3 bytes
     */
    class PickupCollected(
        val pickupID: Short
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_PICKUP_COLLECTED)
                .putShort(pickupID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): PickupCollected {
                val pickupID = buffer.short
                return PickupCollected(pickupID)
            }
        }
    }

    /**
     * Sent by the server to a newly joining client to sync any pickups
     * that were already collected before they joined. This prevents
     * late joiners from seeing ghost pickups that are visually present
     * but not collectable on the server.
     * Payload: [Short pickupID] = 3 bytes
     */
    class PickupSpawned(
        val pickupID: Short
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_PICKUP_SPAWNED)
                .putShort(pickupID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): PickupSpawned {
                val pickupID = buffer.short
                return PickupSpawned(pickupID)  // Fixed: was incorrectly returning PickupCollected
            }
        }
    }

    //endregion
}