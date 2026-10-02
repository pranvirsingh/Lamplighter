package com.pranvir.lamplighter

import android.graphics.Canvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

abstract class Scene(val g: Game, val index: Int) {
    open val ground = 770f
    open val minX get() = 120f
    open val maxX get() = 1480f
    abstract val ambient: Int
    open val warmGroup = 0
    lateinit var art: Boil
    val hotspots = ArrayList<Hot>()
    protected val ffX = FloatArray(3)
    protected val ffY = FloatArray(3)
    protected val flags get() = g.flags

    abstract fun build(s: Sketch)
    open fun reset() {}
    open fun enter() {}
    open fun update(dt: Float, t: Float) {}
    open fun drawBehind(c: Canvas, t: Float) {}
    open fun drawBack(c: Canvas, t: Float) {}
    open fun drawFront(c: Canvas, t: Float) {}

    fun init(seed: Long) { art = buildBoil(seed, 2.6f) { build(it) } }

    fun ffPos(k: Int, t: Float, out: FloatArray) {
        out[0] = ffX[k] + sin(t * 0.7f + k * 2.1f) * 28f + sin(t * 1.9f + k) * 8f
        out[1] = ffY[k] + cos(t * 0.9f + k * 1.3f) * 20f
    }

    private val tmp = FloatArray(2)

    fun fireflyAt(x: Float, y: Float, t: Float): Int {
        for (k in 0 until 3) {
            val id = index * 3 + k
            if (g.fireflies and (1L shl id) != 0L) continue
            ffPos(k, t, tmp)
            if (hypot(x - tmp[0], y - tmp[1]) < 60f) return id
        }
        return -1
    }

    fun drawFireflies(c: Canvas, t: Float, caught: Long) {
        for (k in 0 until 3) {
            val id = index * 3 + k
            if (caught and (1L shl id) != 0L) continue
            ffPos(k, t, tmp)
            val blink = 0.35f + 0.65f * max(0f, sin(t * 2.3f + k * 1.7f))
            Draw.glow(c, tmp[0], tmp[1], 46f, 0.9f * blink)
            Draw.fill.color = withAlpha(Pal.AMBER_HI, (150 + 100 * blink).toInt().coerceIn(0, 255))
            c.drawCircle(tmp[0], tmp[1], 4.5f, Draw.fill)
            Draw.fill.color = withAlpha(Pal.PAPER_HI, 90)
            Draw.r1.set(tmp[0] - 9f, tmp[1] - 9f + sin(t * 30f) * 2f, tmp[0] - 1f, tmp[1] - 3f)
            c.drawOval(Draw.r1, Draw.fill)
        }
    }

    // -------- helpers for scene code

    protected fun exitLeft(target: Int, entryX: Float, cond: () -> Boolean = { true }, blocked: IntArray = intArrayOf()) {
        hotspots.add(Hot(-400f, 380f, minX + 20f, 900f, minX, exit = true, onTap = {
            if (cond()) g.goScene(target, entryX, -1) else g.think(1.8f, *blocked)
        }))
    }

    protected fun exitRight(target: Int, entryX: Float, cond: () -> Boolean = { true }, blocked: IntArray = intArrayOf()) {
        hotspots.add(Hot(1500f, 380f, 2000f, 900f, 1480f, exit = true, onTap = {
            if (cond() && g.wick.x >= 1470f) g.goScene(target, entryX, 1) else if (!cond()) g.think(1.8f, *blocked)
        }))
    }

    /** Draws a street lamp. lit 0..1 */
    fun streetLamp(c: Canvas, x: Float, base: Float, top: Float, lit: Float, t: Float, seed: Float) {
        val st = Draw.stroke; val f = Draw.fill
        if (lit > 0.01f) {
            // light pool on the ground and a big soft halo
            Draw.r1.set(x - 260f * lit, base - 30f, x + 260f * lit, base + 40f)
            f.color = withAlpha(Pal.AMBER, (50 * lit).toInt())
            c.drawOval(Draw.r1, f)
            val flick = 1f + 0.04f * sin(t * 13f + seed) + 0.03f * sin(t * 23f + seed)
            Draw.glow(c, x, top + 30f, 460f * lit * flick, 0.75f * lit)
        }
        // post
        st.color = Pal.INK
        st.strokeWidth = 11f
        c.drawLine(x, base - 20f, x, top + 60f, st)
        f.color = Pal.T5
        Draw.r1.set(x - 22f, base - 34f, x + 22f, base); c.drawRoundRect(Draw.r1, 6f, 6f, f)
        Draw.r1.set(x - 14f, base - 70f, x + 14f, base - 34f); c.drawRoundRect(Draw.r1, 4f, 4f, f)
        st.strokeWidth = 4f
        Draw.r1.set(x - 36f, top + 58f, x, top + 94f); c.drawArc(Draw.r1, 270f, 180f, false, st)
        Draw.r1.set(x, top + 58f, x + 36f, top + 94f); c.drawArc(Draw.r1, 90f, 180f, false, st)
        // head
        Draw.path.reset()
        Draw.path.moveTo(x - 17f, top + 58f); Draw.path.lineTo(x - 26f, top + 6f); Draw.path.lineTo(x + 26f, top + 6f); Draw.path.lineTo(x + 17f, top + 58f); Draw.path.close()
        f.color = lerpColor(Pal.T4, Pal.AMBER_HI, lit)
        c.drawPath(Draw.path, f)
        if (lit > 0.05f) Draw.flame(c, x, top + 46f, 11f * lit, t, seed)
        st.strokeWidth = 4f
        c.drawPath(Draw.path, st)
        c.drawLine(x, top + 6f, x, top + 58f, st.also { it.strokeWidth = 2f })
        st.strokeWidth = 4f
        Draw.path.reset()
        Draw.path.moveTo(x - 34f, top + 8f); Draw.path.lineTo(x, top - 20f); Draw.path.lineTo(x + 34f, top + 8f); Draw.path.close()
        f.color = Pal.T5
        c.drawPath(Draw.path, f); c.drawPath(Draw.path, st)
        c.drawCircle(x, top - 26f, 6f, st)
    }
}

// ====================================================================== shared art helpers

object Parts {
    fun farSkyline(s: Sketch, towerX: Float, groups: IntArray = intArrayOf()) {
        val col = 0xFF57524B.toInt()
        val pts = floatArrayOf(-500f, 900f, -500f, 520f, -380f, 520f, -380f, 470f, -250f, 470f, -250f, 540f, -120f, 540f, -120f, 450f,
            40f, 450f, 40f, 500f, 180f, 500f, 180f, 430f, 330f, 430f, 330f, 520f, 470f, 520f, 470f, 470f, 620f, 470f, 620f, 540f,
            780f, 540f, 780f, 440f, 930f, 440f, 930f, 500f, 1080f, 500f, 1080f, 460f, 1230f, 460f, 1230f, 530f, 1380f, 530f, 1380f, 450f,
            1540f, 450f, 1540f, 510f, 1700f, 510f, 1700f, 440f, 1860f, 440f, 1860f, 520f, 2100f, 520f, 2100f, 900f)
        s.poly(pts, col, withAlpha(Pal.INK, 120), 2f)
        // the clock tower on the horizon
        s.poly(floatArrayOf(towerX - 55f, 900f, towerX - 55f, 170f, towerX - 70f, 170f, towerX, 60f, towerX + 70f, 170f, towerX + 55f, 170f, towerX + 55f, 900f),
            0xFF4A4640.toInt(), withAlpha(Pal.INK, 150), 2f)
        s.ellipse(towerX, 240f, 34f, 34f, 0xFF6A645B.toInt(), withAlpha(Pal.INK, 150), 2f, 4, Pal.AMBER_HI)
        // tiny distant windows
        var k = 0
        for (gi in groups) {
            for (j in 0 until 5) {
                val x = -400f + ((k * 173 + j * 311) % 2400).toFloat()
                val y = 470f + ((k * 37 + j * 53) % 40).toFloat()
                s.rect(x, y, x + 10f, y + 14f, Pal.WIN_DARK, 0, 0f, gi)
            }
            k++
        }
    }

    fun cobbles(s: Sketch, y0: Float, x0: Float = -500f, x1: Float = 2100f) {
        s.rect(x0, y0, x1, 1300f, Pal.T3, Pal.INK, 3f)
        val p = android.graphics.Path()
        var row = 0
        var y = y0 + 16f
        while (y < 1000f) {
            var x = x0 + (if (row % 2 == 0) 0f else 30f)
            while (x < x1) {
                p.moveTo(x, y); p.quadTo(x + 25f, y - 10f, x + 50f, y)
                x += 60f
            }
            y += 24f + row * 2f; row++
        }
        s.shapes.add(Shape(p, 0, withAlpha(Pal.INK, 60), 1.6f, -1, 0))
    }

