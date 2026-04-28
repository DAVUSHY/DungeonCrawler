package jackA.MaM.E4048541

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import ktx.app.KtxScreen
import ktx.app.clearScreen

class ManualJoinScreen(private val game: Main) : KtxScreen {
    private lateinit var stage: Stage
    private lateinit var skin: Skin

    override fun show() {
        skin = Skin(Gdx.files.internal("clean-crispy/skin/clean-crispy-ui.json"))
        stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage

        val table = Table()
        table.setFillParent(true)
        stage.addActor(table)

        val ipLabel = Label("Server IP:", skin)
        val ipField = TextField("", skin)
        val portLabel = Label("Port:", skin)
        val portField = TextField("9999", skin)
        val connectButton = TextButton("Connect", skin)
        val backButton = TextButton("Back", skin)

        table.add(ipLabel).padBottom(10f).row()
        table.add(ipField).width(300f).height(50f).padBottom(20f).row()
        table.add(portLabel).padBottom(10f).row()
        table.add(portField).width(300f).height(50f).padBottom(20f).row()
        table.add(connectButton).width(200f).height(60f).padBottom(20f).row()
        table.add(backButton).width(200f).height(60f).row()

        connectButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                val ip = ipField.text.trim()
                val port = portField.text.trim()
                if (ip.isNotEmpty()) {
                    game.startConnection(ip)
                }
            }
        })

        backButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                game.setScreen<MenuScreen>()
            }
        })
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.37f, green = 0.19f, blue = 0.26f)
        stage.act()
        stage.draw()
    }

    override fun dispose() {
        skin.dispose()
        stage.dispose()
    }
}
