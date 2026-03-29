import java.nio.ByteBuffer

sealed class GameMessage {

    companion object {
        // Message type IDs
        const val TYPE_MOVE: Byte = 1
        const val TYPE_PLAYER_JOINED: Byte = 2
        const val TYPE_PLAYER_LEFT: Byte = 3
        const val TYPE_ATTACK_MELEE: Byte = 4
        const val TYPE_WELCOME: Byte = 5

        // Reads the type byte and delegates to the right subclass
        fun fromBytes(buffer: ByteBuffer): GameMessage {
            val type = buffer.get()
            return when (type) {
                TYPE_MOVE -> Move.fromBytes(buffer)
                TYPE_PLAYER_JOINED -> PlayerJoined.fromBytes(buffer)
                TYPE_PLAYER_LEFT -> PlayerLeft.fromBytes(buffer)
                TYPE_ATTACK_MELEE -> AttackMelee.fromBytes(buffer)
                TYPE_WELCOME -> Welcome.fromBytes(buffer)
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

    // --- ATTACK MELEE ---
    data class AttackMelee(
        val playerID: Short = 0
    ) : GameMessage() {

        override fun toBytes(): ByteArray {
            val payload = ByteBuffer.allocate(1 + 2)
                .put(TYPE_ATTACK_MELEE)
                .putShort(playerID)
                .array()
            return wrapWithLength(payload)
        }

        companion object {
            fun fromBytes(buffer: ByteBuffer): AttackMelee {
                val playerID = buffer.short
                return AttackMelee(playerID)
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
}
