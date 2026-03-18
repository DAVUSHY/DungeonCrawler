package jackA.MaM.E4048541

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

// superclass - kotlins version of parent
open class BasePlayer {
    // Our player
    var ID : Int = 0 // Defaults to 0 gets set when player joins

    var X : Float = 200f
    var Y : Float = 200f
    val size = 12f
    val speed = 75f

    fun render(shapeRenderer : ShapeRenderer, camera : OrthographicCamera)
    {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Our player in green
        shapeRenderer.color = Color.GREEN
        shapeRenderer.rect(X, Y, size, size)
    }
}