    /** A gabled house front. */
    fun house(s: Sketch, l: Float, r: Float, top: Float, roofH: Float, group: Int, wall: Int = Pal.T1, rows: Int = 2) {
        s.rect(l, top, r, 770f, wall, Pal.INK, 3.5f)
        s.brickPattern(l, top, r, 770f, 56f, 26f)
        s.poly(floatArrayOf(l - 26f, top, (l + r) / 2f, top - roofH, r + 26f, top), Pal.T4, Pal.INK, 3.5f)
        s.hatch(l - 10f, top - roofH * 0.6f, r + 10f, top, 14f)
        // chimney
        val cx = l + (r - l) * 0.72f
        s.rect(cx, top - roofH * 0.85f, cx + 34f, top - roofH * 0.35f, Pal.T3, Pal.INK, 3f)
        val n = max(1, ((r - l) / 140f).toInt())
        for (row in 0 until rows) {
            for (k in 0 until n) {
                val wx = l + (r - l) * (k + 0.5f) / n
                val wy = top + 60f + row * 170f
                if (wy + 100f > 690f) continue
                s.window(wx - 34f, wy, wx + 34f, wy + 90f, group)
            }
        }
    }
}

// ====================================================================== scenes

object Scenes {
    fun create(g: Game): Array<Scene> {
        val list = arrayOf<Scene>(Workshop(g), Market(g), Canal(g), Hill(g), Square(g), TowerTop(g))
        for ((i, s) in list.withIndex()) { s.init(1000L + i * 97); s.reset() }
        return list
    }

    // --------------------------------------------------------------- title & ending art

    fun titleArt(): Boil = buildBoil(77, 2.6f) { s ->
        Parts.farSkyline(s, 1260f, intArrayOf(1, 2, 3, 4))
        // near rooftops
        s.poly(floatArrayOf(-500f, 1300f, -500f, 700f, -200f, 560f, 120f, 700f, 120f, 640f, 360f, 520f, 700f, 690f, 700f, 1300f), Pal.T5, Pal.INK, 3.5f)
        s.poly(floatArrayOf(640f, 1300f, 640f, 690f, 1000f, 600f, 1400f, 690f, 1400f, 630f, 1700f, 540f, 2100f, 650f, 2100f, 1300f), 0xFF3C3731.toInt(), Pal.INK, 3.5f)
        s.hatch(-500f, 700f, 700f, 900f, 16f)
        s.rect(250f, 470f, 300f, 580f, Pal.T4, Pal.INK, 3f)
        s.rect(1500f, 480f, 1550f, 600f, Pal.T4, Pal.INK, 3f)
        for (k in 0 until 4) s.window(-150f + k * 190f, 740f, -100f + k * 190f, 810f, 1 + k, Pal.T4, true)
        for (k in 0 until 4) s.window(800f + k * 200f, 740f, 850f + k * 200f, 810f, 4 - k, Pal.T4, true)
    }

    private val titleWick = Wick().apply { x = 380f; ground = 525f; hasPole = true; face = 1 }

    fun drawTitleBehind(c: Canvas, t: Float) {
        // moon
        Draw.fill.color = withAlpha(Pal.PAPER_HI, 220)
        c.drawCircle(1480f, 130f, 46f, Draw.fill)
        Draw.fill.color = Pal.SKY_TOP
        c.drawCircle(1502f, 118f, 42f, Draw.fill)
    }

    fun drawTitleFront(c: Canvas, t: Float, g: Game) {
        titleWick.update(1f / 60f, t)
        titleWick.flame = 1f
        titleWick.draw(c, t, 1f)
        if (g.flags[F.LAMP4]) Draw.glow(c, 1260f, 150f, 500f, 0.8f)
        // drifting fireflies
        for (k in 0 until 7) {
            val x = 200f + k * 190f + sin(t * 0.5f + k) * 50f
            val y = 380f + cos(t * 0.7f + k * 2f) * 80f
            val b = 0.4f + 0.6f * max(0f, sin(t * 2f + k))
            Draw.glow(c, x, y, 40f, b)
            Draw.fill.color = Pal.AMBER_HI
            c.drawCircle(x, y, 3.5f, Draw.fill)
        }
    }

    fun endingArt(): Boil = buildBoil(88, 2.6f) { s ->
        Parts.farSkyline(s, 1300f, intArrayOf(1, 2, 3, 4, 1, 2))
        s.poly(floatArrayOf(-500f, 1300f, -500f, 720f, -150f, 600f, 250f, 720f, 250f, 660f, 520f, 580f, 820f, 700f, 820f, 1300f), Pal.T5, Pal.INK, 3.5f)
        s.poly(floatArrayOf(760f, 1300f, 760f, 700f, 1100f, 610f, 1450f, 700f, 1450f, 640f, 1800f, 560f, 2100f, 660f, 2100f, 1300f), 0xFF3C3731.toInt(), Pal.INK, 3.5f)
        for (k in 0 until 5) s.window(-200f + k * 190f, 760f, -150f + k * 190f, 830f, 1 + k % 4, Pal.T4, true)
        for (k in 0 until 5) s.window(860f + k * 200f, 760f, 910f + k * 200f, 830f, 1 + k % 4, Pal.T4, true)
    }

    private val endWick = Wick().apply { x = 380f; ground = 596f; hasPole = true; face = 1 }

    fun drawEndingFront(c: Canvas, t: Float, endT: Float) {
        // the great lamp blazing at the top of the tower, sweeping its beam
        val lx = 1300f; val ly = 120f
        val a = sin(t * 0.6f) * 0.9f
        Draw.path.reset()
        Draw.path.moveTo(lx, ly)
        Draw.path.lineTo(lx + cos(a - 0.12f) * 1800f, ly + sin(a - 0.12f) * 1800f * 0.25f + 200f)
        Draw.path.lineTo(lx + cos(a + 0.12f) * 1800f, ly + sin(a + 0.12f) * 1800f * 0.25f + 260f)
        Draw.path.close()
        Draw.fill.color = withAlpha(Pal.AMBER_HI, 40)
        c.drawPath(Draw.path, Draw.fill)
        Draw.glow(c, lx, ly, 520f, 1f)
        Draw.flame(c, lx, ly + 20f, 34f, t)
        endWick.setPose(if (endT > 1.5f) Wick.HAPPY else Wick.IDLE)
        endWick.flame = clamp01(endT / 2f)
        endWick.flameTarget = endWick.flame
        endWick.update(1f / 60f, t)
        endWick.draw(c, t, 1f)
        for (k in 0 until 6) Folk.towns(c, 900f + k * 105f, 690f - (k % 2) * 24f, t, k, clamp01(endT - 1f - k * 0.3f))
    }
}

// ---------------------------------------------------------------------- 0. workshop

class Workshop(g: Game) : Scene(g, 0) {
    override val ambient = Sfx.AMB_WORKSHOP
    override val maxX get() = 1360f
    var stoolX = 1060f
    private var doorOpen = 0f

    init {
        ffX[0] = 150f; ffY[0] = 280f
        ffX[1] = 1300f; ffY[1] = 540f
        ffX[2] = 640f; ffY[2] = 130f
        hotspots.add(Hot(20f, 500f, 180f, 770f, 210f) { g.think(1.8f, Icon.FLAME, Icon.NO) })
        hotspots.add(Hot(900f, 220f, 1120f, 470f, 1000f) { g.think(2.2f, Icon.TOWER, Icon.LAMP, Icon.QUESTION) })
        hotspots.add(Hot(710f, 110f, 810f, 210f, 760f) { g.think(1.8f, Icon.CLOCK, Icon.TOWER) })
        hotspots.add(Hot(1195f, 320f, 1295f, 430f, 1240f) { g.think(2f, Icon.LAMP, Icon.HEART) })
        hotspots.add(Hot(330f, 520f, 520f, 720f, 470f) { g.pose(Wick.SHAKE, 0.6f); g.think(1.6f, Icon.ZZZ, Icon.NO) })
        // stool
        hotspots.add(Hot(1000f, 670f, 1120f, 770f, 1130f, visible = { !flags[F.STOOL_MOVED] }) {
            g.face(stoolX)
            g.then { g.wick.setPose(Wick.PUSH); g.sfx(Sfx.SCRAPE, 0.8f) }
            g.tween(1.6f) { p ->
                stoolX = lerp(1060f, 700f, smooth(p))
                g.wick.x = stoolX + 62f
            }
            g.then { g.wick.setPose(Wick.IDLE); g.set(F.STOOL_MOVED) }
        })
        // shelf & oil can
        hotspots.add(Hot(590f, 220f, 850f, 330f, 720f, visible = { !flags[F.GOT_OIL] }) {
            if (!flags[F.STOOL_MOVED]) {
                g.pose(Wick.REACH_UP, 0.9f)
                g.think(2f, Icon.STOOL, Icon.QUESTION)
            } else {
                g.sfxAct(Sfx.STEP, 0.5f)
                g.tween(0.35f) { g.wick.yOff = -80f * smooth(it) }
                g.pose(Wick.REACH_UP, 0.6f)
                g.then { g.set(F.GOT_OIL); g.give(Icon.OILCAN) }
                g.tween(0.3f) { g.wick.yOff = -80f * (1f - smooth(it)) }
                g.pose(Wick.NOD, 0.5f)
            }
        })
        // pole on its hooks
        hotspots.add(Hot(190f, 330f, 290f, 660f, 300f, visible = { !flags[F.GOT_POLE] }) {
            g.pose(Wick.REACH_UP, 0.6f)
            g.then { g.set(F.GOT_POLE); g.wick.hasPole = true; g.sfx(Sfx.PICKUP) }
            g.pose(Wick.HAPPY, 0.8f)
        })
        // the door
        hotspots.add(Hot(1370f, 460f, 1540f, 770f, 1345f, onItem = { item ->
            if (item == Icon.OILCAN && !flags[F.DOOR_OPEN]) {
                g.take(Icon.OILCAN)
                g.then { g.sfx(Sfx.SQUEAK, 0.8f) }
                g.pose(Wick.OIL, 1.3f)
                g.then { g.sfx(Sfx.DOOR) }
                g.tween(0.8f) { doorOpen = smooth(it) }
                g.then { g.set(F.DOOR_OPEN) }
                g.pose(Wick.HAPPY, 0.7f)
                true
            } else false
        }) {
            if (flags[F.DOOR_OPEN]) g.goScene(1, 150f, 1)
            else {
                g.then { g.sfx(Sfx.RATTLE) }
                g.pose(Wick.PUSH, 0.7f)
                g.think(2f, Icon.DOOR, Icon.QUESTION)
            }
        })
    }

