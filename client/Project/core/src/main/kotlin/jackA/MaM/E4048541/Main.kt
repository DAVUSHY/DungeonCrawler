package jackA.MaM.E4048541

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.viewport.ScreenViewport
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.async.KtxAsync
import java.nio.ByteBuffer
import kotlin.math.sqrt

class Main : KtxGame<KtxScreen>() {

    override fun create() {
        KtxAsync.initiate()
        addScreen(LoadingScreen(this))
        addScreen(FirstScreen())
        setScreen<LoadingScreen>()

        // Run connection on background thread
        Thread {
            val loadingScreen = getScreen<LoadingScreen>()

            val udpClient = UdpClient("255.255.255.255")
            val discoveredHost = udpClient.discoverServer()

            val network = (getScreen<FirstScreen>() as FirstScreen).network

            // LibGDX UI updates must happen on the render thread, not a background one
            // using postRunnable here queues the update safely
            if (discoveredHost != null) {
                Gdx.app.postRunnable { loadingScreen.setStatus("Server found! Connecting...") }
                network.host = discoveredHost
            } else {
                Gdx.app.postRunnable { loadingScreen.setStatus("Connecting to default server...") }
            }

            network.connect()

            // Wait for welcome message
            while (network.myId == (-1).toShort()) {
                Thread.sleep(100)
            }

            Gdx.app.postRunnable {
                setScreen<FirstScreen>()
            }
        }.start()
    }
}

class FirstScreen : KtxScreen {
    private val shapeRenderer = ShapeRenderer()

    // Network connection — use your computer's local IP here
    // "10.0.2.2" is how the Android emulator refers to your PC's localhost
    val network = NetworkClient("10.0.2.2", 9999)

    private val udpClient = UdpClient("10.0.2.2")
    private val chatMessages = mutableListOf<String>()
    private lateinit var chatInput: TextField
    private lateinit var sendButton: TextButton

    private lateinit var chatGroup: VerticalGroup

    // Map renderer
    private val map = TmxMapLoader().load("DungeonMap.tmx")
    private val mapRenderer = OrthogonalTiledMapRenderer(map)

    // Camera - controlling which part of the map we are looking at
    private val camera = OrthographicCamera()

    private val player = Player(
        ::collidesWithWall,
        network::send
    )

    // Win con related
    private var gameOver = false
    private var winnerID: Short = -1
    private lateinit var gameOverLabel: Label

    private lateinit var restartButton: TextButton

    // UI
    private lateinit var skin: Skin
    private lateinit var stage: Stage
    private lateinit var touchpad: Touchpad
    private var touchpadX: Float = (Gdx.graphics.width * 0.1).toFloat()
    private var touchpadY: Float = (Gdx.graphics.height * 0.15).toFloat()
    private var touchpadSize = 200f

    private lateinit var parryButton: TextButton
    private var parryButtonX: Float = 0f
    private var parryButtonY: Float = 0f

    private var isParrying = false
    private var parryVisualTimer = 0f
    private val parryVisualDuration = 0.5f

    private lateinit var lightButton: TextButton
    private lateinit var meleeButton: TextButton
    private var meleeButtonX: Float = (Gdx.graphics.width * 0.9).toFloat()
    private var meleeButtonY: Float = (Gdx.graphics.height * 0.15).toFloat()
    private var meleeButtonSize = 100f

    private val attackVisuals = mutableMapOf<Int, Float>()
    private val windupVisuals = mutableMapOf<Int, Float>()
    private val parryVisuals = mutableMapOf<Int, Float>()
    private val attackVisualDuration = 0.2f
    private var maxWindupDuration = 2.0f

    private var localAttackTimer = 0f
    private var localWindupTimer = 0f

    private val windupVisualRadius = 10f

    private var isHoldingHeavy = false

    val attackRadius: Float = 20.0f

    private val killCounts = mutableMapOf<Int, Int>()
    private lateinit var killLabel: Label

    // Other players which is a map of players keyed by IDs
    private val otherPlayers = mutableMapOf<Int, OtherPlayer>()

    // Track if we've connected yet
    private var connected = false

