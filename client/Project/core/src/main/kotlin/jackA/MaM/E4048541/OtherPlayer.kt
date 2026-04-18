package jackA.MaM.E4048541

// This class is our players over the network!
// Any logic that belongs to them goes here
class OtherPlayer(x: Float, y: Float) : BasePlayer() {
    // pass the x and y forward from the base player
    // This prevents the networked player from snapping to their position when joining
    init {
        X = x
        Y = y
    }
}
