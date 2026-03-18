package jackA.MaM.E4048541

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
import kotlin.div
import kotlin.math.sqrt
import kotlin.text.toDouble
import kotlin.times

// There should only be one of these players per client, since this is the player the client controls
class Player(
    // I can use method references to "forward" a function to a class, in this case its super handy
    // for avoiding having to pass through the first class and the network to the player class
    // Method reference to check wall collisions
    private val collisionCheck: (Float, Float) -> Boolean,
    // Method reference to send network messages
    private val sendNetworkMessage: (String) -> Unit
) : BasePlayer()
{
    fun handleInput(touchpad: Touchpad, delta: Float)
    {
        if (touchpad.isTouched)
        {
            val touchX = touchpad.knobPercentX.toFloat()
            val touchY = touchpad.knobPercentY.toFloat()

            val dx = touchX * speed
            val dy = touchY * speed
            val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            if (distance > 5f) {
                // Calculate how much we WANT to move
                val moveX = (dx / distance) * speed * delta
                val moveY = (dy / distance) * speed * delta

                // Try X movement separately
                if (!collisionCheck(X + moveX, Y))
                    X += moveX

                // Try Y movement separately
                if (!collisionCheck(X, Y + moveY))
                    Y += moveY

                sendNetworkMessage("MOVE:$X:$Y")
            }
        }
    }
}