    override fun show() {
        // boilerplate for setting up skin and stage
        skin = Skin(Gdx.files.internal("clean-crispy/skin/clean-crispy-ui.json"))

        stage = Stage(ScreenViewport())

        //Begin layout

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

        // --- GAME OVER ---
        gameOverLabel = Label("", skin)
        gameOverLabel.setPosition(
            (Gdx.graphics.width / 2f) - 100f,
            (Gdx.graphics.height / 2f)
        )
        gameOverLabel.isVisible = false
        stage.addActor(gameOverLabel)

        // --- TOUCHPAD ---
        touchpad = Touchpad(20f, skin)
        touchpad.setSize(touchpadSize, touchpadSize)
        touchpad.setPosition(touchpadX, touchpadY)

        stage.addActor(touchpad)

        // --- CHAT ---
        chatInput = TextField("", skin)
        chatInput.setSize(300f, 40f)
        chatInput.setPosition(
            (Gdx.graphics.width / 2f) - 150f,
            (Gdx.graphics.height - 50f)
        )
        stage.addActor(chatInput)

        sendButton = TextButton("Send", skin)
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

        chatGroup = VerticalGroup()
        chatGroup.setPosition(10f, (Gdx.graphics.height - 200f))
        chatGroup.width = 400f
        stage.addActor(chatGroup)

        // Kill log
        killLabel = Label("", skin)
        killLabel.setPosition(
            (Gdx.graphics.width - 200f),
            (Gdx.graphics.height - 60f)
        )
        stage.addActor(killLabel)

        // --- LIGHT MELEE BUTTON ---
        lightButton = TextButton("Light", skin)
        lightButton.setSize(meleeButtonSize, meleeButtonSize)
        lightButton.setPosition(
            (Gdx.graphics.width * 0.8).toFloat(),
            (Gdx.graphics.height * 0.15).toFloat()
        )
        lightButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                network.send(GameMessage.AttackLight().toBytes())
                localAttackTimer = attackVisualDuration
            }
        })
        stage.addActor(lightButton)

        // --- HEAVY MELEE BUTTON ---
        meleeButton = TextButton("Heavy Melee", skin)
        meleeButton.setSize(meleeButtonSize, meleeButtonSize)
        meleeButton.setPosition(
            (Gdx.graphics.width * 0.9).toFloat(),
            (Gdx.graphics.height * 0.15).toFloat()
        )

        meleeButton.addListener(object : InputListener() {
            override fun touchDown(
                event: InputEvent?,
                x: Float,
                y: Float,
                pointer: Int,
                button: Int
            ): Boolean {
                isHoldingHeavy = true
                localWindupTimer = maxWindupDuration
                // Tell the server that we are starting the wind up so that it can complete it!
                network.send(GameMessage.AttackHeavyWindup().toBytes())
                return true
            }

            override fun touchUp(
                event: InputEvent?,
                x: Float,
                y: Float,
                pointer: Int,
                button: Int
            ) {
                isHoldingHeavy = false
                localWindupTimer = 0f
                localAttackTimer = attackVisualDuration
                network.send(GameMessage.AttackHeavy().toBytes())

                super.touchUp(event, x, y, pointer, button)
            }

            //override fun clicked(event: InputEvent, x: Float, y: Float) {
            //    network.send(GameMessage.AttackLight().toBytes())
            //    localAttackTimer = attackVisualDuration
            //}
        })

        stage.addActor(meleeButton)

        // --- PARRY BUTTON ---
        parryButton = TextButton("Parry", skin)
        parryButton.setSize(meleeButtonSize, meleeButtonSize)
        parryButton.setPosition(
            (Gdx.graphics.width * 0.7).toFloat(),
            (Gdx.graphics.height * 0.15).toFloat()
        )

        parryButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                network.send(GameMessage.ParryStart().toBytes())
                isParrying = true
            }
        })

        stage.addActor(parryButton)

        Gdx.input.inputProcessor = stage

        Gdx.app.logLevel = Application.LOG_DEBUG
        // Setup the camera to show a reasonable area
        // Since my tileset is 16px this should show 30x17
        camera.setToOrtho(false, 480f, 270f)
        camera.update()

        // Previously handled joining and UDP here since it was the perfect place
        // With the addition of the loading screen that had to change (now located in main)
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.37f, green = 0.19f, blue = 0.26f)

        // Process any messages from the server
        processServerMessages()

        // Handle input and movement
        if (!gameOver) handleInput(delta)

        // Centre the camera on the player
        camera.position.set(player.X + player.size /2, player.Y + player.size / 2, 0f)
        camera.update()

        // Draw the map
        mapRenderer.setView(camera)
        mapRenderer.render()

        // UI rendering
        stage.act()
        stage.draw()

        // Draw players on top of the map
        player.render(shapeRenderer, camera, delta)

        // Other players in red
        shapeRenderer.color = Color.RED
        for (renderTarget in otherPlayers.values)
        {
            val isFlashing = renderTarget.hitFlashTimer > 0f
            val isSquishing = renderTarget.squishTimer > 0f

            shapeRenderer.color = if (isFlashing) Color.WHITE else Color.RED

            val drawWidth = if (isSquishing) renderTarget.size * 1.4f else renderTarget.size
            val drawHeight = if (isSquishing) renderTarget.size * 0.7f else renderTarget.size

            shapeRenderer.rect(renderTarget.X, renderTarget.Y, drawWidth, drawHeight)
            renderTarget.drawHealthBar(shapeRenderer)

            if (renderTarget.hitFlashTimer > 0f) renderTarget.hitFlashTimer -= delta
            if (renderTarget.squishTimer > 0f) renderTarget.squishTimer -= delta
        }

        // Draw the attack for the local player
        if (localAttackTimer > 0f) {
            shapeRenderer.color = Color(1f, 1f, 0f, 0.4f)
            shapeRenderer.circle(player.X + player.size / 2, player.Y + player.size / 2, attackRadius)
            localAttackTimer -= delta
        }

        // Local heavy windup
        if (localWindupTimer > 0f) {
            shapeRenderer.color = Color.BLUE
            shapeRenderer.circle(player.X + player.size / 2, player.Y + player.size / 2, windupVisualRadius)
            localWindupTimer -= delta
        }

        // Draw attack around player
        shapeRenderer.color = Color.YELLOW

        val toRemove = mutableListOf<Int>()
        for ((id, timer) in attackVisuals)
        {
            val pos = otherPlayers[id]
            if (pos != null)
            {
                shapeRenderer.circle(pos.X + pos.size / 2, pos.Y + pos.size / 2, attackRadius)
                attackVisuals[id] = timer - delta

                if (timer - delta <= 0f) toRemove.add(id)
            }
        }
        toRemove.forEach { attackVisuals.remove(it) }

        // Heavy windup — blue circle on winding up players
        shapeRenderer.color = Color.BLUE
        val windupToRemove = mutableListOf<Int>()
        for ((id, timer) in windupVisuals) {
            val pos = otherPlayers[id]
            if (pos != null) {
                shapeRenderer.circle(pos.X + pos.size / 2, pos.Y + pos.size / 2, windupVisualRadius)
                windupVisuals[id] = timer - delta
                if (timer - delta <= 0f) windupToRemove.add(id)
            }
        }
        windupToRemove.forEach { windupVisuals.remove(it) }

        if (isParrying || parryVisualTimer > 0f) {
            shapeRenderer.color = if (isParrying) Color.CYAN else Color.WHITE
            shapeRenderer.circle(
                player.X + player.size / 2,
                player.Y + player.size / 2,
                18f
            )
            if (parryVisualTimer > 0f) parryVisualTimer -= delta
        }

        shapeRenderer.color = Color.CYAN
        val parryToRemove = mutableListOf<Int>()
        for ((id, timer) in parryVisuals) {
            val pos = otherPlayers[id]
            if (pos != null) {
                shapeRenderer.circle(pos.X + pos.size / 2, pos.Y + pos.size / 2, 18f)
                parryVisuals[id] = timer - delta
                if (timer - delta <= 0f) parryToRemove.add(id)
            }
        }
        parryToRemove.forEach { parryVisuals.remove(it) }

        shapeRenderer.end()
    }

    private fun processServerMessages() {
        for (message in network.getMessages()) {
            when (message) {
                is GameMessage.Move -> {
                    if (message.playerID != network.myId) {
                        otherPlayers[message.playerID.toInt()]?.let {
                            it.X = message.x
                            it.Y = message.y
                        }
                    }
                    else {
                        // server is forcing our position (e.g respawn)
                        player.X = message.x
                        player.Y = message.y
                    }
                }

                is GameMessage.PlayerJoined -> {
                    if (message.playerID != network.myId) {
                        otherPlayers[message.playerID.toInt()] = OtherPlayer(message.x, message.y)
                        println("Player ${message.playerID} joined the game!")
                    }
                }

                is GameMessage.PlayerLeft -> {
                    otherPlayers.remove(message.playerID.toInt())
                    println("Player ${message.playerID} left the game")
                }

                is GameMessage.Welcome -> {
                    // Most already handled in NetworkClient

                    player.ID = network.myId
                    // Tell the server our initial position
                    // If this is not here, the player will not be seen at their spawn point
                    // snapping to their moved position
                    network.send(GameMessage.Move(x = player.X, y = player.Y).toBytes())
                }

                is GameMessage.AttackLight -> {
                    attackVisuals[message.playerID.toInt()] = attackVisualDuration
                }

                is GameMessage.AttackResult -> {
                    // its us
                    if (message.targetID == network.myId)
                    {
                        player.health = message.newHealth
                        player.triggerHit()
                    } else // someone else
                    {
                        // nullable safety: checks the case if the other player happens to not be there
                        otherPlayers[message.targetID.toInt()]?.let {
                            it.health = message.newHealth
                            it.triggerHit()
                        }
                    }
                }

                is GameMessage.AttackHeavyWindup -> {
                    // Show blue windup circle on the attacker
                    windupVisuals[message.playerID.toInt()] = maxWindupDuration
                }

                is GameMessage.AttackHeavy -> {
                    // Flash orange on release
                    attackVisuals[message.playerID.toInt()] = attackVisualDuration
                    windupVisuals.remove(message.playerID.toInt())
                }

                is GameMessage.ParryStart -> {
                    if (message.playerID != network.myId) {
                        parryVisuals[message.playerID.toInt()] = parryVisualDuration
                    }
                }

                is GameMessage.ParryResult -> {
                    if (message.playerID == network.myId) {
                        isParrying = false
                        parryVisualTimer = parryVisualDuration
                        // success = true means they parried successfully, false means window expired
                        if (message.success) {
                            println("Parry successful!")
                        }
                    }
                }

                is GameMessage.ChatMessage -> {
                    chatMessages.add(message.message)
                    if (chatMessages.size > 5) chatMessages.removeAt(0)

                    chatGroup.clear()
                    for (msg in chatMessages) {
                        chatGroup.addActor(
                            com.badlogic.gdx.scenes.scene2d.ui.Label(msg, skin)
                        )
                    }
                }

                is GameMessage.KillUpdate -> {
                    killCounts[message.killerID.toInt()] = message.kills
                    val sb = StringBuilder()
                    for ((id, kills) in killCounts) {
                        sb.appendLine("Player $id: $kills kills")
                    }
                    killLabel.setText(sb.toString())
                }

                is GameMessage.GameOver -> {
                    gameOver = true
                    winnerID = message.winnerID
                    val text = if (message.winnerID == network.myId) "YOU WIN!" else "Player ${message.winnerID} Wins!"
                    gameOverLabel.setText(text)
                    gameOverLabel.isVisible = true
                    restartButton.isVisible = true
                }

                is GameMessage.RestartRequest -> {}

                is GameMessage.GameRestart -> {
                    gameOver = false
                    gameOverLabel.isVisible = false
                    killCounts.clear()
                    killLabel.setText("")
                }
            }
        }
    }

    private fun handleInput(delta: Float)
    {
        player.handleInput(touchpad, delta)
    }

    private fun isWall(x: Float, y: Float): Boolean
    {
        val wallsLayer = map.layers["Walls"] as TiledMapTileLayer

        // Need to convert world position to tile position which in our case is 16px
        // E.g pixel 48 with 16 pixel tiles = tile 3 (using int to round up to tile number)
        val tileX = (x/ 16).toInt()
        val tileY = (y/ 16).toInt()

        // If its outside the bounds of the map treat it like a wall
        if(tileX < 0 || tileX >= wallsLayer.width || tileY < 0 || tileY >= wallsLayer.height)
            return true

        // getCell returns null if there is no tile there
        // if there is a tile on the walls layer its a wall!
        return wallsLayer.getCell(tileX, tileY) != null
    }

    // Im using tile-based detection for walls since box2d would be overkill adding unnecessary overhead
    // when only a simple static binary collision check is needed here
    // Box2d will prove useful in the future with more complexity but walls not require this
    fun collidesWithWall(x: Float, y: Float): Boolean
    {
        // Check all four corners of the player
        // with a small margin (1px) so we don't get stuck on edges
        val margin = 1f
        val left = x + margin
        val right = x + player.size - margin
        val bottom = y + margin
        val top = y + player.size - margin

        return isWall(left, bottom) ||
            isWall(right, bottom) ||
            isWall(left, top) ||
            isWall(right, top)
    }

    override fun dispose() {
        skin.dispose()
        stage.dispose()
        shapeRenderer.disposeSafely()
        map.disposeSafely()
        mapRenderer.disposeSafely()
    }
}
