package jackA.MaM.E4048541

/*
                ~GameState ~ Shared Mutable Game State~

    Holds all states/ variables that needs to be accessed by multiple systems
    (GameRenderer, UIManager, MessageHandler )
    Passing a single GameState reference has proven cleaner than threading
    individual variables through every constructor

    reset() is called on show() each time FirstScreen becomes active,
    to get  a clean slate between sessions. This prevents more stale state
    visual bugs which have plagued this project
 */
class GameState {

    //region Player State
    val otherPlayers = mutableMapOf<Int, OtherPlayer>()
    //endregion

    //region Combat Visual Timers

    // Map = playerID & remaining display time.
    // Set when an AttackLight or AttackHeavy message is received.
    // Decremented each frame, entry removed when timer reaches zero.
    val attackVisuals = mutableMapOf<Int, Float>()

    // other "visuals" maps follow a similar structure to one another
    val windupVisuals = mutableMapOf<Int, Float>()

    // ~Parry~
    val parryVisuals = mutableMapOf<Int, Float>()
    var isParrying = false
    var parryVisualTimer = 0f

    // ~Attack~
    var localAttackTimer = 0f
    var localWindupTimer = 0f
    var isHoldingHeavy = false
    //endregion

    //region Game Flow State
    var gameOver = false
    var winnerID: Short = -1

    val killCounts = mutableMapOf<Int, Int>()
    //endregion

    //region World State

    // Tracks which pickups are currently active (visible and collectable).
    val pickupStates = mutableMapOf(
        0.toShort() to true,
        1.toShort() to true,
        2.toShort() to true,
        3.toShort() to true
    )
    //endregion

    //region Chat
    // list of the last 5 chat and or system messages shown in the chat feed
    val chatMessages = mutableListOf<String>()
    //endregion

    fun reset() {
        otherPlayers.clear()
        killCounts.clear()
        gameOver = false
        winnerID = -1
        chatMessages.clear()
        isParrying = false
        parryVisualTimer = 0f
        localAttackTimer = 0f
        localWindupTimer = 0f
        isHoldingHeavy = false
        attackVisuals.clear()
        windupVisuals.clear()
        parryVisuals.clear()
        pickupStates.keys.forEach { pickupStates[it] = true }
    }
}

