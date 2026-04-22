package jackA.MaM.E4048541

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.viewport.ScreenViewport
import ktx.app.KtxScreen
import ktx.app.clearScreen

class LoadingScreen(private val game: Main) : KtxScreen {
    private lateinit var stage: Stage
    private lateinit var skin: Skin
    private lateinit var statusLabel: Label

    override fun show() {
        skin = Skin(Gdx.files.internal("clean-crispy/skin/clean-crispy-ui.json"))
        stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage

        statusLabel = com.badlogic.gdx.scenes.scene2d.ui.Label("Searching for server...", skin)
        statusLabel.setPosition(
            (Gdx.graphics.width / 2f) - 120f,
            (Gdx.graphics.height / 2f)
        )
        stage.addActor(statusLabel)
    }

    fun setStatus(text: String) {
        statusLabel.setText(text)
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
