package jackA.MaM.E4048541

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

// superclass - kotlins version of parent
open class BasePlayer {
    // Our player
    var ID : Short = 0 // Defaults to 0 gets set when player joins

    var X : Float = 225f
    var Y : Float = 200f
    val size = 12f
    val speed = 75f

    var health: Int = 100

    // Visual Feedback
    var hitFlashTimer: Float = 0f
    val hitFlashDuration: Float = 0.15f
    var squishTimer: Float = 0f
    val squishDuration: Float = 0.1f

    fun drawHealthBar(shapeRenderer: ShapeRenderer) {
        val barWidth = 20f
        val barHeight = 3f
        val barYOffset = 4f
        val barX = X
        val barY = Y + size + barYOffset

        val healthPercent = health / 100f

        // Red background bar
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(barX, barY, barWidth, barHeight)

        // Green fill bar scaled to current health
        shapeRenderer.color = Color.GREEN
        shapeRenderer.rect(barX, barY, barWidth * healthPercent, barHeight)
    }

    fun render(shapeRenderer : ShapeRenderer, camera : OrthographicCamera, delta: Float)
    {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Our player in green
        shapeRenderer.color = if (hitFlashTimer > 0f) Color.WHITE else Color.GREEN
        shapeRenderer.rect(X, Y, size, size)

        // Draw our players healthbar
        // other player healthbar gets drawn in the render loop
        drawHealthBar(shapeRenderer)

        if (hitFlashTimer > 0f) hitFlashTimer -= delta
    }

    fun triggerHit() {
        hitFlashTimer = hitFlashDuration
        squishTimer = squishDuration
    }
}
