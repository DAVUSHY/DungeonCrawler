package jackA.MaM.E4048541

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.assets.toInternalFile
import ktx.async.KtxAsync
import ktx.graphics.use

class Main : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate()

        addScreen(FirstScreen())
        setScreen<FirstScreen>()
    }
}

class FirstScreen : KtxScreen {
    private val shapeRenderer = ShapeRenderer()

    // Our player
    private var playerX = 200f
    private var playerY = 200f
    private val playerSize = 50f
    private val playerSpeed = 200f

    // Other players — a map of ID to their position
    private val otherPlayers = mutableMapOf<Int, Pair<Float, Float>>()

    // Network connection — use your computer's local IP here
    // "10.0.2.2" is how the Android emulator refers to your PC's localhost
    private val network = NetworkClient("10.0.2.2", 9999)

    // Track if we've connected yet
    private var connected = false

    override fun show() {
        // show() is called once when this screen becomes active
        // Perfect place to start the connection
        network.connect()
        connected = true
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.1f, green = 0.1f, blue = 0.15f)

        // Process any messages from the server
        processServerMessages()

        // Handle input and movement
        handleInput(delta)

        // Draw everything
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Draw our player in green
        shapeRenderer.color = Color.GREEN
        shapeRenderer.rect(playerX, playerY, playerSize, playerSize)

        // Draw other players in red
        shapeRenderer.color = Color.RED
        for ((_, position) in otherPlayers) {
            shapeRenderer.rect(position.first, position.second, playerSize, playerSize)
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

    private fun handleInput(delta: Float) {
        if (Gdx.input.isTouched) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()

            val dx = touchX - playerX
            val dy = touchY - playerY
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            if (distance > 5f) {
                playerX += (dx / distance) * playerSpeed * delta
                playerY += (dy / distance) * playerSpeed * delta

                // Tell the server where we moved!
                network.send("MOVE:$playerX:$playerY")
            }
        }
    }

    override fun dispose() {
        shapeRenderer.disposeSafely()
    }
}