    override fun reset() { stoolX = 1060f; doorOpen = 0f }
    override fun enter() {
        stoolX = if (flags[F.STOOL_MOVED]) 700f else 1060f
        doorOpen = if (flags[F.DOOR_OPEN]) 1f else 0f
    }

    override fun build(s: Sketch) {
        s.rect(-500f, -400f, 2100f, 770f, Pal.T2, Pal.INK, 0f)
        for (k in 0 until 30) s.line(-500f + k * 90f, 90f, -500f + k * 90f, 600f, withAlpha(Pal.INK, 50), 2f)
        s.rect(-500f, 600f, 2100f, 770f, Pal.T3, Pal.INK, 3f)
        s.hatch(-500f, 610f, 2100f, 770f, 18f)
        s.rect(-500f, 40f, 2100f, 92f, Pal.T4, Pal.INK, 3.5f)
        s.hatch(-500f, 40f, 2100f, 92f, 12f)
        s.rect(-500f, 770f, 2100f, 1300f, Pal.T3, Pal.INK, 3.5f)
        for (y in intArrayOf(805, 850, 910, 990)) s.line(-500f, y.toFloat(), 2100f, y.toFloat(), withAlpha(Pal.INK, 70), 2f)
        // window with the dark town outside
        s.rect(888f, 208f, 1132f, 482f, Pal.T4, Pal.INK, 4f)
        s.rect(900f, 220f, 1120f, 470f, Pal.SKY_MID, Pal.INK, 3f)
        s.poly(floatArrayOf(900f, 470f, 900f, 420f, 950f, 400f, 990f, 430f, 1040f, 395f, 1060f, 300f, 1070f, 270f, 1080f, 300f, 1080f, 395f, 1120f, 410f, 1120f, 470f), Pal.T5, Pal.INK, 2f)
        s.line(1010f, 220f, 1010f, 470f, Pal.INK, 5f)
        s.line(900f, 345f, 1120f, 345f, Pal.INK, 5f)
        s.rect(875f, 470f, 1145f, 492f, Pal.T3, Pal.INK, 3f)
        // shelf with jars
        s.rect(600f, 300f, 840f, 318f, Pal.T4, Pal.INK, 3f)
        s.poly(floatArrayOf(620f, 318f, 650f, 318f, 620f, 360f), Pal.T4, Pal.INK, 2.5f)
        s.poly(floatArrayOf(790f, 318f, 820f, 318f, 820f, 360f), Pal.T4, Pal.INK, 2.5f)
        s.rect(615f, 250f, 648f, 300f, Pal.T1, Pal.INK, 2.5f)
        s.rect(658f, 262f, 690f, 300f, Pal.PAPER, Pal.INK, 2.5f)
        s.ellipse(705f, 285f, 12f, 15f, Pal.T3, Pal.INK, 2.5f)
        // wall clock (stopped)
        s.ellipse(760f, 160f, 40f, 40f, Pal.PAPER, Pal.INK, 3.5f)
        s.line(760f, 160f, 760f, 132f, Pal.INK, 3f)
        s.line(760f, 160f, 780f, 168f, Pal.INK, 3f)
        s.rect(752f, 200f, 768f, 240f, Pal.T4, Pal.INK, 2f)
        // hooks for the pole
        s.rect(222f, 345f, 262f, 358f, Pal.T4, Pal.INK, 2.5f)
        s.rect(222f, 628f, 262f, 641f, Pal.T4, Pal.INK, 2.5f)
        // armchair
        s.rect(345f, 520f, 505f, 700f, Pal.T4, Pal.INK, 3.5f)
        s.hatch(345f, 520f, 505f, 700f, 12f)
        s.rect(330f, 650f, 520f, 720f, Pal.T3, Pal.INK, 3.5f)
        s.rect(318f, 610f, 350f, 720f, Pal.T4, Pal.INK, 3f)
        s.rect(500f, 610f, 532f, 720f, Pal.T4, Pal.INK, 3f)
        s.line(340f, 720f, 336f, 770f, Pal.INK, 6f)
        s.line(510f, 720f, 514f, 770f, Pal.INK, 6f)
        // stove (cold)
        s.rect(40f, 560f, 170f, 770f, Pal.T4, Pal.INK, 3.5f)
        s.rect(70f, 620f, 140f, 690f, Pal.T5, Pal.INK, 3f)
        s.rect(92f, 92f, 120f, 560f, Pal.T4, Pal.INK, 3f)
        // workbench + tools + photo
        s.rect(1140f, 600f, 1350f, 626f, Pal.T4, Pal.INK, 3.5f)
        s.line(1160f, 626f, 1160f, 770f, Pal.INK, 7f)
        s.line(1330f, 626f, 1330f, 770f, Pal.INK, 7f)
        s.rect(1165f, 690f, 1325f, 704f, Pal.T3, Pal.INK, 2.5f)
        s.rect(1290f, 540f, 1330f, 600f, withAlpha(Pal.PAPER_HI, 120), Pal.INK, 2.5f)
        s.rect(1195f, 320f, 1295f, 430f, Pal.T4, Pal.INK, 3f)
        s.rect(1206f, 331f, 1284f, 419f, Pal.PAPER, Pal.INK, 2f)
        s.line(1245f, 415f, 1245f, 360f, Pal.INK, 2.5f)
        s.rect(1236f, 344f, 1254f, 362f, Pal.WIN_DARK, Pal.INK, 1.5f, 0, Pal.AMBER_HI)
        s.line(1160f, 480f, 1230f, 540f, Pal.INK, 4f)
        s.rect(1222f, 470f, 1250f, 486f, Pal.T4, Pal.INK, 2f)
        // door frame and exterior glimpse
        s.rect(1368f, 458f, 1532f, 770f, Pal.T4, Pal.INK, 4f)
        s.rect(1382f, 472f, 1518f, 770f, Pal.SKY_MID, Pal.INK, 2f)
        s.rect(1382f, 700f, 1518f, 770f, Pal.T4, Pal.INK, 2f)
    }

    override fun drawBack(c: Canvas, t: Float) {
        val st = Draw.stroke
        // pole on hooks
        if (!flags[F.GOT_POLE]) {
            st.color = Pal.INK; st.strokeWidth = 6f
            c.drawLine(242f, 350f, 242f, 640f, st)
            st.strokeWidth = 3.5f
            Draw.r1.set(232f, 330f, 252f, 352f); c.drawArc(Draw.r1, 180f, 200f, false, st)
        }
        // oil can
        if (!flags[F.GOT_OIL]) Icon.draw(c, Icon.OILCAN, 770f, 272f, 56f, Pal.INK, Pal.BRASS, t)
        // stool
        val sx = stoolX
        st.color = Pal.INK; st.strokeWidth = 6f
        c.drawLine(sx - 36f, 700f, sx - 44f, 770f, st)
        c.drawLine(sx + 36f, 700f, sx + 44f, 770f, st)
        c.drawLine(sx - 40f, 740f, sx + 40f, 740f, st.also { it.strokeWidth = 4f })
        Draw.fill.color = Pal.T1
        Draw.r1.set(sx - 50f, 686f, sx + 50f, 704f)
        c.drawRoundRect(Draw.r1, 5f, 5f, Draw.fill)
        st.strokeWidth = 3f
        c.drawRoundRect(Draw.r1, 5f, 5f, st)
        // door leaf (hinged on the right)
        val open = doorOpen
        val leafW = 136f * (1f - open * 0.82f)
        Draw.fill.color = Pal.T3
        Draw.r1.set(1518f - leafW, 472f, 1518f, 770f)
        c.drawRect(Draw.r1, Draw.fill)
        st.strokeWidth = 3f
        c.drawRect(Draw.r1, st)
        if (leafW > 40f) {
            st.strokeWidth = 2f; st.color = withAlpha(Pal.INK, 90)
            for (k in 1..3) c.drawLine(1518f - leafW * k / 4f, 476f, 1518f - leafW * k / 4f, 766f, st)
            Draw.fill.color = Pal.INK
            c.drawCircle(1518f - leafW + 18f, 620f, 7f, Draw.fill)
        }
    }
}

// ---------------------------------------------------------------------- 1. market square

class Market(g: Game) : Scene(g, 1) {
    override val ambient = Sfx.AMB_WIND
    override val warmGroup = 1
    private var crowX = 1150f
    private var crowY = 405f
    private var crowFlap = 0f
    private var caw = 0f
    private var valveY = -1f
    private var shutter = 0f
    private var monger = 0f

