import android.graphics.Canvas
import android.graphics.Typeface
import com.pranvir.lamplighter.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin

class FakeHost : Host {
    val prefs = HashMap<String, Any?>()
    var sounds = 0
    val log = IntArray(Sfx.COUNT)
    var musicLayers = -1; var amb = -1
    override fun loadString(key: String) = prefs[key] as? String
    override fun saveString(key: String, v: String?) { prefs[key] = v }
    override fun loadInt(key: String, def: Int) = (prefs[key] as? Int) ?: def
    override fun saveInt(key: String, v: Int) { prefs[key] = v }
    override fun sound(id: Int, vol: Float, rate: Float) {
        check(id in 0 until Sfx.COUNT) { "bad sfx $id" }; check(vol in 0f..1f); check(rate in 0.5f..2f)
        sounds++; log[id]++
    }
    override fun setAudio(soundOn: Boolean, musicOn: Boolean) {}
    override fun setMusicState(layers: Int, ambient: Int) { musicLayers = layers; amb = ambient }
    override fun haptic(strong: Boolean) {}
}

const val W = 2400
const val H = 1080
val shots = File("/home/claude/lamplighter/shots").apply { mkdirs() }
var frames = 0L
val tiny = BufferedImage(600, 270, BufferedImage.TYPE_INT_ARGB)

fun tick(g: Game, dt: Float = 1f / 60f) {
    g.update(dt); frames++
    if (frames % 6 == 0L) { val c = Canvas(tiny); g.draw(c); check(c.saveCount == 1) { "unbalanced canvas" } }
}
fun ticks(g: Game, s: Float) = repeat((s * 60).toInt().coerceAtLeast(1)) { tick(g) }
fun shot(g: Game, name: String) {
    val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val c = Canvas(img); g.draw(c); check(c.saveCount == 1)
    val small = BufferedImage(W / 2, H / 2, BufferedImage.TYPE_INT_RGB)
    val g2 = small.createGraphics()
    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g2.drawImage(img, 0, 0, W / 2, H / 2, null)
    ImageIO.write(small, "png", File(shots, "$name.png"))
}
fun tapS(g: Game, x: Float, y: Float) { g.touchDown(x, y); tick(g); g.touchUp(x, y); tick(g) }
fun tapW(g: Game, x: Float, y: Float) = tapS(g, g.toScreenX(x), g.toScreenY(y))
val snapshots = ArrayList<String>()
fun snap(host: FakeHost) { (host.prefs[Game.SAVE_KEY] as? String)?.let { snapshots.add(it) } }
fun idle(g: Game, max: Float = 30f) {
    var t = 0f
    while ((g.debugBusy || g.debugFade != 0) && (g.mini == null || g.mini!!.solved)) { tick(g); t += 1f / 60f; check(t < max) { "stuck busy (scene ${g.sceneIdx})" } }
    ticks(g, 0.1f)
}
fun btn(g: Game, id: Int) { val o = FloatArray(2); check(g.debugBtn(id, o)) { "btn $id" }; tapS(g, o[0], o[1]) }
fun useItem(g: Game, item: Int, x: Float, y: Float) {
    val o = FloatArray(2); check(g.debugInvSlotCenter(item, o)) { "no item $item" }
    tapS(g, o[0], o[1]); check(g.selected == item) { "item not selected" }
    tapW(g, x, y)
}
fun drag(g: Game, x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 10) {
    g.touchDown(x0, y0); tick(g)
    for (k in 1..steps) { g.touchMove(x0 + (x1 - x0) * k / steps, y0 + (y1 - y0) * k / steps); tick(g) }
    g.touchUp(x1, y1); tick(g)
}
fun waitMini(g: Game, max: Float = 15f) { var t = 0f; while (g.mini == null) { tick(g); t += 1f / 60f; check(t < max) { "mini never opened" } } ; ticks(g, 0.4f) }
fun catchFireflies(g: Game) {
    val o = FloatArray(2)
    for (k in 0 until 3) {
        idle(g)
        g.scene.ffPos(k, g.time + 1f / 60f, o)
        tapW(g, o[0], o[1])
        ticks(g, 0.2f)
    }
}
fun newGame(host: FakeHost): Game {
    val g = Game(host); g.resize(W, H, 2.75f); g.setInsets(90, 0, 60, 0); return g
}

