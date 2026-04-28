package jackA.MaM.E4048541

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import ktx.app.KtxScreen
import ktx.app.clearScreen

class MenuScreen(private val game: Main) : KtxScreen {
    private lateinit var stage: Stage
    private lateinit var skin: Skin

    override fun show() {
        skin = Skin(Gdx.files.internal("clean-crispy/skin/clean-crispy-ui.json"))
        stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage

        // Table is the cleanest way to lay out menu UI in LibGDX
        // it handles positioning and padding automatically
        val table = Table()
        table.setFillParent(true)
        stage.addActor(table)

        val titleLabel = Label("Dungeon Crawler", skin)
        val quickJoinButton = TextButton("Quick Join", skin)
        val manualJoinButton = TextButton("Manual Join", skin)
        val quitButton = TextButton("Quit", skin)

        table.add(titleLabel).padBottom(40f).row()
        table.add(quickJoinButton).width(200f).height(60f).padBottom(20f).row()
        table.add(manualJoinButton).width(200f).height(60f).padBottom(20f).row()
        table.add(quitButton).width(200f).height(60f).row()

        quickJoinButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                game.startConnection(null)
            }
        })

        manualJoinButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                game.setScreen<ManualJoinScreen>()
            }
        })

        quitButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                Gdx.app.exit()
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