    init {
        ffX[0] = 120f; ffY[0] = 210f
        ffX[1] = 1000f; ffY[1] = 230f
        ffX[2] = 1450f; ffY[2] = 410f
        exitLeft(0, 1340f)
        exitRight(2, 140f)
        hotspots.add(Hot(170f, 590f, 280f, 770f, 290f) {
            if (flags[F.LAMP1]) g.think(1.6f, Icon.HEART) else { g.then { g.sfx(Sfx.KNOCK) }; g.pose(Wick.PUSH, 0.4f); g.think(1.6f, Icon.ZZZ) }
        })
        // fountain with a coin
        hotspots.add(Hot(300f, 580f, 540f, 790f, 550f) {
            if (!flags[F.GOT_COIN]) {
                g.face(440f)
                g.pose(Wick.REACH_DOWN, 0.8f)
                g.then { g.set(F.GOT_COIN); g.give(Icon.COIN) }
            } else g.think(1.4f, Icon.FISH, Icon.NO)
        })
        // crow on the sign bracket
        hotspots.add(Hot(1090f, 320f, 1260f, 460f, 1110f, onItem = { item ->
            if (item == Icon.COIN && !flags[F.COIN_GIVEN]) { tradeCoin(); true } else false
        }) {
            g.then { caw = 1f; g.sfx(Sfx.CAW) }
            if (flags[F.COIN_GIVEN]) g.npcThink(crowX, crowY - 50f, 1.6f, Icon.COIN, Icon.HEART)
            else g.npcThink(crowX, crowY - 50f, 2f, Icon.COIN, Icon.QUESTION)
        })
        // the lamp
        hotspots.add(Hot(760f, 330f, 880f, 770f, 745f, onItem = { item ->
            if (item == Icon.VALVE) {
                g.take(Icon.VALVE)
                g.pose(Wick.REACH_DOWN, 0.6f)
                g.then { g.sfx(Sfx.CLANK) }
                openValve()
                true
            } else false
        }) {
            when {
                flags[F.LAMP1] -> g.think(1.4f, Icon.HEART)
                flags[F.GAS_ON] -> lightIt()
                flags[F.GOT_VALVE] && !g.has(Icon.VALVE) -> openValve()
                else -> {
                    g.then { g.wick.setPose(Wick.RAISE_POLE) }
                    g.wait(0.8f)
                    g.then { g.wick.setPose(Wick.IDLE) }
                    g.think(2f, Icon.LAMP, Icon.VALVE, Icon.QUESTION)
                }
            }
        })
        // fish stall
        hotspots.add(Hot(1280f, 450f, 1590f, 770f, 1265f) {
            if (!flags[F.LAMP1]) { g.then { g.sfx(Sfx.KNOCK) }; g.pose(Wick.PUSH, 0.4f); g.think(1.6f, Icon.ZZZ, Icon.LAMP) }
            else if (!flags[F.GOT_FISH]) {
                g.npcThink(1430f, 520f, 1.8f, Icon.LAMP, Icon.HEART)
                g.pose(Wick.GIVE, 0.6f)
                g.then { g.set(F.GOT_FISH); g.give(Icon.FISH) }
                g.pose(Wick.HAPPY, 0.6f)
            } else g.npcThink(1430f, 520f, 1.5f, Icon.HEART)
        })
    }

    private fun tradeCoin() {
        g.take(Icon.COIN)
        g.set(F.COIN_GIVEN)
        g.pose(Wick.GIVE, 0.4f)
        g.then { caw = 1f; g.sfx(Sfx.CAW) }
        // crow swoops down, grabs the coin and flies up to its nest
        g.tween(1.4f) { p ->
            crowFlap = 1f
            val s = smooth(p)
            val midX = g.wick.x + 60f; val midY = 560f
            if (s < 0.5f) { val q = s * 2f; crowX = lerp(1150f, midX, q); crowY = lerp(405f, midY, q) }
            else { val q = (s - 0.5f) * 2f; crowX = lerp(midX, 1215f, q); crowY = lerp(midY, 380f, q) }
        }
        g.then { crowFlap = 0f; g.sfx(Sfx.RUSTLE) }
        g.wait(0.4f)
        // out tumbles the shiny valve wheel
        g.tween(0.7f) { p -> valveY = lerp(395f, 760f, p * p) }
        g.then { g.sfx(Sfx.CLINK) }
        g.walkTo(1100f)
        g.pose(Wick.REACH_DOWN, 0.6f)
        g.then { valveY = -1f; g.set(F.GOT_VALVE); g.give(Icon.VALVE) }
        g.tween(1f) { p -> crowFlap = 1f; crowX = lerp(1215f, 1150f, p); crowY = lerp(380f, 405f, p) }
        g.then { crowFlap = 0f }
    }

    private fun openValve() {
        g.openMini(PressureMini(g) {
            g.set(F.GAS_ON)
            g.sfx(Sfx.HISS, 0.5f)
            lightIt()
        })
    }

    private fun lightIt() {
        g.walkTo(745f)
        g.lightLamp(F.LAMP1, 820f) {}
        g.tween(1.5f) { shutter = smooth(it) }
        g.tween(0.8f) { monger = smooth(it) }
    }

    override fun reset() { crowX = 1150f; crowY = 405f; valveY = -1f; shutter = 0f; monger = 0f }
    override fun enter() {
        shutter = if (flags[F.LAMP1]) 1f else 0f
        monger = shutter
        crowX = 1150f; crowY = 405f; crowFlap = 0f; valveY = -1f
    }

    override fun update(dt: Float, t: Float) {
        caw = max(0f, caw - dt * 2f)
    }

    override fun build(s: Sketch) {
        Parts.farSkyline(s, 1320f, intArrayOf(2, 3))
        Parts.house(s, -400f, 300f, 200f, 150f, 1, Pal.T1, 3)
        s.rect(175f, 596f, 275f, 770f, Pal.T4, Pal.INK, 3f)
        s.ellipse(255f, 690f, 5f, 5f, Pal.INK, Pal.INK, 1f)
        // middle building with flat roof and cornice
        s.rect(560f, 250f, 1150f, 770f, 0xFFB0A591.toInt(), Pal.INK, 3.5f)
        s.brickPattern(560f, 250f, 1150f, 770f, 60f, 28f)
        s.rect(545f, 232f, 1165f, 256f, Pal.T4, Pal.INK, 3f)
        for (k in 0 until 4) s.window(600f + k * 140f, 320f, 670f + k * 140f, 420f, 1)
        s.rect(580f, 480f, 1130f, 520f, Pal.T4, Pal.INK, 3f)
        s.hatch(560f, 600f, 1150f, 770f, 16f)
        // fountain
        s.ellipse(420f, 742f, 115f, 32f, Pal.T3, Pal.INK, 3.5f)
        s.rect(305f, 742f, 535f, 780f, Pal.T2, Pal.INK, 3.5f)
        s.rect(404f, 610f, 436f, 742f, Pal.T2, Pal.INK, 3f)
        s.ellipse(420f, 610f, 50f, 14f, Pal.T1, Pal.INK, 3f)
        s.ellipse(420f, 588f, 12f, 14f, Pal.T2, Pal.INK, 2.5f)
        // crow's bracket & nest
        s.rect(1172f, 400f, 1188f, 770f, Pal.T4, Pal.INK, 3f)
        s.rect(1110f, 398f, 1250f, 412f, Pal.T4, Pal.INK, 3f)
        s.ellipse(1215f, 392f, 34f, 14f, Pal.T3, Pal.INK, 3f)
        s.hatch(1182f, 380f, 1248f, 404f, 7f)
        // fish stall
        s.rect(1270f, 440f, 1600f, 770f, Pal.T2, Pal.INK, 3.5f)
        s.rect(1296f, 640f, 1580f, 770f, Pal.T3, Pal.INK, 3.5f)
        for (k in 1..5) s.line(1296f, 640f + k * 22f, 1580f, 640f + k * 22f, withAlpha(Pal.INK, 70), 2f)
        for (k in 0 until 8) {
            val x = 1262f + k * 44f
            s.poly(floatArrayOf(x, 470f, x + 44f, 470f, x + 44f, 540f, x + 22f, 556f, x, 540f), if (k % 2 == 0) Pal.PAPER else Pal.T4, Pal.INK, 2.5f)
        }
        s.line(1282f, 540f, 1282f, 770f, Pal.INK, 7f)
        s.line(1586f, 540f, 1586f, 770f, Pal.INK, 7f)
        Parts.cobbles(s, 770f)
    }

