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
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
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

        addScreen(FirstScreen())
        setScreen<FirstScreen>()
    }
}

class FirstScreen : KtxScreen {
    private val shapeRenderer = ShapeRenderer()

    // Network connection — use your computer's local IP here
    // "10.0.2.2" is how the Android emulator refers to your PC's localhost
    private val network = NetworkClient("10.0.2.2", 9999)

    // Map renderer
    private val map = TmxMapLoader().load("DungeonCrawlerMap.tmx")
    private val mapRenderer = OrthogonalTiledMapRenderer(map)

    // Camera - controlling which part of the map we are looking at
    private val camera = OrthographicCamera()

    private val player = Player(
        ::collidesWithWall,
        network::send
    )

    // UI
    private lateinit var skin: Skin
    private lateinit var stage: Stage
    private lateinit var touchpad: Touchpad
    private var touchpadX: Float = (Gdx.graphics.width * 0.1).toFloat()
    private var touchpadY: Float = (Gdx.graphics.height * 0.15).toFloat()
    private var touchpadSize = 200f

    private lateinit var meleeButton: TextButton
    private var meleeButtonX: Float = (Gdx.graphics.width * 0.9).toFloat()
    private var meleeButtonY: Float = (Gdx.graphics.height * 0.15).toFloat()
    private var meleeButtonSize = 100f

    // Other players — a map of ID to their position
    private val otherPlayers = mutableMapOf<Int, Pair<Float, Float>>()

    // Track if we've connected yet
    private var connected = false

    override fun show() {
        // boilerplate for setting up skin and stage
        skin = Skin(Gdx.files.internal("clean-crispy/skin/clean-crispy-ui.json"))

        stage = Stage(ScreenViewport())

        //Begin layout
        touchpad = Touchpad(20f, skin)
        touchpad.setSize(touchpadSize, touchpadSize)
        touchpad.setPosition(touchpadX, touchpadY)

        stage.addActor(touchpad)

        meleeButton = TextButton("Hello", skin)
        meleeButton.setSize(meleeButtonSize, meleeButtonSize)
        meleeButton.setPosition(meleeButtonX, meleeButtonY)

        meleeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                network.send(GameMessage.AttackMelee().toBytes())
            }
        })

        stage.addActor(meleeButton)

        Gdx.input.inputProcessor = stage

        Gdx.app.logLevel = Application.LOG_DEBUG
        // Setup the camera to show a reasonable area
        // Since my tileset is 16px this should show 30x17
        camera.setToOrtho(false, 480f, 270f)
        camera.update()

        // show() is called once when this screen becomes active
        // Perfect place to start the connection
        network.connect()
        connected = true

        player.ID = network.myId
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.37f, green = 0.19f, blue = 0.26f)

        // Process any messages from the server
        processServerMessages()

        // Handle input and movement
        handleInput(delta)

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
        player.render(shapeRenderer, camera)

        // Other players in red
        shapeRenderer.color = Color.RED
        for ((_, position) in otherPlayers) {
            shapeRenderer.rect(position.first, position.second, player.size, player.size)
        }

        shapeRenderer.end()
    }

    private fun processServerMessages() {
        for (message in network.getMessages()) {
            when (message) {
                is GameMessage.Move -> {
                    if (message.playerID != network.myId) {
                        otherPlayers[message.playerID.toInt()] = Pair(message.x, message.y)
                    }
                }

                is GameMessage.PlayerJoined -> {
                    if (message.playerID != network.myId) {
                        otherPlayers[message.playerID.toInt()] = Pair(message.x, message.y)
                        println("Player ${message.playerID} joined the game!")
                    }
                }

                is GameMessage.PlayerLeft -> {
                    otherPlayers.remove(message.playerID.toInt())
                    println("Player ${message.playerID} left the game")
                }

                is GameMessage.Welcome -> {
                    // Already handled in NetworkClient
                }

                is GameMessage.AttackMelee -> {
                    // TODO: show attack visual for this player
                }

                is GameMessage.AttackResult -> {
                    // TODO: implement data for attack
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
