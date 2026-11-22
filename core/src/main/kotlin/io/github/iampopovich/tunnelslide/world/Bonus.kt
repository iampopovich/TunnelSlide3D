package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.graphics.Color

/** Bonus fields lying on the track (see docs: SPEC "Бонусы"). */
enum class BonusKind(val label: String, val color: Color) {
    /** Orange: one extra hit; used automatically when you crash into something. */
    SHIELD("SHIELD", Color(1.00f, 0.55f, 0.10f, 1f)),
    /** Red: destroys every obstacle in sight. Stored in the slot, used by tap. */
    QUAKE("QUAKE", Color(1.00f, 0.15f, 0.15f, 1f)),
    /** Blue: slows the whole game down for a while. Stored in the slot, used by tap. */
    SLOWMO("TIME", Color(0.25f, 0.55f, 1.00f, 1f)),
    /** Green: ends the current flat / outside episode (and hazard zones later). Slot, tap. */
    FOLD("FOLD", Color(0.25f, 1.00f, 0.45f, 1f)),
}

/** A bonus field on the surface; [u] is its centre, [z] its near edge. */
class Pickup(val u: Float, val z: Float, val kind: BonusKind) {
    var taken = false
}
