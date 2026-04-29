package jackA.MaM.E4048541

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.viewport.ScreenViewport
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.async.KtxAsync

    /*
                            ~Main~

    Registers all screens and handles the connection flow that bridges
    the menu screens and the game screen.

    Screens are registered once and reused
    state reset is handled by each screen's show() method.

    */
class Main : KtxGame<KtxScreen>() {

    override fun create() {
        KtxAsync.initiate()
        addScreen(MenuScreen(this))
        addScreen(ManualJoinScreen(this))
        addScreen(LoadingScreen(this))
        addScreen(FirstScreen(this))
        setScreen<MenuScreen>()
    }

    /*
    Initiates a server connection from a background thread
    Called by MenuScreen (quick join) and ManualJoinScreen (manual join)

    If manualIp is null, UDP broadcast discovery is attempted first
    if failed it falls back to the default IP in NetworkClient

    LibGDX UI updates must happen on the render thread (postRunnable)
    queues status label updates safely from the background thread
    Transitions to FirstScreen once the Welcome message confirms a connection

    manualIp Server IP entered by the user, or null for auto-discovery

     */

    fun startConnection(manualIp: String?) {
        val loadingScreen = getScreen<LoadingScreen>()
        setScreen<LoadingScreen>()

        Thread {
            val network = getScreen<FirstScreen>().network

            if (manualIp != null) {
                Gdx.app.postRunnable { loadingScreen.setStatus("Connecting to $manualIp...") }
                network.host = manualIp
            } else {
                Gdx.app.postRunnable { loadingScreen.setStatus("Searching for server...") }
                val udpClient = UdpClient("255.255.255.255")
                val discovered = udpClient.discoverServer()
                if (discovered != null) {
                    Gdx.app.postRunnable { loadingScreen.setStatus("Server found! Connecting...") }
                    network.host = discovered
                } else {
                    Gdx.app.postRunnable { loadingScreen.setStatus("No server found. Trying default...") }
                }
            }

            network.connect()

            // Poll until Welcome message confirms the server has assigned us an ID
            while (network.myId == (-1).toShort()) {
                Thread.sleep(100)
            }

            Gdx.app.postRunnable { setScreen<FirstScreen>() }
        }.start()
    }
}

    /*
                        ~FirstScreen~

    Intersection of the three core client systems:
        1) GameRenderer: world space ShapeRenderer drawing
        2) UIManager: Scene2D stage, buttons, labels
        3) MessageHandler: incoming network message processing

    All shared mutable state lives in a GameState instance, that is
    passed to each system. FirstScreen itself handles only the
    LibGDX lifecycle (show, render, dispose) and map/camera setup

    */
class FirstScreen(private val game: Main) : KtxScreen {

    //region Network
    /*
        TCP client ~ public so Main.startConnection() can update the host before connecting

        "10.0.2.2" is the Android emulator's alias for the host machine's localhost
        Change to the server machine's LAN IP when testing on a real device
     */
    val network = NetworkClient("10.0.2.2", 9999)

    /*
        UDP client used for outgoing chat messages and LAN server discovery
        Chat is sent over UDP since dropped messages are acceptable and
        latency matters more than guaranteed delivery for chat
    */
    private val udpClient = UdpClient("10.0.2.2")

    //endregion

    //region Map and Camera
    private val map = TmxMapLoader().load("DungeonMap.tmx")
    private val mapRenderer = OrthogonalTiledMapRenderer(map)
    private val camera = OrthographicCamera()
    //endregion

    //region Player
    /*

        The local Player uses method references to avoid
        passing NetworkClient and FirstScreen directly into Player

        Keeping the Player decoupled from the network and screen layers

    */
    private val player = Player(::collidesWithWall, network::send)
    //endregion

    //region Systems
    private val shapeRenderer = ShapeRenderer()
    private val state = GameState()
    private lateinit var skin: Skin
    private lateinit var ui: UIManager
    private lateinit var renderer: GameRenderer
    private lateinit var messageHandler: MessageHandler
    //endregion

    override fun show() {
        // Reset all game state to prevent stale state from previous sessions
        // causing visual bugs like players mirages  or invisible pickups
        state.reset()
        player.health = 100
        player.X = 225f
        player.Y = 200f

        skin = Skin(Gdx.files.internal("clean-crispy/skin/clean-crispy-ui.json"))

        // Initialise the three subsystems, injecting shared dependencies
        ui = UIManager(skin, network, udpClient, player, state, game)
        ui.buildUI()

        renderer = GameRenderer(shapeRenderer, camera, player, state)
        messageHandler = MessageHandler(network, player, state, ui)

        Gdx.input.inputProcessor = ui.stage
        Gdx.app.logLevel = Application.LOG_DEBUG

        // 480x270 viewport shows 30x17 tiles at 16px tile size
        camera.setToOrtho(false, 480f, 270f)
        camera.update()
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.37f, green = 0.19f, blue = 0.26f)

        // Process network messages first so state is up to date before rendering
        messageHandler.process()

        // Input is frozen during game over to prevent actions after the round ends
        if (!state.gameOver) player.handleInput(ui.touchpad, delta)

        camera.position.set(player.X + player.size / 2, player.Y + player.size / 2, 0f)
        camera.update()

        mapRenderer.setView(camera)
        mapRenderer.render()

        // UI renders ontop
        ui.stage.act()
        ui.stage.draw()

        renderer.render(delta)
    }

    //region Wall Collision

    /*
        Tile-based collision is used instead of Box2D since wall geometry
        is static and only needs a binary in/out check. Box2D would
        add significant overhead and complexity for no gameplay benefit here
     */
    private fun isWall(x: Float, y: Float): Boolean {
        val wallsLayer = map.layers["Walls"] as TiledMapTileLayer
        val tileX = (x / 16).toInt()
        val tileY = (y / 16).toInt()

        // Treat outside the map as walls so players cant walk off the map edge
        if (tileX < 0 || tileX >= wallsLayer.width || tileY < 0 || tileY >= wallsLayer.height)
            return true

        return wallsLayer.getCell(tileX, tileY) != null
    }

    /*
        Checks all four corners of the players bounding box against wall tiles

        Axis are checked independently so the player can slide along walls
        rather than stopping dead on contact
     */
    fun collidesWithWall(x: Float, y: Float): Boolean {
        val margin = 1f
        return isWall(x + margin, y + margin) ||
            isWall(x + player.size - margin, y + margin) ||
            isWall(x + margin, y + player.size - margin) ||
            isWall(x + player.size - margin, y + player.size - margin)
    }

    //endregion

    override fun dispose() {
        skin.dispose()
        ui.dispose()
        shapeRenderer.disposeSafely()
        map.disposeSafely()
        mapRenderer.disposeSafely()
    }
}
