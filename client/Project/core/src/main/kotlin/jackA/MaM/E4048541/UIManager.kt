package jackA.MaM.E4048541

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport

    /*
                ~UIManager ~ Scene2D UI Creation/Manager~
    Responsible for positioning, creating and managing all UI elements

    Scene2D renders in screen coordinates independently of the game camera,
    so UI elements stay in fixed screen positions

    UI elements that need to react to game state (game over label, kill counter)
    are updated via the public update methods called by MessageHandler
    */
class UIManager(
    private val skin: Skin,
    private val network: NetworkClient,
    private val udpClient: UdpClient,
    private val player: Player,
    private val state: GameState,
    private val game: Main
) {
    //region Stage and Layout
    val stage = Stage(ScreenViewport())

    private val buttonSize = 100f
    //endregion

    //region UI Element References
    private lateinit var chatInput: TextField
    private lateinit var chatGroup: VerticalGroup
    private lateinit var killLabel: Label
    lateinit var gameOverLabel: Label
    lateinit var restartButton: TextButton
    lateinit var quitToMenuButton: TextButton
    //endregion

    fun buildUI() {
        buildGameOverUI()
        buildTouchpad()
        buildChatUI()
        buildKillLabel()
        buildCombatButtons()
    }

    //region UI Construction

    private fun buildGameOverUI() {
        // Game over label
        gameOverLabel = Label("", skin)
        gameOverLabel.setPosition(
            (Gdx.graphics.width / 2f) - 100f,
            (Gdx.graphics.height / 2f)
        )
        gameOverLabel.isVisible = false
        stage.addActor(gameOverLabel)

        // Restart button (requires all players to vote)
        restartButton = TextButton("Restart", skin)
        restartButton.setSize(150f, 60f)
        restartButton.setPosition(
            (Gdx.graphics.width / 2f) - 75f,
            (Gdx.graphics.height / 2f) - 80f
        )
        restartButton.isVisible = false
        restartButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                network.send(GameMessage.RestartRequest(player.ID).toBytes())
                restartButton.isVisible = false
            }
        })
        stage.addActor(restartButton)

        // Quit button
        quitToMenuButton = TextButton("Quit to Menu", skin)
        quitToMenuButton.setSize(150f, 60f)
        quitToMenuButton.setPosition(
            (Gdx.graphics.width / 2f) + 90f,
            (Gdx.graphics.height / 2f) - 80f
        )
        quitToMenuButton.isVisible = false
        quitToMenuButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                network.reset()
                game.setScreen<MenuScreen>()
                quitToMenuButton.isVisible = false
            }
        })
        stage.addActor(quitToMenuButton)
    }

    private fun buildTouchpad() {
        val touchpad = Touchpad(20f, skin)
        touchpad.setSize(200f, 200f)
        touchpad.setPosition(
            (Gdx.graphics.width * 0.1).toFloat(),
            (Gdx.graphics.height * 0.15).toFloat()
        )
        stage.addActor(touchpad)
        this.touchpad = touchpad
    }

    lateinit var touchpad: Touchpad

    private fun buildChatUI() {
        // Text input field for writing messages
        chatInput = TextField("", skin)
        chatInput.setSize(300f, 40f)
        chatInput.setPosition(
            (Gdx.graphics.width / 2f) - 150f,
            (Gdx.graphics.height - 50f)
        )
        stage.addActor(chatInput)

        // Send button
        val sendButton = TextButton("Send", skin)
        sendButton.setSize(80f, 40f)
        sendButton.setPosition(
            (Gdx.graphics.width / 2f) + 160f,
            (Gdx.graphics.height - 50f)
        )
        sendButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                val text = chatInput.text.trim()
                if (text.isNotEmpty()) {
                    udpClient.sendChat("Player ${player.ID}: $text")
                    chatInput.text = ""
                }
            }
        })
        stage.addActor(sendButton)

        // last 5 messages
        chatGroup = VerticalGroup()
        chatGroup.setPosition(10f, (Gdx.graphics.height - 200f))
        chatGroup.width = 400f
        stage.addActor(chatGroup)
    }

    private fun buildKillLabel() {
        killLabel = Label("", skin)
        killLabel.setPosition(
            (Gdx.graphics.width - 200f),
            (Gdx.graphics.height - 60f)
        )
        stage.addActor(killLabel)
    }

    private fun buildCombatButtons() {
        // Parry button
        val parryButton = TextButton("Parry", skin)
        parryButton.setSize(buttonSize, buttonSize)
        parryButton.setPosition(
            (Gdx.graphics.width * 0.7).toFloat(),
            (Gdx.graphics.height * 0.15).toFloat()
        )
        parryButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                network.send(GameMessage.ParryStart().toBytes())
                state.isParrying = true
            }
        })
        stage.addActor(parryButton)

        // Attack Buttons
        val lightButton = TextButton("Light", skin)
        lightButton.setSize(buttonSize, buttonSize)
        lightButton.setPosition(
            (Gdx.graphics.width * 0.8).toFloat(),
            (Gdx.graphics.height * 0.15).toFloat()
        )
        lightButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                network.send(GameMessage.AttackLight().toBytes())
                state.localAttackTimer = ATTACK_VISUAL_DURATION
            }
        })
        stage.addActor(lightButton)

        // press and hold to wind up, release to fire
        // touchDown returns true so Scene2D continues sending touch events to this listener
        val meleeButton = TextButton("Heavy Melee", skin)
        meleeButton.setSize(buttonSize, buttonSize)
        meleeButton.setPosition(
            (Gdx.graphics.width * 0.9).toFloat(),
            (Gdx.graphics.height * 0.15).toFloat()
        )
        meleeButton.addListener(object : InputListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                state.isHoldingHeavy = true
                state.localWindupTimer = MAX_WINDUP_DURATION
                network.send(GameMessage.AttackHeavyWindup().toBytes())
                return true
            }

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                state.isHoldingHeavy = false
                state.localWindupTimer = 0f
                state.localAttackTimer = ATTACK_VISUAL_DURATION
                network.send(GameMessage.AttackHeavy().toBytes())
                super.touchUp(event, x, y, pointer, button)
            }
        })
        stage.addActor(meleeButton)
    }

    //endregion

    //region UI Update Methods

    // used for both chat and system messages
    fun addMessage(text: String) {
        state.chatMessages.add(text)
        if (state.chatMessages.size > 5) state.chatMessages.removeAt(0)
        chatGroup.clear()
        for (msg in state.chatMessages) {
            chatGroup.addActor(Label(msg, skin))
        }
    }

    fun updateKillLabel() {
        val sb = StringBuilder()
        for ((id, kills) in state.killCounts) {
            sb.appendLine("Player $id: $kills kills")
        }
        killLabel.setText(sb.toString())
    }

    fun clearKillLabel() {
        state.killCounts.clear()
        killLabel.setText("")
    }

    fun showGameOver(winnerText: String) {
        gameOverLabel.setText(winnerText)
        gameOverLabel.isVisible = true
        restartButton.isVisible = true
        quitToMenuButton.isVisible = true
    }

    fun hideGameOver() {
        gameOverLabel.isVisible = false
        restartButton.isVisible = false
        quitToMenuButton.isVisible = false
    }

    //endregion

    companion object {
        const val ATTACK_VISUAL_DURATION = 0.2f
        const val MAX_WINDUP_DURATION = 2.0f
    }

    fun dispose() {
        stage.dispose()
    }
}