    override fun drawBack(c: Canvas, t: Float) {
        val lit1 = g.warmth[1]
        // townsfolk
        Folk.towns(c, 620f, 770f, t, 0, lit1 * 1.2f - 0.2f)
        Folk.towns(c, 1010f, 770f, t, 3, lit1 * 1.2f - 0.4f)
        // fishmonger & shutter
        Folk.fishmonger(c, 1440f, 640f, t, monger)
        val sh = shutter
        Draw.fill.color = Pal.T4
        val shBottom = lerp(640f, 560f, sh)
        c.drawRect(1296f, 556f, 1580f, shBottom, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 3f
        c.drawRect(1296f, 556f, 1580f, shBottom, Draw.stroke)
        Draw.stroke.strokeWidth = 1.5f
        var y = 566f
        while (y < shBottom - 4f) { c.drawLine(1300f, y, 1576f, y, Draw.stroke); y += 12f }
        if (monger > 0.5f) {
            for (k in 0 until 3) Icon.draw(c, Icon.FISH, 1340f + k * 60f, 632f, 44f, Pal.INK, Pal.T1, t)
        }
        // coin glint
        if (!flags[F.GOT_COIN]) {
            Icon.draw(c, Icon.COIN, 452f, 736f, 30f, Pal.INK, Pal.BRASS, t)
            val gl = max(0f, sin(t * 3f))
            Draw.glow(c, 446f, 730f, 30f, gl * 0.6f, Pal.PAPER_HI)
        }
        // valve in nest (glinting) / falling
        if (!flags[F.COIN_GIVEN]) {
            Icon.draw(c, Icon.VALVE, 1222f, 382f, 34f, Pal.INK, Pal.BRASS, t)
            Draw.glow(c, 1228f, 376f, 30f, max(0f, sin(t * 2.3f)) * 0.6f, Pal.PAPER_HI)
        }
        if (valveY > 0f) Icon.draw(c, Icon.VALVE, 1150f, valveY - 12f, 34f, Pal.INK, Pal.BRASS, t)
        // lamp + valve fitting
        streetLamp(c, 820f, 770f, 330f, lit1, t, 1f)
        val fitted = flags[F.GAS_ON] || (flags[F.GOT_VALVE] && !g.has(Icon.VALVE))
        Draw.fill.color = Pal.T4
        c.drawRect(832f, 700f, 866f, 716f, Draw.fill)
        if (fitted) Icon.draw(c, Icon.VALVE, 872f, 708f, 34f, Pal.INK, Pal.BRASS, t)
        else { Draw.fill.color = Pal.INK; c.drawCircle(870f, 708f, 6f, Draw.fill) }
        // crow
        Folk.crow(c, crowX, crowY, t, caw, crowFlap, if (crowFlap > 0f && crowX < 1150f) -1 else -1)
        if (flags[F.COIN_GIVEN] && crowFlap <= 0f) Icon.draw(c, Icon.COIN, crowX - 10f, crowY - 4f, 18f, Pal.INK, Pal.BRASS, t)
    }
}

// ---------------------------------------------------------------------- 2. canal & drawbridge

class Canal(g: Game) : Scene(g, 2) {
    override val ambient = Sfx.AMB_WATER
    override val warmGroup = 2
    override val maxX get() = if (flags[F.BRIDGE_DOWN]) 1480f else 960f
    var bridge = 0f
    private var catMode = 0
    private var gearDrop = -1f
    private var catT = 0f

    init {
        ffX[0] = 300f; ffY[0] = 440f
        ffX[1] = 920f; ffY[1] = 440f
        ffX[2] = 1240f; ffY[2] = 640f
        exitLeft(1, 1470f)
        exitRight(3, 140f, { flags[F.BRIDGE_DOWN] }, intArrayOf(Icon.BRIDGE, Icon.QUESTION))
        // water / raised bridge
        hotspots.add(Hot(990f, 120f, 1430f, 790f, 950f, visible = { !flags[F.BRIDGE_DOWN] }) {
            g.think(2f, Icon.LAMP, Icon.BRIDGE, Icon.QUESTION)
        })
        // boat & boatman
        hotspots.add(Hot(1110f, 790f, 1320f, 900f, 950f) {
            if (!flags[F.LAMP2]) { g.then { g.sfx(Sfx.SNORE, 0.6f) }; g.npcThink(1215f, 700f, 1.6f, Icon.ZZZ) }
            else if (!flags[F.GOT_LENS]) {
                g.npcThink(1215f, 680f, 2f, Icon.LENS, Icon.HILL)
                g.pose(Wick.REACH_UP, 0.6f)
                g.then { g.set(F.GOT_LENS); g.give(Icon.LENS) }
                g.pose(Wick.HAPPY, 0.6f)
            } else g.npcThink(1215f, 700f, 1.5f, Icon.HEART)
        })
        // cat guarding a gear
        hotspots.add(Hot(440f, 540f, 620f, 700f, 410f, onItem = { item ->
            if (item == Icon.FISH && !flags[F.FISH_GIVEN]) {
                g.take(Icon.FISH)
                g.pose(Wick.GIVE, 0.6f)
                g.then { g.set(F.FISH_GIVEN); catMode = 1; catT = 0f; g.sfx(Sfx.MEOW, 0.8f, 1.2f) }
                g.wait(0.6f)
                g.tween(0.5f) { gearDrop = lerp(610f, 760f, it * it) }
                g.then { g.sfx(Sfx.CLINK) }
                g.walkTo(600f)
                g.pose(Wick.REACH_DOWN, 0.6f)
                g.then { gearDrop = -1f; g.set(F.GOT_GEAR); g.give(Icon.GEAR) }
                true
            } else false
        }) {
            g.then { g.sfx(Sfx.MEOW, 0.8f, if (flags[F.FISH_GIVEN]) 1.2f else 0.8f) }
            if (!flags[F.FISH_GIVEN]) g.npcThink(515f, 540f, 1.8f, Icon.FISH, Icon.QUESTION) else g.npcThink(515f, 560f, 1.4f, Icon.HEART)
        })
        // bridge control box
        hotspots.add(Hot(815f, 580f, 930f, 770f, 790f) {
            if (flags[F.BRIDGE_DOWN]) g.pose(Wick.NOD, 0.5f)
            else openGears()
        })
        // lamp 2 on the lowered bridge
        hotspots.add(Hot(1320f, 450f, 1440f, 790f, 1320f, visible = { flags[F.BRIDGE_DOWN] }) {
            if (flags[F.LAMP2]) g.think(1.4f, Icon.HEART)
            else g.lightLamp(F.LAMP2, 1400f) {}
        })
    }

    private fun openGears() {
        g.openMini(GearsMini(g, g.has(Icon.GEAR)) {
            g.take(Icon.GEAR)
            g.set(F.BRIDGE_DOWN)
            g.then { g.wick.setPose(Wick.CRANK); g.sfx(Sfx.RUMBLE) }
            g.tween(2.6f) { bridge = smooth(it) }
            g.then { g.wick.setPose(Wick.IDLE); g.sfx(Sfx.CLANK); g.host.haptic(true) }
            g.pose(Wick.HAPPY, 0.8f)
        })
    }

    override fun reset() { bridge = 0f; catMode = 0; gearDrop = -1f }
    override fun enter() {
        bridge = if (flags[F.BRIDGE_DOWN]) 1f else 0f
        catMode = if (flags[F.FISH_GIVEN]) 2 else 0
        gearDrop = -1f
    }

    override fun update(dt: Float, t: Float) {
        catT += dt
        if (catMode == 1 && catT > 4f) catMode = 2
    }

    override fun build(s: Sketch) {
        Parts.farSkyline(s, 1520f, intArrayOf(1, 3))
        Parts.house(s, -400f, 260f, 230f, 120f, 2, 0xFFA99D88.toInt(), 3)
        s.rect(40f, 560f, 200f, 770f, Pal.T4, Pal.INK, 3f)
        s.hatch(40f, 560f, 200f, 770f, 12f)
        Parts.house(s, 1440f, 2100f, 260f, 140f, 2, Pal.T1, 3)
        // quay
        s.rect(-500f, 770f, 1000f, 1300f, Pal.T3, Pal.INK, 3.5f)
        for (k in 0 until 16) s.line(-480f + k * 100f, 770f, -480f + k * 100f, 1000f, withAlpha(Pal.INK, 50), 2f)
        s.line(-500f, 830f, 1000f, 830f, withAlpha(Pal.INK, 60), 2f)
        s.rect(1420f, 770f, 2100f, 1300f, Pal.T3, Pal.INK, 3.5f)
        // pier for the bridge pivot
        s.rect(960f, 690f, 1030f, 1000f, Pal.T2, Pal.INK, 3.5f)
        s.brickPattern(960f, 690f, 1030f, 1000f, 35f, 22f)
        // crates
        s.rect(440f, 690f, 545f, 770f, Pal.T1, Pal.INK, 3f)
        s.line(440f, 690f, 545f, 770f, withAlpha(Pal.INK, 90), 2f)
        s.rect(525f, 700f, 615f, 770f, Pal.T2, Pal.INK, 3f)
        s.rect(470f, 620f, 580f, 690f, Pal.T1, Pal.INK, 3f)
        s.line(470f, 620f, 580f, 690f, withAlpha(Pal.INK, 90), 2f)
        // control box
        s.rect(830f, 610f, 910f, 770f, Pal.T4, Pal.INK, 3.5f)
        s.ellipse(870f, 650f, 22f, 22f, Pal.BRASS, Pal.INK, 3f)
        s.rect(836f, 700f, 904f, 712f, Pal.T3, Pal.INK, 2f)
        // mooring post + rope
        s.rect(300f, 720f, 330f, 790f, Pal.T4, Pal.INK, 3f)
        s.polyline(floatArrayOf(330f, 740f, 380f, 770f, 420f, 790f))
    }

