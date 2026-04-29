package jackA.MaM.E4048541

    /*
                           ~ Message Handler ~
    Processes all of the incoming game messages from the server
    updating the game state and UI according.

    For clarity, I've separated each message type. I've found this a much cleaner implementation
    to what I previously had setup in main.

    The client acts on the servers requests to maintain authority. With health, positions
    alongside the game state changes all originating from server messages here.
    */
class MessageHandler(
    private val network: NetworkClient,
    private val player: Player,
    private val state: GameState,
    private val ui: UIManager
) {

    // Called every frame from first screens render
    fun process() {
        for (message in network.getMessages()) {
            when (message) {
                is GameMessage.Move -> handleMove(message)
                is GameMessage.PlayerJoined -> handlePlayerJoined(message)
                is GameMessage.PlayerLeft -> handlePlayerLeft(message)
                is GameMessage.Welcome -> handleWelcome()
                is GameMessage.AttackLight -> handleAttackLight(message)
                is GameMessage.AttackResult -> handleAttackResult(message)
                is GameMessage.AttackHeavyWindup -> handleHeavyWindup(message)
                is GameMessage.AttackHeavy -> handleHeavyAttack(message)
                is GameMessage.ParryStart -> handleParryStart(message)
                is GameMessage.ParryResult -> handleParryResult(message)
                is GameMessage.ChatMessage -> handleChat(message)
                is GameMessage.KillUpdate -> handleKillUpdate(message)
                is GameMessage.GameOver -> handleGameOver(message)
                is GameMessage.GameRestart -> handleGameRestart()
                is GameMessage.PickupCollected -> handlePickupCollected(message)
                is GameMessage.PickupSpawned -> handlePickupSpawned(message)
                is GameMessage.RestartRequest -> {}
            }
        }
    }

    //region Connection Handlers

    // Moves based on who the message is from
    private fun handleMove(message: GameMessage.Move) {
        if (message.playerID != network.myId) {
            state.otherPlayers[message.playerID.toInt()]?.let {
                it.X = message.x
                it.Y = message.y
            }
        } else {
            // Server-authoritative position correction (e.g. respawn teleport)
            player.X = message.x
            player.Y = message.y
        }
    }

    // Position of new player is included in the message to render them instantly
    // If the game is over on join I reset everything
    private fun handlePlayerJoined(message: GameMessage.PlayerJoined) {
        if (message.playerID == network.myId) return

        state.otherPlayers[message.playerID.toInt()] = OtherPlayer(message.x, message.y)
        ui.addMessage("Player ${message.playerID} joined the game!")

        if (state.gameOver) {
            state.gameOver = false
            ui.hideGameOver()
            ui.clearKillLabel()
            ui.addMessage("New player joined! GAME RESET")
        }
    }

    private fun handlePlayerLeft(message: GameMessage.PlayerLeft) {
        state.otherPlayers.remove(message.playerID.toInt())
        ui.addMessage("Player ${message.playerID} left the game.")

        if (state.gameOver) {
            ui.restartButton.isVisible = false
            ui.addMessage("Opponent left. Return to menu or wait for a new player.")
        }
    }

    // Assigns id alongside a move message for the server to know where they are
    private fun handleWelcome() {
        player.ID = network.myId
        network.send(GameMessage.Move(x = player.X, y = player.Y).toBytes())
    }

    //endregion

    //region Combat Handlers

    // Is used on both registering healthpack healing and player damage (server authoritative)
    // Also handles respawn health reset (Server sends newhealth = 100 on respawn)
    private fun handleAttackResult(message: GameMessage.AttackResult) {
        if (message.targetID == network.myId) {
            player.health = message.newHealth
            player.triggerHit()
        } else {
            state.otherPlayers[message.targetID.toInt()]?.let {
                it.health = message.newHealth
                it.triggerHit()
            }
        }
    }

    // sets up visuals for all otherPlayer attacks & windup.
    private fun handleAttackLight(message: GameMessage.AttackLight) {
        state.attackVisuals[message.playerID.toInt()] = UIManager.ATTACK_VISUAL_DURATION
    }

    private fun handleHeavyWindup(message: GameMessage.AttackHeavyWindup) {
        state.windupVisuals[message.playerID.toInt()] = UIManager.MAX_WINDUP_DURATION
    }

    private fun handleHeavyAttack(message: GameMessage.AttackHeavy) {
        state.attackVisuals[message.playerID.toInt()] = UIManager.ATTACK_VISUAL_DURATION
        state.windupVisuals.remove(message.playerID.toInt())
    }

    //endregion

    //region Parry Handlers

    // Sets up the visuals for otherPlayer parryies
    private fun handleParryStart(message: GameMessage.ParryStart) {
        if (message.playerID != network.myId) {
            state.parryVisuals[message.playerID.toInt()] = state.parryVisualTimer.let { 0.5f }
        }
    }

    private fun handleParryResult(message: GameMessage.ParryResult) {
        if (message.playerID != network.myId) return
        state.isParrying = false
        state.parryVisualTimer = 0.5f
        if (message.success) ui.addMessage("Parry successful!")
    }

    //endregion

    //region Communication Handlers

    private fun handleChat(message: GameMessage.ChatMessage) {
        ui.addMessage(message.message)
    }

    //endregion

    //region Game State Handlers

    private fun handleKillUpdate(message: GameMessage.KillUpdate) {
        state.killCounts[message.killerID.toInt()] = message.kills
        ui.addMessage("Player ${message.killerID} scored a kill! (${message.kills}/${KILL_TARGET})")
        ui.updateKillLabel()
    }

    private fun handleGameOver(message: GameMessage.GameOver) {
        state.gameOver = true
        state.winnerID = message.winnerID
        val text = if (message.winnerID == network.myId) "YOU WIN!" else "Player ${message.winnerID} Wins!"
        ui.showGameOver(text)
        ui.addMessage(text)
    }

    // all players must vote for a restart to happen
    private fun handleGameRestart() {
        state.gameOver = false
        ui.hideGameOver()
        ui.clearKillLabel()
        state.pickupStates.keys.forEach { state.pickupStates[it] = true }
        ui.addMessage("Game restarted!")
    }

    //endregion

    //region World Handlers

    private fun handlePickupCollected(message: GameMessage.PickupCollected) {
        state.pickupStates[message.pickupID] = false
        ui.addMessage("A health pickup was collected!")
    }

    // Sent by the server to sync the pickup state for any late joining players
    // fixing an issue where they would see all pickups as active if someone
    // had been collected the pickups before they connect.
    private fun handlePickupSpawned(message: GameMessage.PickupSpawned) {
        state.pickupStates[message.pickupID] = true
    }

    //endregion

    companion object {
        private const val KILL_TARGET = 5
    }
}
