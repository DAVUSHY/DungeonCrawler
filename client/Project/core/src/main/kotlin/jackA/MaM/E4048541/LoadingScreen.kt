package jackA.MaM.E4048541

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.utils.viewport.ScreenViewport
import ktx.app.KtxScreen
import ktx.app.clearScreen

class LoadingScreen(private val game: Main) : KtxScreen {
    private lateinit var stage: Stage
    private lateinit var skin: Skin
    private lateinit var statusLabel: Label
    private var timeoutTimer = 0f
    private val timeoutDuration = 10f


    override fun show() {
        skin = Skin(Gdx.files.internal("clean-crispy/skin/clean-crispy-ui.json"))
        stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage
        timeoutTimer = 0f

        val table = Table()
        table.setFillParent(true)
        stage.addActor(table)

        statusLabel = Label("Searching for server...", skin)
        val cancelButton = TextButton("Cancel", skin)

        table.add(statusLabel).padBottom(30f).row()
        table.add(cancelButton).width(150f).height(50f).row()

        cancelButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                cancelConnection()
            }
        })
    }

    fun setStatus(text: String) {
        statusLabel.setText(text)
    }

    private fun cancelConnection() {
        // Reset network state and go back to menu
        game.getScreen<FirstScreen>().network.reset()
        game.setScreen<MenuScreen>()
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.37f, green = 0.19f, blue = 0.26f)

        timeoutTimer += delta
        if (timeoutTimer >= timeoutDuration) {
            setStatus("Connection timed out.")
            // Brief pause to allow the player to read the message then go back
            if (timeoutTimer >= timeoutDuration + 2f) {
                cancelConnection()
            }
        }

        stage.act()
        stage.draw()
    }

    override fun dispose() {
        skin.dispose()
        stage.dispose()
    }
}
