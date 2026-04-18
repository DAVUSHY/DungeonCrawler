import java.nio.Buffer
import java.nio.ByteBuffer

sealed class GameMessage {

    companion object {
        // Message type IDs
        const val TYPE_MOVE: Byte = 1
        const val TYPE_PLAYER_JOINED: Byte = 2
        const val TYPE_PLAYER_LEFT: Byte = 3
        const val TYPE_WELCOME: Byte = 4

        // Attacks
        const val TYPE_ATTACK_LIGHT: Byte = 5
        const val TYPE_ATTACK_RESULT: Byte = 6
        const val TYPE_ATTACK_HEAVY_WINDUP: Byte = 7
        const val TYPE_ATTACK_HEAVY: Byte = 8

        // Parry
        const val TYPE_PARRY_START: Byte = 9
        const val TYPE_PARRY_RESULT: Byte = 10

        // Chat
        const val TYPE_CHAT_MESSAGE: Byte = 11

        // Kill
        const val TYPE_KILL_UPDATE: Byte = 12

        // Game Over
        const val TYPE_GAME_OVER: Byte = 13

        // Restart
        const val TYPE_RESTART_REQUEST: Byte = 14
        // Game Restart
        const val TYPE_GAME_RESTART: Byte = 15

        // Reads the type byte and delegates to the right subclass
        fun fromBytes(buffer: ByteBuffer): GameMessage {
            val type = buffer.get()
            return when (type) {
                TYPE_MOVE -> Move.fromBytes(buffer)
                TYPE_PLAYER_JOINED -> PlayerJoined.fromBytes(buffer)
                TYPE_PLAYER_LEFT -> PlayerLeft.fromBytes(buffer)
                TYPE_WELCOME -> Welcome.fromBytes(buffer)

                // Attacks
                TYPE_ATTACK_LIGHT -> AttackLight.fromBytes(buffer)
                TYPE_ATTACK_RESULT -> AttackResult.fromBytes(buffer)
                TYPE_ATTACK_HEAVY_WINDUP -> AttackHeavyWindup.fromBytes(buffer)
                TYPE_ATTACK_HEAVY -> AttackHeavy.fromBytes(buffer)

                // Parry
                TYPE_PARRY_START -> ParryStart.fromBytes(buffer)
                TYPE_PARRY_RESULT -> ParryResult.fromBytes(buffer)

                // Chat
                TYPE_CHAT_MESSAGE -> ChatMessage.fromBytes(buffer)

                // Kill
                TYPE_KILL_UPDATE -> KillUpdate.fromBytes(buffer)

                // GAME OVER
                TYPE_GAME_OVER -> GameOver.fromBytes(buffer)

                // RESTART
                TYPE_RESTART_REQUEST -> RestartRequest.fromBytes(buffer)
                // GAME RESTART
                TYPE_GAME_RESTART -> GameRestart.fromBytes(buffer)

                else -> throw IllegalArgumentException("Unknown message type: $type")
            }
        }
    }

    // Every message must be able to serialise itself
    abstract fun toBytes(): ByteArray

    // Helper to wrap payload with length prefix
    // Every subclass calls this so the length header is consistent
    protected fun wrapWithLength(payload: ByteArray): ByteArray {
        val length = payload.size.toShort()
        return ByteBuffer.allocate(2 + payload.size)
            .putShort(length)
            .put(payload)
            .array()
    }

    // --- MOVE ---
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

    // --- PLAYER JOINED ---
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

    // --- PLAYER LEFT ---
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

    // --- WELCOME ---
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

    // --- ATTACK RESULT ---
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
            fun fromBytes(buffer: ByteBuffer): AttackResult{
                val targetID = buffer.short
                val newHealth = buffer.int
                return AttackResult(targetID, newHealth)
            }
        }
    }

    // --- ATTACK LIGHT ---
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

    // --- ATTACK HEAVY WINDUP ---
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

    // --- ATTACK HEAVY ---
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

    // --- PARRY START ---
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

    // --- PARRY RESULT ---
    data class ParryResult(
        val playerID: Short = 0,
        val success: Boolean
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2 + 1)
                .put(TYPE_PARRY_RESULT)
                .putShort(playerID)
                .put(if (success) 1.toByte() else 0.toByte()) // Kotlin and java lack a .putBoolean have to use bytes
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

    // --- CHAT MESSAGE ---
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

    // --- KILL UPDATE ---
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
            fun fromBytes(buffer: ByteBuffer): KillUpdate{
                val killerID = buffer.short
                val kills = buffer.int
                return KillUpdate(killerID, kills)
            }
        }
    }

    // --- GAME OVER ---
    data class GameOver(
        val winnerID: Short,
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_GAME_OVER)
                .putShort(winnerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): GameOver{
                val winnerID = buffer.short
                return GameOver(winnerID)
            }
        }
    }

    // --- RESTART REQUEST ---
    data class RestartRequest(
        val playerID: Short,
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_RESTART_REQUEST)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): RestartRequest{
                val playerID = buffer.short
                return RestartRequest(playerID)
            }
        }
    }

    // --- GAME RESTART ---
    class GameRestart() : GameMessage(){
        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1)
                .put(TYPE_GAME_RESTART)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): GameRestart{
                return GameRestart()
            }
        }
    }
}
