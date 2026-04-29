package jackA.MaM.E4048541

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

    /*
                          ~GameRenderer~
    Responsible for drawing all players, health bars, combat visuals
    (attack flashes, windup circles, parry indicators), and world pickups

    UI elements (labels, buttons) are handled separately by UIManager
    using Stage/Scene2D which manages its own projection

    */
class GameRenderer(
    private val shapeRenderer: ShapeRenderer,
    private val camera: OrthographicCamera,
    private val player: Player,
    private val state: GameState
) {

    //region Constants
    val attackRadius: Float = 20.0f

    private val windupVisualRadius = 10f

    // Must match the server pickup list
    private val pickupPositions = mapOf(
        0.toShort() to Pair(218f, 152f),
        1.toShort() to Pair(92f, 75f),
        2.toShort() to Pair(400f, 160f),
        3.toShort() to Pair(218f, 310f)
    )
    //endregion

    fun render(delta: Float) {
        drawLocalPlayer(delta)
        drawOtherPlayers(delta)
        drawCombatVisuals(delta)
        drawPickups()
        finishFrame()
    }

    //region Player Drawing

    private fun drawLocalPlayer(delta: Float) {
        player.render(shapeRenderer, camera, delta)
    }

    // Draws all networked (other) players with hit flash and squish effects.
    private fun drawOtherPlayers(delta: Float) {
        for (renderTarget in state.otherPlayers.values) {
            // Flash white briefly on hit, otherwise use team colour (red)
            shapeRenderer.color = if (renderTarget.hitFlashTimer > 0f) Color.WHITE else Color.RED

            // Squish effect: wider and shorter briefly on hit
            val drawWidth = if (renderTarget.squishTimer > 0f) renderTarget.size * 1.4f else renderTarget.size
            val drawHeight = if (renderTarget.squishTimer > 0f) renderTarget.size * 0.7f else renderTarget.size

            shapeRenderer.rect(renderTarget.X, renderTarget.Y, drawWidth, drawHeight)
            renderTarget.drawHealthBar(shapeRenderer)

            if (renderTarget.hitFlashTimer > 0f) renderTarget.hitFlashTimer -= delta
            if (renderTarget.squishTimer > 0f) renderTarget.squishTimer -= delta
        }
    }

    //endregion

    //region Combat Visuals

    private fun drawCombatVisuals(delta: Float) {
        drawLocalAttackVisual(delta)
        drawLocalWindupVisual(delta)
        drawRemoteAttackVisuals(delta)
        drawRemoteWindupVisuals(delta)
        drawParryVisuals(delta)
        drawRemoteParryVisuals(delta)
    }

    private fun drawLocalAttackVisual(delta: Float) {
        if (state.localAttackTimer > 0f) {
            // Experimenting with trying to alpha on the attacks
            // so that you can see whats going on underneath (this method proved to not work)
            // Going to leave it in as I think I was getting close to figuring out a openGL workaround
            shapeRenderer.color = Color(1f, 1f, 0f, 0.4f)
            shapeRenderer.circle(player.X + player.size / 2, player.Y + player.size / 2, attackRadius)
            state.localAttackTimer -= delta
        }
    }

    private fun drawLocalWindupVisual(delta: Float) {
        if (state.localWindupTimer > 0f) {
            shapeRenderer.color = Color.BLUE
            shapeRenderer.circle(player.X + player.size / 2, player.Y + player.size / 2, windupVisualRadius)
            state.localWindupTimer -= delta
        }
    }

    private fun drawRemoteAttackVisuals(delta: Float) {
        shapeRenderer.color = Color.YELLOW
        val toRemove = mutableListOf<Int>()
        for ((id, timer) in state.attackVisuals) {
            val pos = state.otherPlayers[id] ?: continue
            shapeRenderer.circle(pos.X + pos.size / 2, pos.Y + pos.size / 2, attackRadius)
            state.attackVisuals[id] = timer - delta
            if (timer - delta <= 0f) toRemove.add(id)
        }
        toRemove.forEach { state.attackVisuals.remove(it) }
    }

    private fun drawRemoteWindupVisuals(delta: Float) {
        shapeRenderer.color = Color.BLUE
        val toRemove = mutableListOf<Int>()
        for ((id, timer) in state.windupVisuals) {
            val pos = state.otherPlayers[id] ?: continue
            shapeRenderer.circle(pos.X + pos.size / 2, pos.Y + pos.size / 2, windupVisualRadius)
            state.windupVisuals[id] = timer - delta
            if (timer - delta <= 0f) toRemove.add(id)
        }
        toRemove.forEach { state.windupVisuals.remove(it) }
    }

    // Cyan while the parry window is active, briefly white when it resolves
    private fun drawParryVisuals(delta: Float) {
        if (state.isParrying || state.parryVisualTimer > 0f) {
            shapeRenderer.color = if (state.isParrying) Color.CYAN else Color.WHITE
            shapeRenderer.circle(player.X + player.size / 2, player.Y + player.size / 2, 18f)
            if (state.parryVisualTimer > 0f) state.parryVisualTimer -= delta
        }
    }

    private fun drawRemoteParryVisuals(delta: Float) {
        shapeRenderer.color = Color.CYAN
        val toRemove = mutableListOf<Int>()
        for ((id, timer) in state.parryVisuals) {
            val pos = state.otherPlayers[id] ?: continue
            shapeRenderer.circle(pos.X + pos.size / 2, pos.Y + pos.size / 2, 18f)
            state.parryVisuals[id] = timer - delta
            if (timer - delta <= 0f) toRemove.add(id)
        }
        toRemove.forEach { state.parryVisuals.remove(it) }
    }

    //endregion

    //region World Objects

    // Healthpacks as crosses
    private fun drawPickups() {
        shapeRenderer.color = Color.GREEN
        for ((id, active) in state.pickupStates) {
            if (!active) continue
            val pickup = pickupPositions[id] ?: continue
            // Draw a cross shape using two overlapping rectangles
            shapeRenderer.rect(pickup.first - 1f, pickup.second - 5f, 3f, 10f)
            shapeRenderer.rect(pickup.first - 5f, pickup.second - 1f, 10f, 3f)
        }
    }

    //endregion

    private fun finishFrame() {
        shapeRenderer.end()
    }
}