    override fun drawBack(c: Canvas, t: Float) {
        val st = Draw.stroke
        Draw.fill.color = 0xFF4E4A45.toInt()
        c.drawRect(1030f, 782f, 1420f, 1300f, Draw.fill)
        st.color = Pal.INK; st.strokeWidth = 3f
        c.drawLine(1030f, 782f, 1420f, 782f, st)
        val lit = g.warmth[2]
        // water ripples & reflections
        st.strokeWidth = 2f
        for (k in 0 until 9) {
            val y = 800f + k * 14f
            st.color = withAlpha(Pal.PAPER_HI, 50 + k * 4)
            val off = sin(t * 1.3f + k) * 20f
            c.drawLine(1040f + off + (k % 3) * 30f, y, 1110f + off + (k % 3) * 30f, y, st)
            c.drawLine(1200f - off, y + 6f, 1290f - off, y + 6f, st)
        }
        if (lit > 0.01f) {
            for (k in 0 until 6) {
                st.color = withAlpha(Pal.AMBER_HI, (130 * lit).toInt())
                st.strokeWidth = 5f
                val x = 1400f + sin(t * 2f + k) * 10f
                c.drawLine(x - 20f + k * 2f, 810f + k * 18f, x + 20f - k * 3f, 810f + k * 18f, st)
            }
        }
        // boat & boatman (under the bridge)
        val bob = sin(t * 1.4f) * 4f
        Folk.boatman(c, 1210f, 790f + bob, t, if (flags[F.LAMP2]) (if (flags[F.GOT_LENS]) 0.6f else 1f) else 0f)
        Draw.path.reset()
        Draw.path.moveTo(1100f, 790f + bob); Draw.path.lineTo(1320f, 790f + bob); Draw.path.lineTo(1290f, 830f + bob); Draw.path.lineTo(1130f, 830f + bob); Draw.path.close()
        Draw.fill.color = Pal.T5
        c.drawPath(Draw.path, Draw.fill)
        st.color = Pal.INK; st.strokeWidth = 3f
        c.drawPath(Draw.path, st)
        // cat on the crates
        Folk.cat(c, 520f, 620f, t, catMode, lit)
        if (!flags[F.FISH_GIVEN]) {
            Icon.gear(c, 560f, 604f, 12f, 17f, 7, t * 0.8f, Pal.BRASS, Pal.INK, 2.5f)
        } else if (catMode == 1) Icon.draw(c, Icon.FISH, 562f, 616f, 30f, Pal.INK, Pal.T1, t)
        if (gearDrop > 0f) Icon.gear(c, 600f, gearDrop - 16f, 12f, 17f, 7, gearDrop * 0.05f, Pal.BRASS, Pal.INK, 2.5f)
        // townsfolk on far bank once lit
        Folk.towns(c, 1560f, 770f, t, 1, lit * 1.3f - 0.3f)
        Folk.towns(c, 250f, 770f, t, 2, lit * 1.3f - 0.5f)
        // bascule bridge: pivot at (1010, 770)
        val ang = -72f * (1f - bridge)
        c.save()
        c.rotate(ang, 1010f, 770f)
        Draw.fill.color = Pal.T4
        c.drawRect(1000f, 748f, 1420f, 776f, Draw.fill)
        st.color = Pal.INK; st.strokeWidth = 3.5f
        c.drawRect(1000f, 748f, 1420f, 776f, st)
        st.strokeWidth = 2.5f
        for (k in 0 until 9) c.drawLine(1020f + k * 46f, 748f, 1020f + k * 46f, 700f, st)
        c.drawLine(1010f, 700f, 1420f, 700f, st)
        // counterweight behind the pivot
        Draw.fill.color = Pal.T5
        c.drawRect(930f, 752f, 1005f, 800f, Draw.fill)
        c.drawRect(930f, 752f, 1005f, 800f, st)
        c.restore()
        // lamp at the tip of the leaf, always upright
        val rad = Math.toRadians(ang.toDouble())
        val tipX = 1010f + (cos(rad) * 395.0).toFloat()
        val tipY = 762f + (sin(rad) * 395.0).toFloat()
        streetLamp(c, tipX, tipY, tipY - 250f, lit, t, 2f)
    }
}

// ---------------------------------------------------------------------- 3. observatory hill

class Hill(g: Game) : Scene(g, 3) {
    override val ambient = Sfx.AMB_CRICKETS
    override val warmGroup = 3
    private var beam = 0f

    init {
        ffX[0] = 800f; ffY[0] = 360f
        ffX[1] = 300f; ffY[1] = 300f
        ffX[2] = 1400f; ffY[2] = 690f
        exitLeft(2, 1470f)
        exitRight(4, 140f)
        hotspots.add(Hot(170f, 610f, 270f, 770f, 290f) {
            if (flags[F.LAMP3]) g.think(1.4f, Icon.STAR, Icon.HEART) else { g.then { g.sfx(Sfx.KNOCK) }; g.think(1.4f, Icon.ZZZ) }
        })
        hotspots.add(Hot(390f, 540f, 500f, 770f, 360f) {
            if (!flags[F.LAMP3]) { g.then { g.sfx(Sfx.SNORE, 0.6f) }; g.npcThink(450f, 520f, 1.6f, Icon.ZZZ) }
            else if (!flags[F.GOT_KEY]) {
                g.npcThink(450f, 520f, 2.2f, Icon.KEY, Icon.TOWER)
                g.pose(Wick.GIVE, 0.6f)
                g.then { g.set(F.GOT_KEY); g.give(Icon.KEY) }
                g.pose(Wick.HAPPY, 0.6f)
            } else g.npcThink(450f, 520f, 2f, Icon.TELESCOPE, Icon.STAR)
        })
        hotspots.add(Hot(505f, 430f, 690f, 770f, 700f) {
            g.face(600f)
            g.openMini(TelescopeMini(g))
            g.then { flags[F.SAW_STARS] = true; g.save() }
        })
        hotspots.add(Hot(1070f, 280f, 1230f, 770f, 1045f, onItem = { item ->
            if (item == Icon.LENS && !flags[F.LENS_IN]) {
                g.take(Icon.LENS)
                g.pose(Wick.REACH_UP, 0.8f)
                g.then { g.set(F.LENS_IN); g.sfx(Sfx.CLINK) }
                openMirrors()
                true
            } else false
        }) {
            when {
                flags[F.LAMP3] -> g.think(1.4f, Icon.HEART)
                flags[F.LENS_IN] -> openMirrors()
                else -> g.think(2f, Icon.LAMP, Icon.LENS, Icon.QUESTION)
            }
        })
    }

    private fun openMirrors() {
        g.openMini(MirrorMini(g) {
            g.walkTo(1045f)
            g.lightLamp(F.LAMP3, 1150f) {}
        })
    }

    override fun enter() { beam = if (flags[F.LAMP3]) 1f else 0f }
    override fun update(dt: Float, t: Float) { beam = approach(beam, if (flags[F.LAMP3]) 1f else 0f, dt * 0.5f) }

    override fun build(s: Sketch) {
        // the town far below, whose windows show every lamp you've lit
        s.poly(floatArrayOf(820f, 900f, 820f, 600f, 900f, 600f, 900f, 570f, 980f, 570f, 980f, 610f, 1060f, 610f, 1060f, 560f,
            1150f, 560f, 1150f, 600f, 1300f, 600f, 1300f, 540f, 1380f, 540f, 1380f, 590f, 1500f, 590f, 1500f, 550f, 1640f, 550f,
            1640f, 600f, 1800f, 600f, 1800f, 560f, 2100f, 560f, 2100f, 900f), 0xFF524D46.toInt(), withAlpha(Pal.INK, 150), 2f)
        s.poly(floatArrayOf(1560f, 900f, 1560f, 330f, 1540f, 330f, 1590f, 250f, 1640f, 330f, 1620f, 330f, 1620f, 900f), 0xFF4A4640.toInt(), withAlpha(Pal.INK, 150), 2f)
        s.ellipse(1590f, 380f, 18f, 18f, 0xFF6A645B.toInt(), withAlpha(Pal.INK, 150), 1.5f, 4, Pal.AMBER_HI)
        for (k in 0 until 14) {
            val x = 840f + k * 88f
            val y = 590f + (k * 37 % 30)
            s.rect(x, y, x + 12f, y + 14f, Pal.WIN_DARK, 0, 0f, 1 + k % 4)
        }
        // hill
        s.poly(floatArrayOf(-500f, 1300f, -500f, 560f, -200f, 480f, 150f, 450f, 450f, 520f, 700f, 690f, 900f, 770f, 2100f, 770f, 2100f, 1300f), Pal.T3, Pal.INK, 3.5f)
        s.rect(-500f, 770f, 2100f, 1300f, Pal.T3, 0, 0f)
        s.line(-500f, 770f, 2100f, 770f, Pal.INK, 3f)
        for (k in 0 until 40) {
            val x = -450f + k * 65f
            val y = 790f + (k * 53 % 90)
            s.polyline(floatArrayOf(x, y, x + 6f, y - 16f, x + 12f, y))
        }
        // observatory
        s.rect(80f, 470f, 360f, 770f, Pal.T1, Pal.INK, 3.5f)
        s.brickPattern(80f, 470f, 360f, 770f, 50f, 26f)
        s.poly(floatArrayOf(60f, 470f, 70f, 400f, 110f, 350f, 170f, 318f, 220f, 310f, 270f, 318f, 330f, 350f, 370f, 400f, 380f, 470f), Pal.T2, Pal.INK, 3.5f)
        s.poly(floatArrayOf(205f, 312f, 238f, 312f, 244f, 470f, 199f, 470f), Pal.T5, Pal.INK, 2.5f)
        s.rect(172f, 606f, 268f, 770f, Pal.T4, Pal.INK, 3f)
        s.window(110f, 520f, 150f, 580f, 3, Pal.T4, false)
        s.window(290f, 520f, 330f, 580f, 3, Pal.T4, false)
        // telescope on its tripod
        s.poly(floatArrayOf(515f, 555f, 660f, 455f, 676f, 478f, 530f, 578f), Pal.BRASS, Pal.INK, 3f)
        s.line(595f, 520f, 560f, 770f, Pal.INK, 4f)
        s.line(595f, 520f, 640f, 770f, Pal.INK, 4f)
        s.line(595f, 520f, 600f, 770f, Pal.INK, 4f)
        // beacon column
        s.rect(1110f, 420f, 1190f, 770f, Pal.T1, Pal.INK, 3.5f)
        s.brickPattern(1110f, 420f, 1190f, 770f, 40f, 30f)
        s.rect(1090f, 405f, 1210f, 425f, Pal.T3, Pal.INK, 3f)
        s.rect(1098f, 290f, 1202f, 405f, withAlpha(Pal.PAPER_HI, 60), Pal.INK, 3f)
        s.poly(floatArrayOf(1088f, 292f, 1150f, 240f, 1212f, 292f), Pal.T4, Pal.INK, 3f)
        s.line(1150f, 292f, 1150f, 405f, Pal.INK, 2.5f)
        // fence
        for (k in 0 until 6) s.rect(740f + k * 60f, 720f, 752f + k * 60f, 770f, Pal.T4, Pal.INK, 2f)
        s.line(740f, 735f, 1052f, 735f, Pal.INK, 3f)
    }

