import android.graphics.Canvas
import android.graphics.Typeface
import com.pranvir.lamplighter.*
import java.util.Random

fun consistent(g: Game): String? {
    val f = g.flags
    fun x(a: Boolean, b: Boolean) = a != b
    if (f[F.GOT_OIL] && !x(g.has(Icon.OILCAN), f[F.DOOR_OPEN])) return "oil"
    if (f[F.GOT_COIN] && !x(g.has(Icon.COIN), f[F.COIN_GIVEN])) return "coin"
    if (f[F.COIN_GIVEN] && !f[F.GOT_VALVE]) return "valve lost"
    if (f[F.GOT_FISH] && !x(g.has(Icon.FISH), f[F.FISH_GIVEN])) return "fish"
    if (f[F.FISH_GIVEN] && !f[F.GOT_GEAR]) return "gear lost"
    if (f[F.GOT_GEAR] && !x(g.has(Icon.GEAR), f[F.BRIDGE_DOWN])) return "gear"
    if (f[F.GOT_LENS] && !x(g.has(Icon.LENS), f[F.LENS_IN])) return "lens"
    if (f[F.GOT_KEY] && !x(g.has(Icon.KEY), f[F.TOWER_OPEN])) return "key"
    return null
}

fun monkeyRound(seed: Long, frames: Int, start: String? = null): String {
    val rnd = Random(seed)
    val host = FakeHost()
    if (start != null) host.prefs[Game.SAVE_KEY] = start
    var g = newGame(host)
    var busyFor = 0f
    var downNow = false
    val o = FloatArray(2)
    for (f in 0 until frames) {
        val dt = when (rnd.nextInt(30)) { 0 -> 0.25f; 1 -> 0f; else -> 1f / (30 + rnd.nextInt(90)) }
        val roll = rnd.nextInt(100)
        when {
            g.mode == Game.MODE_TITLE && roll < 20 -> { if (g.debugBtn(Game.B_PLAY, o)) tapS(g, o[0], o[1]) }
            g.mode == Game.MODE_ENDING && roll < 5 -> tapS(g, 1000f, 500f)
            roll < 12 && g.mode == Game.MODE_PLAY && g.mini == null && !g.debugBusy -> {
                // tap a random visible hotspot (drives the story forward)
                val hs = g.scene.hotspots
                val h = hs[rnd.nextInt(hs.size)]
                if (h.visible()) tapW(g, h.cx.coerceIn(-150f, 1750f), h.cy.coerceIn(20f, 880f))
            }
            roll < 18 && g.mode == Game.MODE_PLAY && g.mini == null && !g.debugBusy && g.inv.isNotEmpty() -> {
                val item = g.inv[rnd.nextInt(g.inv.size)]
                val hs = g.scene.hotspots
                val h = hs[rnd.nextInt(hs.size)]
                if (h.visible() && g.debugInvSlotCenter(item, o)) { tapS(g, o[0], o[1]); tapW(g, h.cx.coerceIn(-150f, 1750f), h.cy.coerceIn(20f, 880f)) }
            }
            roll < 40 -> {
                val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H
                if (!downNow) { g.touchDown(x, y); downNow = true } else { g.touchUp(x, y); downNow = false }
            }
            roll < 55 -> if (downNow) g.touchMove(rnd.nextFloat() * W, rnd.nextFloat() * H)
            roll < 57 -> if (rnd.nextInt(8) == 0) g.onBack()
            roll < 58 -> if (rnd.nextInt(10) == 0) { g.onPause(); if (g.debugBtn(Game.B_RESUME, o)) tapS(g, o[0], o[1]) }
            roll < 59 -> if (rnd.nextInt(60) == 0) {
                g.resize(if (rnd.nextBoolean()) 1600 else 2800, if (rnd.nextBoolean()) 720 else 1440, 2f + rnd.nextFloat())
                g.setInsets(rnd.nextInt(150), rnd.nextInt(80), rnd.nextInt(150), rnd.nextInt(80))
            }
            roll < 60 -> if (rnd.nextInt(40) == 0) { g.touchCancel(); downNow = false }
            roll < 61 -> if (rnd.nextInt(60) == 0) {
                // simulate process death + restore
                g.onPause(); g = newGame(host); downNow = false
                consistent(g)?.let { throw AssertionError("restored save inconsistent: $it") }
            }
            roll < 62 && g.mini != null && rnd.nextInt(6) == 0 -> {
                val m = g.mini!!
                // GearsMini needs the large gear to be solvable for real
                if (m !is GearsMini || g.has(Icon.GEAR)) m.debugSolve()
            }
            roll < 70 && g.mini != null -> {
                // play with the machine: circular drags around the middle of the screen
                val cx = W / 2f + (rnd.nextFloat() - 0.5f) * W * 0.6f; val cy = H / 2f + (rnd.nextFloat() - 0.5f) * H * 0.6f
                g.touchDown(cx, cy); downNow = true
                var a = 0f
                repeat(10) { a += 0.3f; g.touchMove(cx + kotlin.math.cos(a) * 120f, cy + kotlin.math.sin(a) * 120f); g.update(1 / 60f) }
                g.touchUp(cx, cy); downNow = false
            }
        }
        g.update(dt)
        if (f % 4 == 0) { val c = Canvas(tiny); g.draw(c); check(c.saveCount == 1) { "unbalanced canvas" } }
        // invariants
        val fl = g.flags
        check(g.inv.size == g.inv.toSet().size) { "duplicate items ${g.inv}" }
        if (fl[F.LAMP2]) check(fl[F.BRIDGE_DOWN]) { "lamp2 w/o bridge" }
        if (fl[F.LAMP1]) check(fl[F.GAS_ON]) { "lamp1 w/o gas" }
        if (fl[F.LAMP3]) check(fl[F.LENS_IN])
        if (fl[F.LAMP4]) check(fl[F.CLOCK_RUN])
        if (fl[F.GAS_ON]) check(!g.inv.contains(Icon.VALVE))
        if (fl[F.DOOR_OPEN]) check(!g.inv.contains(Icon.OILCAN))
        if (fl[F.BRIDGE_DOWN]) check(!g.inv.contains(Icon.GEAR)) { "gear kept after bridge" }
        check(!g.wick.x.isNaN() && g.wick.x in -10f..1700f) { "wick x ${g.wick.x}" }
        if (g.mode == Game.MODE_PLAY && g.debugBusy && g.mini == null && !g.debugPaused()) busyFor += dt else busyFor = 0f
        if (!g.debugBusy) consistent(g)?.let { throw AssertionError("live state inconsistent: $it") }
        check(busyFor < 40f) { "stuck busy in scene ${g.sceneIdx} acts=${g.debugActs}" }
    }
    val lamps = g.lampsLit()
    return "seed $seed: scene=${g.sceneIdx} lamps=$lamps flags=${g.flags.count { it }} inv=${g.inv} ending=${g.flags[F.ENDING_SEEN]} sounds=${host.sounds}"
}

fun main(args: Array<String>) {
    Fonts.title = Typeface.createFromFile("/home/claude/tc/fell.ttf")
    val rounds = args.getOrNull(0)?.toInt() ?: 8
    val frames = args.getOrNull(1)?.toInt() ?: 40000
    for (r in 0 until rounds) println(monkeyRound(1000L + r, frames))
    val snaps = java.io.File("/home/claude/lamplighter/build/snapshots.txt").readLines().filter { it.isNotBlank() }
    for ((i, sv) in snaps.withIndex()) println("from snapshot $i -> " + monkeyRound(5000L + i, frames, sv))
    println("MONKEY OK")
}