fun playMain() {
    Fonts.title = Typeface.createFromFile("/home/claude/tc/fell.ttf")
    val host = FakeHost()
    var g = newGame(host)
    ticks(g, 1f); shot(g, "00_title")
    btn(g, Game.B_PLAY); ticks(g, 1f)
    check(g.mode == Game.MODE_PLAY && g.sceneIdx == 0)
    shot(g, "01_workshop_sleep")
    tapW(g, 800f, 500f); ticks(g, 2.5f); shot(g, "02_wake_bubble"); idle(g)
    check(g.flag(F.AWAKE))
    catchFireflies(g)
    tapW(g, 240f, 500f); idle(g); check(g.flag(F.GOT_POLE))
    tapW(g, 720f, 280f); ticks(g, 1.6f); shot(g, "03_think_stool"); idle(g); check(!g.flag(F.GOT_OIL))
    tapW(g, 1060f, 720f); ticks(g, 1.2f); shot(g, "04_push_stool"); idle(g); check(g.flag(F.STOOL_MOVED))
    tapW(g, 720f, 280f); idle(g); check(g.has(Icon.OILCAN))
    tapW(g, 1450f, 600f); idle(g); check(!g.flag(F.DOOR_OPEN))
    // wrong item on something -> shake
    useItem(g, Icon.OILCAN, 240f, 500f); idle(g); check(g.has(Icon.OILCAN))
    useItem(g, Icon.OILCAN, 1450f, 600f); ticks(g, 2f); shot(g, "05_door_oil"); idle(g); check(g.flag(F.DOOR_OPEN))
    // hint bubble
    tapW(g, g.wick.x, 650f); ticks(g, 0.8f); shot(g, "06_hint"); idle(g)
    tapW(g, 1450f, 600f); idle(g); check(g.sceneIdx == 1) { "not in market" }
    shot(g, "10_market_dark"); snap(host)
    catchFireflies(g)
    // --- market
    tapW(g, 1170f, 400f); ticks(g, 1.8f); shot(g, "11_crow_wants_coin"); idle(g)
    tapW(g, 420f, 700f); idle(g); check(g.has(Icon.COIN))
    useItem(g, Icon.COIN, 1170f, 400f); ticks(g, 2.4f); shot(g, "12_crow_swoop"); idle(g); check(g.has(Icon.VALVE))
    tapW(g, 820f, 500f); idle(g)
    useItem(g, Icon.VALVE, 820f, 500f); waitMini(g)
    val pm = g.mini as PressureMini
    shot(g, "13_pressure")
    // steer the valve: keep the needle in the band
    val c = FloatArray(2); pm.wheel(c); val r = pm.wheelRadius()
    var a = -1.5f
    g.touchDown(c[0] + cos(a) * r, c[1] + sin(a) * r); tick(g)
    var t = 0f
    var shotTaken = false
    while (!pm.solved) {
        val p = pm.pressure
        val d = when { p < 62f -> 0.05f; p > 70f -> -0.05f; else -> 0f }
        a += d
        g.touchMove(c[0] + cos(a) * r, c[1] + sin(a) * r); tick(g)
        t += 1f / 60f
        if (!shotTaken && t > 2f) { shot(g, "14_pressure_turning"); shotTaken = true }
        check(t < 40f) { "could not hold pressure (p=$p)" }
    }
    g.touchUp(c[0], c[1]); tick(g)
    println("pressure solved in ${"%.1f".format(t)}s")
    idle(g); ticks(g, 1f)
    check(g.flag(F.LAMP1)) { "lamp1 not lit" }
    ticks(g, 1.5f); shot(g, "15_market_lit"); snap(host)
    tapW(g, 1430f, 600f); idle(g); check(g.has(Icon.FISH))
    tapW(g, 1560f, 600f); idle(g); check(g.sceneIdx == 2) { "not in canal" }
    shot(g, "20_canal_dark"); snap(host)
    catchFireflies(g)
    // --- canal
    tapW(g, 870f, 680f); waitMini(g)
    var gm = g.mini as GearsMini
    shot(g, "21_gears_missing")
    val o = FloatArray(2); val p2 = FloatArray(2)
    gm.trayPos(0, o); gm.pegPos(2, p2); drag(g, o[0], o[1], p2[0], p2[1])
    gm.trayPos(1, o); gm.pegPos(0, p2); drag(g, o[0], o[1], p2[0], p2[1])
    check(gm.onPeg[2] == 0 && gm.onPeg[0] == 1)
    gm.crankCenter(o); val cr = gm.crankRadius()
    drag(g, o[0] + cr, o[1], o[0], o[1] + cr, 6)
    ticks(g, 0.2f); shot(g, "22_gears_jam")
    check(!gm.solved)
    // close with X
    tapS(g, (W / 2f) + 0f, 0f) // miss
    g.onBack(); tick(g)
    check(g.mini == null)
    idle(g)
    tapW(g, 520f, 600f); idle(g)
    useItem(g, Icon.FISH, 520f, 600f); ticks(g, 1f); shot(g, "23_cat_eats"); idle(g); check(g.has(Icon.GEAR))
    tapW(g, 870f, 680f); waitMini(g)
    gm = g.mini as GearsMini
    gm.trayPos(1, o); gm.pegPos(0, p2); drag(g, o[0], o[1], p2[0], p2[1])
    gm.trayPos(2, o); gm.pegPos(1, p2); drag(g, o[0], o[1], p2[0], p2[1])
    gm.trayPos(0, o); gm.pegPos(2, p2); drag(g, o[0], o[1], p2[0], p2[1])
    check(gm.chainOk()) { "chain not ok ${gm.onPeg.toList()}" }
    gm.crankCenter(o)
    g.touchDown(o[0] + cr, o[1]); tick(g)
    var ca = 0f
    while (!gm.solved) { ca += 0.12f; g.touchMove(o[0] + cos(ca) * cr, o[1] + sin(ca) * cr); tick(g); if (ca > 3f && ca < 3.15f) shot(g, "24_gears_turning"); check(ca < 40f) }
    g.touchUp(o[0], o[1]); tick(g)
    idle(g); check(g.flag(F.BRIDGE_DOWN))
    shot(g, "25_bridge_down"); snap(host)
    tapW(g, 1380f, 600f); idle(g); check(g.flag(F.LAMP2))
    ticks(g, 1.5f); shot(g, "26_canal_lit")
    tapW(g, 1210f, 850f); idle(g); check(g.has(Icon.LENS))
    // mid-game save/restore check
    run {
        val g2 = newGame(host)
        check(g2.sceneIdx == g.sceneIdx) { "restore scene" }
        for (f in 0 until F.COUNT) check(g2.flags[f] == g.flags[f]) { "restore flag $f" }
        check(g2.inv == g.inv) { "restore inv" }
        check(g2.fireflies == g.fireflies)
    }
    tapW(g, 1560f, 600f); idle(g); check(g.sceneIdx == 3)
    shot(g, "30_hill_dark"); snap(host)
    catchFireflies(g)
    // --- hill
    tapW(g, 600f, 600f); waitMini(g); shot(g, "31_telescope"); tapS(g, W / 2f, H / 2f); ticks(g, 0.3f); check(g.mini == null); idle(g)
    tapW(g, 1150f, 500f); idle(g)
    useItem(g, Icon.LENS, 1150f, 500f); waitMini(g)
    val mm = g.mini as MirrorMini
    shot(g, "32_mirrors")
    val need = intArrayOf(1, -1, -1, 0, 0, 0, 0, 0, 1)
    for (i in need.indices) {
        if (need[i] < 0 || mm.state[i] == need[i]) continue
        mm.tapCell(MirrorMini.MIRRORS[i * 2], MirrorMini.MIRRORS[i * 2 + 1], o); tapS(g, o[0], o[1]); ticks(g, 0.15f)
    }
    check(mm.hit) { "beam should hit" }
    ticks(g, 0.8f); shot(g, "33_mirrors_solved")
    idle(g); ticks(g, 1f); check(g.flag(F.LAMP3))
    ticks(g, 1.5f); shot(g, "34_hill_lit"); snap(host)
    tapW(g, 450f, 650f); idle(g); check(g.has(Icon.KEY))
    tapW(g, 1560f, 600f); idle(g); check(g.sceneIdx == 4)
    shot(g, "40_square"); snap(host)
    catchFireflies(g)
    tapW(g, 1050f, 650f); idle(g)
    useItem(g, Icon.KEY, 1050f, 650f); idle(g); check(g.flag(F.TOWER_OPEN))
    shot(g, "41_square_open")
    tapW(g, 1050f, 650f); idle(g); check(g.sceneIdx == 5)
    shot(g, "50_tower_top"); snap(host)
    catchFireflies(g)
    tapW(g, 1150f, 500f); idle(g); check(!g.flag(F.LAMP4))
    tapW(g, 430f, 650f); waitMini(g)
    val rm = g.mini as RingsMini
    shot(g, "51_rings")
    for (ring in 0..2) { var guard = 0; while (rm.topSymbol(ring) != RingsMini.TARGET[ring]) { rm.ringTapPoint(ring, o); tapS(g, o[0], o[1]); ticks(g, 0.1f); check(++guard < 9) } }
    ticks(g, 0.5f); shot(g, "52_rings_solved")
    idle(g); check(g.flag(F.CLOCK_RUN))
    tapW(g, 1150f, 500f); ticks(g, 3.5f); shot(g, "53_finale_giving_flame")
    var k = 0
    while (g.mode != Game.MODE_ENDING) { tick(g); check(++k < 60 * 20) { "no ending" } }
    ticks(g, 3f); shot(g, "60_ending")
    ticks(g, 4f); shot(g, "61_ending_card")
    println("fireflies ${g.fireflyCount()}/${g.fireflyTotal}")
    check(g.fireflyCount() == 18) { "fireflies missed" }
    tapS(g, W / 2f, H / 2f); ticks(g, 1f)
    check(g.mode == Game.MODE_TITLE)
    shot(g, "62_title_after")
    // pause menu
    btn(g, Game.B_PLAY); ticks(g, 1f); btn(g, Game.B_PAUSE); ticks(g, 0.3f); shot(g, "63_pause"); btn(g, Game.B_RESUME)
    println("sounds=${host.sounds} frames=$frames")
    java.io.File("/home/claude/lamplighter/build/snapshots.txt").writeText(snapshots.joinToString("\n"))
    println("snapshots: ${snapshots.size}")
    println("PLAYTHROUGH OK")
}

object PlayRunner { @JvmStatic fun main(args: Array<String>) = playMain() }
