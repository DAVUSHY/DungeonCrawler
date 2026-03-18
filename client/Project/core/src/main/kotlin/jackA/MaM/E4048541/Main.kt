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
import jackA.MaM.E4048541.GameMessage.Companion.BASE_MESSAGE_SIZE
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.async.KtxAsync
import java.nio.ByteBuffer
import kotlin.math.sqrt

enum class GameAction(val id: Byte) {
    MOVE(1), ATTACK_MELEE(2);

    companion object {
        fun fromId(id: Byte) = entries.first { it.id == id }
    }
}

data class GameMessage(
    val playerID: String,
    val x: Float,
    val y: Float,
    val action: GameAction)
{
    // Convenient toString() for printing to terminal
    override fun toString() =
        with(StringBuilder()) {
            append("\tPLAYER ID: $playerID\n")
            append("\tPosition: ($x, $y)\n")
            append("\tAction: $action")
            toString()
        }

    companion object {
        const val BASE_MESSAGE_SIZE =
            (2 * Float.SIZE_BYTES) +
                (2 * Byte.SIZE_BYTES) + 1
    }
}

// This function converts GameMessages into bytes for sending across the network
fun GameMessage.toByteArray(): ByteArray =
    with(playerID.toByteArray(Charsets.UTF_8)) {
        ByteBuffer
            .allocate( size + BASE_MESSAGE_SIZE)
            .apply {
                // "put" is a relative method, which writes the byte given to it into this buffer
                // it will be "put" at the current position and the position in the buffer is
                // incremented for the next byte to be placed at!
                put(size.toByte()) // 1 byte
                put(this@with) // n-bytes
                putFloat(x) // 4 bytes
                putFloat(y) // 4 bytes
                put(action.id) // 1 byte
            }.array()
    }

fun GameMessage.Companion.buildFromBuiltArray(byteArray: ByteArray) =
    // ByteBuffer.wrap(byteArray) -> wraps the passed through byteArray into a buffer.
    // The new buffer's capacity and limit will be array.length
    // This makes it so that any alterations to this byteBuffer will now affect the array
    // and vice versa. Additionally, its byte order will be Big Endian.
    with(ByteBuffer.wrap(byteArray)){
        // "get()" (similar to put) reads the byte at the current position and increments the position
        // Read the byte length of the PlayerID, this tells us how long the playersID is!
        // With this information, we can tell the buffer to "skip" the first X amount of bytes
        // If the ID was "John", get would read the first byte see its 4 bytes long
        val playerIDLength = get().toInt()
        val rawPlayerID = ByteArray(playerIDLength)

        // grabs specific bytes and converts them to UTF-8 String (computer to human)
        get(rawPlayerID)
        val playerID = String(rawPlayerID, Charsets.UTF_8)

        val x = getFloat() // can also use shorthand -> float
        val y = getFloat()

        // Read the GameAction - single byte
        val action = GameAction.fromId(get())

        GameMessage(playerID, x, y, action)
    }

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
                network.send("ATTACK_MELEE")
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
            val parts = message.split(":")

            when (parts[0]) {
                "MOVE" -> {
                    val id = parts[1].toInt()
                    val x = parts[2].toFloat()
                    val y = parts[3].toFloat()

                    // Don't update ourselves from the server — we already know where we are
                    if (id != network.myId) {
                        otherPlayers[id] = Pair(x, y)
                    }
                }

                "PLAYER_JOINED" -> {
                    val id = parts[1].toInt()
                    val x = parts[2].toFloat()
                    val y = parts[3].toFloat()

                    if (id != network.myId) {
                        otherPlayers[id] = Pair(x, y)
                        println("Player $id joined the game!")
                    }
                }

                "PLAYER_LEFT" -> {
                    val id = parts[1].toInt()
                    otherPlayers.remove(id)
                    println("Player $id left the game")
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