    override fun drawBehind(c: Canvas, t: Float) {
        g.drawStars(c, 1.2f)
        Draw.fill.color = withAlpha(Pal.PAPER_HI, 230)
        c.drawCircle(1330f, 140f, 50f, Draw.fill)
        Draw.fill.color = Pal.SKY_TOP
        c.drawCircle(1354f, 126f, 46f, Draw.fill)
    }

    override fun drawBack(c: Canvas, t: Float) {
        Folk.astronomer(c, 450f, 770f, t, if (flags[F.LAMP3]) 1f else 0f)
        // lens + beacon light
        if (flags[F.LENS_IN]) {
            c.save(); c.rotate(90f, 1150f, 350f)
            Icon.draw(c, Icon.LENS, 1150f, 350f, 90f, Pal.INK, Pal.BRASS, t)
            c.restore()
        }
        val b = beam
        if (b > 0.01f) {
            val a = sin(t * 0.5f) * 0.6f + PI.toFloat()
            Draw.path.reset()
            Draw.path.moveTo(1150f, 350f)
            Draw.path.lineTo(1150f + cos(a - 0.1f) * 1500f, 350f + sin(a - 0.1f) * 500f)
            Draw.path.lineTo(1150f + cos(a + 0.1f) * 1500f, 350f + sin(a + 0.1f) * 500f)
            Draw.path.close()
            Draw.fill.color = withAlpha(Pal.AMBER_HI, (45 * b).toInt())
            c.drawPath(Draw.path, Draw.fill)
            Draw.glow(c, 1150f, 350f, 420f * b, 0.9f * b)
            Draw.flame(c, 1150f, 372f, 16f * b, t, 3f)
        }
        Folk.towns(c, 880f, 770f, t, 2, g.warmth[3] * 1.3f - 0.3f)
    }
}

// ---------------------------------------------------------------------- 4. the square & clock tower

class Square(g: Game) : Scene(g, 4) {
    override val ambient = Sfx.AMB_WIND
    override val warmGroup = 4
    private var door = 0f
    private var clockA = 0f

    init {
        ffX[0] = 300f; ffY[0] = 500f
        ffX[1] = 1360f; ffY[1] = 360f
        ffX[2] = 700f; ffY[2] = 250f
        exitLeft(3, 1470f)
        hotspots.add(Hot(930f, 40f, 1170f, 300f, 1050f) {
            if (flags[F.CLOCK_RUN]) g.think(1.5f, Icon.CLOCK, Icon.HEART) else g.think(2f, Icon.CLOCK, Icon.NO, Icon.QUESTION)
        })
        hotspots.add(Hot(1300f, 690f, 1460f, 770f, 1280f) { g.think(1.4f, Icon.ZZZ, Icon.NO) })
        hotspots.add(Hot(975f, 540f, 1125f, 770f, 960f, onItem = { item ->
            if (item == Icon.KEY && !flags[F.TOWER_OPEN]) {
                g.take(Icon.KEY)
                g.pose(Wick.GIVE, 0.7f)
                g.then { g.sfx(Sfx.UNLOCK) }
                g.tween(1f) { door = smooth(it) }
                g.then { g.set(F.TOWER_OPEN); g.sfx(Sfx.DOOR) }
                g.pose(Wick.HAPPY, 0.6f)
                true
            } else false
        }) {
            if (flags[F.TOWER_OPEN]) {
                g.walkTo(1050f)
                g.then { g.goScene(5, 230f, 1) }
            } else {
                g.then { g.sfx(Sfx.RATTLE) }
                g.pose(Wick.PUSH, 0.6f)
                g.think(2f, Icon.KEY, Icon.QUESTION)
            }
        })
    }

    override fun enter() { door = if (flags[F.TOWER_OPEN]) 1f else 0f }
    override fun update(dt: Float, t: Float) { if (flags[F.CLOCK_RUN]) clockA += dt * 0.5f }

    override fun build(s: Sketch) {
        Parts.house(s, -400f, 150f, 220f, 130f, 4, Pal.T1, 3)
        Parts.house(s, 170f, 700f, 280f, 150f, 3, 0xFFB0A591.toInt(), 3)
        Parts.house(s, 1300f, 2100f, 250f, 140f, 4, Pal.T1, 3)
        // clock tower
        s.rect(860f, -400f, 1240f, 770f, 0xFFA59A86.toInt(), Pal.INK, 4f)
        s.brickPattern(860f, -400f, 1240f, 770f, 70f, 34f)
        s.rect(840f, 330f, 1260f, 360f, Pal.T3, Pal.INK, 3.5f)
        s.ellipse(1050f, 170f, 128f, 128f, Pal.T4, Pal.INK, 4f)
        s.ellipse(1050f, 170f, 112f, 112f, Pal.PAPER, Pal.INK, 3f, 4, Pal.AMBER_HI)
        for (k in 0 until 12) {
            val a = k * PI.toFloat() / 6f
            s.line(1050f + cos(a) * 92f, 170f + sin(a) * 92f, 1050f + cos(a) * 106f, 170f + sin(a) * 106f, Pal.INK, 3f)
        }
        s.window(920f, 420f, 960f, 500f, 4, Pal.T4, false)
        s.window(1140f, 420f, 1180f, 500f, 4, Pal.T4, false)
        s.poly(floatArrayOf(965f, 770f, 965f, 600f, 985f, 560f, 1050f, 535f, 1115f, 560f, 1135f, 600f, 1135f, 770f), Pal.T5, Pal.INK, 4f)
        // bench
        s.rect(1310f, 700f, 1450f, 716f, Pal.T4, Pal.INK, 3f)
        s.line(1320f, 716f, 1320f, 770f, Pal.INK, 5f)
        s.line(1440f, 716f, 1440f, 770f, Pal.INK, 5f)
        s.rect(1310f, 660f, 1450f, 674f, Pal.T4, Pal.INK, 3f)
        Parts.cobbles(s, 770f)
    }

    override fun drawBack(c: Canvas, t: Float) {
        // clock hands (stopped at five to twelve until the clock runs)
        val st = Draw.stroke
        st.color = Pal.INK; st.strokeWidth = 7f
        val mA = -PI.toFloat() / 2f - 0.52f + clockA
        val hA = -PI.toFloat() / 2f - 0.05f + clockA / 12f
        c.drawLine(1050f, 170f, 1050f + cos(hA) * 60f, 170f + sin(hA) * 60f, st)
        st.strokeWidth = 5f
        c.drawLine(1050f, 170f, 1050f + cos(mA) * 92f, 170f + sin(mA) * 92f, st)
        Draw.fill.color = Pal.INK; c.drawCircle(1050f, 170f, 8f, Draw.fill)
        // tower door
        val d = door
        Draw.fill.color = Pal.T3
        c.save()
        c.clipRect(Draw.r1.also { it.set(975f, 540f, 1125f, 770f) })
        c.drawRect(975f + 150f * d * 0.85f, 540f, 1125f, 770f, Draw.fill)
        st.strokeWidth = 3f
        c.drawRect(975f + 150f * d * 0.85f, 540f, 1125f, 770f, st)
        c.restore()
        if (d < 0.1f) Icon.draw(c, Icon.KEY, 1100f, 670f, 30f, Pal.INK, Pal.BRASS, t)
        if (d > 0.5f) Draw.glow(c, 1050f, 680f, 120f, 0.3f * d)
        // townsfolk gather as the lamps come on
        val lamps = g.lampsLit()
        val xs = floatArrayOf(300f, 520f, 1400f, 760f, 1500f, 420f, 1250f, 640f)
        for (k in 0 until 8) Folk.towns(c, xs[k], 770f, t, k, if (k < lamps * 2) 1f else 0f)
        if (g.flags[F.LAMP4]) Draw.glow(c, 1050f, 170f, 300f, 0.7f)
    }
}

// ---------------------------------------------------------------------- 5. inside the tower top

class TowerTop(g: Game) : Scene(g, 5) {
    override val ambient = Sfx.AMB_TICK
    override val warmGroup = 4
    override val minX get() = 150f
    override val maxX get() = 1450f
    private var gearA = 0f
    private var pend = 0f
    private var wickUp = 0f

    init {
        ffX[0] = 260f; ffY[0] = 320f
        ffX[1] = 820f; ffY[1] = 620f
        ffX[2] = 1380f; ffY[2] = 250f
        hotspots.add(Hot(-400f, 380f, 200f, 900f, 150f, exit = true) { g.goScene(4, 1000f, -1) })
        hotspots.add(Hot(350f, 500f, 510f, 770f, 540f) {
            if (flags[F.CLOCK_RUN]) g.pose(Wick.NOD, 0.5f)
            else g.openMini(RingsMini(g) {
                g.set(F.CLOCK_RUN)
                g.sfx(Sfx.CHIME)
                g.host.haptic(true)
                g.pose(Wick.HAPPY, 1f)
            })
        })
        hotspots.add(Hot(1050f, 300f, 1250f, 770f, 1010f) {
            when {
                flags[F.LAMP4] -> g.think(1.6f, Icon.HEART, Icon.FLAME)
                !flags[F.CLOCK_RUN] -> {
                    g.then { g.wick.setPose(Wick.RAISE_POLE) }
                    g.wait(0.7f)
                    g.then { g.wick.setPose(Wick.IDLE) }
                    g.think(2.2f, Icon.CLOCK, Icon.GEAR, Icon.QUESTION)
                }
                else -> finale()
            }
        })
    }

    private fun finale() {
        g.face(1150f)
        g.then { g.wick.setPose(Wick.RAISE_POLE) }
        g.wait(0.6f)
        // Wick gives his own flame to the great lamp...
        g.sfxAct(Sfx.WHOOSH)
        g.tween(1.6f) { p -> g.wick.flameTarget = 1f - 0.85f * p; g.wick.flame = g.wick.flameTarget; g.wick.poleFlame = p * 1.3f }
        g.wait(0.4f)
        g.then {
            g.flags[F.LAMP4] = true
            g.sfx(Sfx.LAMP_ON); g.sfx(Sfx.CHIME, 0.8f)
            g.host.haptic(true)
            g.save()
            g.updateAudioState()
        }
        g.tween(0.5f) { g.wick.poleFlame = 1.3f * (1f - it) }
        g.wait(1.4f)
        // ...and the great lamp gives it back, brighter
        g.sfxAct(Sfx.IGNITE)
        g.tween(1.2f) { p -> g.wick.flameTarget = 0.15f + 1.05f * p; g.wick.flame = g.wick.flameTarget }
        g.then { g.wick.setPose(Wick.HAPPY) }
        g.wait(2f)
        g.then { g.wick.setPose(Wick.IDLE); g.wick.flameTarget = 1f; g.startEnding() }
    }

    override fun enter() { wickUp = if (flags[F.CLOCK_RUN]) 1f else 0f }

    override fun update(dt: Float, t: Float) {
        if (flags[F.CLOCK_RUN]) {
            gearA += dt * 0.6f
            pend += dt
            wickUp = approach(wickUp, 1f, dt * 0.4f)
        }
    }

    override fun build(s: Sketch) {
        s.rect(-500f, -400f, 2100f, 770f, 0xFF7E7568.toInt(), Pal.INK, 0f)
        s.brickPattern(-500f, -400f, 2100f, 770f, 90f, 44f, withAlpha(Pal.INK, 60))
        // the clock face seen from inside (a big translucent disc)
        s.ellipse(800f, 250f, 210f, 210f, Pal.T4, Pal.INK, 4f)
        s.ellipse(800f, 250f, 192f, 192f, 0xFF8F887C.toInt(), Pal.INK, 3f, 4, Pal.AMBER_HI)
        for (k in 0 until 12) {
            val a = k * PI.toFloat() / 6f
            s.line(800f + cos(a) * 160f, 250f + sin(a) * 160f, 800f + cos(a) * 185f, 250f + sin(a) * 185f, Pal.INK, 4f)
        }
        // floor
        s.rect(-500f, 770f, 2100f, 1300f, Pal.T4, Pal.INK, 3.5f)
        for (k in 0 until 26) s.line(-480f + k * 100f, 770f, -480f + k * 100f - 40f, 1000f, withAlpha(Pal.INK, 70), 2f)
        // stairs going down on the left
        for (k in 0 until 6) s.rect(-100f + k * 40f, 770f - k * 0f, 200f, 790f + k * 22f, Pal.T3, Pal.INK, 2.5f)
        s.rect(-120f, 400f, 150f, 770f, 0xFF5A534A.toInt(), Pal.INK, 3f)
        s.hatch(-120f, 400f, 150f, 770f, 14f)
        // mechanism cabinet
        s.rect(360f, 520f, 500f, 770f, Pal.BRASS_DK, Pal.INK, 3.5f)
        s.ellipse(430f, 600f, 44f, 44f, Pal.BRASS, Pal.INK, 3f)
        s.ellipse(430f, 600f, 28f, 28f, Pal.T2, Pal.INK, 2.5f)
        s.ellipse(430f, 600f, 12f, 12f, Pal.T4, Pal.INK, 2f)
        s.rect(380f, 670f, 480f, 750f, Pal.T4, Pal.INK, 2.5f)
        // great lamp pedestal
        s.rect(1080f, 610f, 1220f, 770f, Pal.T2, Pal.INK, 3.5f)
        s.brickPattern(1080f, 610f, 1220f, 770f, 35f, 26f)
        s.rect(1060f, 590f, 1240f, 614f, Pal.T4, Pal.INK, 3f)
        // arched window slit
        s.poly(floatArrayOf(1420f, 500f, 1420f, 300f, 1450f, 260f, 1480f, 300f, 1480f, 500f), Pal.SKY_MID, Pal.INK, 3f)
    }

    override fun drawBack(c: Canvas, t: Float) {
        val run = flags[F.CLOCK_RUN]
        // wall gears
        Icon.gear(c, 250f, 180f, 90f, 110f, 12, gearA, Pal.T2, Pal.INK, 3.5f)
        Icon.gear(c, 395f, 250f, 55f, 70f, 8, -gearA * 1.6f + 0.2f, Pal.BRASS_DK, Pal.INK, 3.5f)
        Icon.gear(c, 1300f, 140f, 70f, 88f, 10, -gearA * 1.2f, Pal.T2, Pal.INK, 3.5f)
        // pendulum
        val sw = if (run) sin(pend * 2.6f) * 0.32f else 0.08f
        c.save()
        c.rotate(Math.toDegrees(sw.toDouble()).toFloat(), 800f, 250f)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 7f
        c.drawLine(800f, 250f, 800f, 640f, Draw.stroke)
        Draw.fill.color = Pal.BRASS
        c.drawCircle(800f, 660f, 44f, Draw.fill)
        Draw.stroke.strokeWidth = 4f
        c.drawCircle(800f, 660f, 44f, Draw.stroke)
        c.drawCircle(800f, 660f, 26f, Draw.stroke)
        c.restore()
        Draw.fill.color = Pal.INK; c.drawCircle(800f, 250f, 12f, Draw.fill)
        // the great lamp
        val lit = g.warmth[4]
        if (lit > 0.01f) Draw.glow(c, 1150f, 440f, 900f * lit, 0.95f * lit)
        Draw.path.reset()
        Draw.path.moveTo(1085f, 590f); Draw.path.lineTo(1060f, 330f); Draw.path.lineTo(1240f, 330f); Draw.path.lineTo(1215f, 590f); Draw.path.close()
        Draw.fill.color = lerpColor(withAlpha(Pal.PAPER_HI, 110), Pal.AMBER_HI, lit)
        c.drawPath(Draw.path, Draw.fill)
        // wick holder rises when the clock runs
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 6f
        val wy = lerp(580f, 470f, smooth(wickUp))
        c.drawLine(1150f, 590f, 1150f, wy, Draw.stroke)
        Draw.fill.color = Pal.T5
        c.drawRect(1136f, wy - 12f, 1164f, wy, Draw.fill)
        if (lit > 0.01f) Draw.flame(c, 1150f, wy - 10f, 42f * lit, t, 5f)
        Draw.stroke.strokeWidth = 5f
        c.drawPath(Draw.path, Draw.stroke)
        for (k in 1..3) c.drawLine(1060f + k * 45f - (k - 2) * 6f, 330f, 1085f + k * 32f - (k - 2) * 2f, 590f, Draw.stroke.also { it.strokeWidth = 3f })
        Draw.stroke.strokeWidth = 5f
        Draw.path.reset()
        Draw.path.moveTo(1040f, 332f); Draw.path.lineTo(1150f, 250f); Draw.path.lineTo(1260f, 332f); Draw.path.close()
        Draw.fill.color = Pal.T5
        c.drawPath(Draw.path, Draw.fill); c.drawPath(Draw.path, Draw.stroke)
        c.drawCircle(1150f, 236f, 14f, Draw.stroke)
    }
}
