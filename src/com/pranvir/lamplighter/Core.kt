package com.pranvir.lamplighter

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ====================================================================== math

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
fun smooth(t: Float): Float { val x = clamp01(t); return x * x * (3f - 2f * x) }
fun approach(v: Float, target: Float, rate: Float): Float =
    if (v < target) min(target, v + rate) else max(target, v - rate)

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val tt = clamp01(t)
    val aa = (a ushr 24) and 255; val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
    val ba = (b ushr 24) and 255; val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
    return ((aa + (ba - aa) * tt).toInt() shl 24) or ((ar + (br - ar) * tt).toInt() shl 16) or
        ((ag + (bg - ag) * tt).toInt() shl 8) or (ab + (bb - ab) * tt).toInt()
}

fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
fun alphaF(c: Int, f: Float): Int = withAlpha(c, (((c ushr 24) and 255) * clamp01(f)).toInt())

// ====================================================================== palette

/** Sepia & charcoal. Amber light is the only colour in the world. */
object Pal {
    const val PAPER = 0xFFDAD1BF.toInt()
    const val PAPER_HI = 0xFFE8E0CF.toInt()
    const val T1 = 0xFFBDB29E.toInt()
    const val T2 = 0xFF9A8F7C.toInt()
    const val T3 = 0xFF71685B.toInt()
    const val T4 = 0xFF4B453E.toInt()
    const val T5 = 0xFF34302B.toInt()
    const val INK = 0xFF221E1B.toInt()
    const val SKY_TOP = 0xFF3B3C44.toInt()
    const val SKY_MID = 0xFF6A6560.toInt()
    const val SKY_LOW = 0xFF9C9281.toInt()
    const val BRASS = 0xFFB59A6B.toInt()
    const val BRASS_DK = 0xFF7D6A48.toInt()
    const val TIN = 0xFFC5BDAE.toInt()
    const val TIN_DK = 0xFF8E8677.toInt()
    const val AMBER = 0xFFFFB347.toInt()
    const val AMBER_HI = 0xFFFFDC8A.toInt()
    const val AMBER_DK = 0xFFE07B26.toInt()
    const val WIN_DARK = 0xFF3E3934.toInt()
}

object Fonts {
    var title: Typeface = Typeface.SERIF
}

// ====================================================================== sketchy shapes

/** A static, pre-built piece of scene art. glow >= 0 means the fill warms up with that lamp group. */
class Shape(val path: Path, val fill: Int, val stroke: Int, val strokeW: Float, val glow: Int, val glowCol: Int)

/**
 * Builds hand-drawn looking paths. Each scene is built three times with different jitter
 * so that lines can "boil" like frames of hand-drawn animation.
 */
class Sketch(seed: Long, private val amp: Float) {
    private val rnd = Random(seed)
    val shapes = ArrayList<Shape>(256)

    private fun j() = (rnd.nextFloat() - 0.5f) * 2f * amp

    fun polyPath(pts: FloatArray, closed: Boolean, seg: Float = 26f, p: Path = Path()): Path {
        val n = pts.size / 2
        var first = true
        val edges = if (closed) n else n - 1
        for (e in 0 until edges) {
            val x0 = pts[e * 2]; val y0 = pts[e * 2 + 1]
            val x1 = pts[((e + 1) % n) * 2]; val y1 = pts[((e + 1) % n) * 2 + 1]
            val len = hypot(x1 - x0, y1 - y0)
            val steps = max(1, (len / seg).toInt())
            for (k in 0 until steps) {
                val t = k / steps.toFloat()
                val x = lerp(x0, x1, t) + j()
                val y = lerp(y0, y1, t) + j()
                if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            }
        }
        if (closed) p.close() else p.lineTo(pts[pts.size - 2] + j(), pts[pts.size - 1] + j())
        return p
    }

    fun poly(pts: FloatArray, fill: Int, stroke: Int = Pal.INK, sw: Float = 3f, glow: Int = -1, glowCol: Int = Pal.AMBER) {
        shapes.add(Shape(polyPath(pts, true), fill, stroke, sw, glow, glowCol))
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, fill: Int, stroke: Int = Pal.INK, sw: Float = 3f, glow: Int = -1, glowCol: Int = Pal.AMBER) =
        poly(floatArrayOf(l, t, r, t, r, b, l, b), fill, stroke, sw, glow, glowCol)

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: Int, stroke: Int = Pal.INK, sw: Float = 3f, glow: Int = -1, glowCol: Int = Pal.AMBER) {
        val n = max(10, ((rx + ry) / 7f).toInt())
        val pts = FloatArray(n * 2)
        for (k in 0 until n) {
            val a = k * 2 * PI / n
            pts[k * 2] = cx + (cos(a) * rx).toFloat()
            pts[k * 2 + 1] = cy + (sin(a) * ry).toFloat()
        }
        shapes.add(Shape(polyPath(pts, true, 1000f), fill, stroke, sw, glow, glowCol))
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, stroke: Int = Pal.INK, sw: Float = 3f) {
        shapes.add(Shape(polyPath(floatArrayOf(x0, y0, x1, y1), false), 0, stroke, sw, -1, 0))
    }

    fun polyline(pts: FloatArray, stroke: Int = Pal.INK, sw: Float = 3f) {
        shapes.add(Shape(polyPath(pts, false), 0, stroke, sw, -1, 0))
    }

    /** Diagonal charcoal hatching inside an axis-aligned rectangle. */
    fun hatch(l: Float, t: Float, r: Float, b: Float, spacing: Float, col: Int = withAlpha(Pal.INK, 70), sw: Float = 1.6f) {
        val p = Path()
        val h = b - t
        var x = l - h
        while (x < r) {
            // line from (x, b) to (x + h, t), clipped to [l, r]
            var xa = x; var ya = b; var xb = x + h; var yb = t
            if (xa < l) { ya -= (l - xa); xa = l }
            if (xb > r) { yb += (xb - r); xb = r }
            if (xa < xb) {
                p.moveTo(xa + j() * 0.3f, ya)
                p.lineTo(xb + j() * 0.3f, yb)
            }
            x += spacing
        }
        shapes.add(Shape(p, 0, col, sw, -1, 0))
    }

    /** A window that glows when its lamp group is lit. */
    fun window(l: Float, t: Float, r: Float, b: Float, group: Int, frame: Int = Pal.T4, panes: Boolean = true) {
        rect(l - 5f, t - 5f, r + 5f, b + 5f, frame, Pal.INK, 2.5f)
        rect(l, t, r, b, Pal.WIN_DARK, Pal.INK, 2f, group)
        if (panes) {
            line((l + r) / 2f, t, (l + r) / 2f, b, Pal.INK, 2.5f)
            line(l, (t + b) / 2f, r, (t + b) / 2f, Pal.INK, 2.5f)
        }
    }

    fun brickPattern(l: Float, t: Float, r: Float, b: Float, bw: Float, bh: Float, col: Int = withAlpha(Pal.INK, 45)) {
        val p = Path()
        var y = t + bh
        var row = 0
        while (y < b) {
            p.moveTo(l, y + j() * 0.3f); p.lineTo(r, y + j() * 0.3f)
            var x = l + (if (row % 2 == 0) bw * 0.5f else 0f)
            while (x < r) {
                p.moveTo(x, y); p.lineTo(x + j() * 0.3f, y - bh)
                x += bw
            }
            y += bh; row++
        }
        shapes.add(Shape(p, 0, col, 1.4f, -1, 0))
    }
}

/** Three boiling variants of a piece of static art. */
class Boil(val variants: Array<ArrayList<Shape>>)

fun buildBoil(seed: Long, amp: Float, build: (Sketch) -> Unit): Boil {
    val v = Array(3) { k ->
        val s = Sketch(seed * 31 + k * 7919, amp)
        build(s)
        s.shapes
    }
    return Boil(v)
}

object Draw {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val path = Path()
    val path2 = Path()
    val r1 = RectF()
    val r2 = RectF()
    private val fm = Paint.FontMetrics()

    fun shapes(c: Canvas, list: ArrayList<Shape>, warmth: FloatArray) {
        for (i in 0 until list.size) {
            val s = list[i]
            if (s.fill != 0) {
                fill.color = if (s.glow >= 0 && s.glow < warmth.size) lerpColor(s.fill, s.glowCol, warmth[s.glow]) else s.fill
                c.drawPath(s.path, fill)
            }
            if (s.strokeW > 0f && s.stroke != 0) {
                stroke.color = s.stroke
                stroke.strokeWidth = s.strokeW
                c.drawPath(s.path, stroke)
            }
        }
    }

    private val glowCache = HashMap<Int, RadialGradient>()
    private val glowM = Matrix()

    /** Soft amber light pool. One cached unit gradient per colour, positioned with a local matrix. */
    fun glow(c: Canvas, x: Float, y: Float, r: Float, strength: Float, col: Int = Pal.AMBER) {
        if (strength <= 0.01f || r <= 1f) return
        var sh = glowCache[col]
        if (sh == null) {
            sh = RadialGradient(0f, 0f, 1f,
                intArrayOf(withAlpha(col, 175), withAlpha(col, 72), withAlpha(col, 0)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            glowCache[col] = sh
        }
        glowM.setScale(r, r)
        glowM.postTranslate(x, y)
        sh.setLocalMatrix(glowM)
        glowP.shader = sh
        glowP.alpha = (255 * clamp01(strength)).toInt()
        c.drawCircle(x, y, r, glowP)
    }

    /** A living flame. */
    fun flame(c: Canvas, x: Float, y: Float, size: Float, time: Float, seed: Float = 0f) {
        if (size <= 0.5f) return
        val f1 = sin(time * 17f + seed) * 0.12f + sin(time * 29f + seed * 2f) * 0.08f
        val h = size * (1.6f + f1)
        val w = size * 0.62f
        val sway = sin(time * 7f + seed) * size * 0.18f
        path.reset()
        path.moveTo(x + sway, y - h)
        path.cubicTo(x + w * 1.1f, y - h * 0.45f, x + w, y, x, y)
        path.cubicTo(x - w, y, x - w * 1.1f, y - h * 0.45f, x + sway, y - h)
        path.close()
        fill.color = Pal.AMBER_DK
        c.drawPath(path, fill)
        path.reset()
        val h2 = h * 0.62f; val w2 = w * 0.55f
        path.moveTo(x + sway * 0.6f, y - h2)
        path.cubicTo(x + w2 * 1.1f, y - h2 * 0.4f, x + w2, y, x, y)
        path.cubicTo(x - w2, y, x - w2 * 1.1f, y - h2 * 0.4f, x + sway * 0.6f, y - h2)
        path.close()
        fill.color = Pal.AMBER_HI
        c.drawPath(path, fill)
    }

    fun title(c: Canvas, s: String, x: Float, cy: Float, size: Float, col: Int, face: Typeface = Fonts.title) {
        text.typeface = face; text.textSize = size; text.color = col
        text.getFontMetrics(fm)
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    fun textWidth(s: String, size: Float, face: Typeface = Fonts.title): Float {
        text.typeface = face; text.textSize = size
        return text.measureText(s)
    }
}

// ====================================================================== paper & grain

/** Paper texture, a slow charcoal grain and a soft vignette laid over everything. */
class Paper {
    private val rng = Random(5)
    private val size = 192
    private val grain = arrayOfNulls<BitmapShader>(3)
    private val gp = Paint().apply { isFilterBitmap = false }
    private val m = Matrix()
    private val vig = Paint(Paint.ANTI_ALIAS_FLAG)
    private var w = 1f
    private var h = 1f
    private var dp = 1f
    private var idx = 0
    private var ox = 0f
    private var oy = 0f
    private var clock = 0f
    private var ready = false
    // floating dust motes
    private val nMotes = 26
    private val mx = FloatArray(nMotes); private val my = FloatArray(nMotes); private val mv = FloatArray(nMotes); private val ms = FloatArray(nMotes)
    private val motePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun resize(width: Int, height: Int, density: Float) {
        w = max(1, width).toFloat(); h = max(1, height).toFloat(); dp = max(0.5f, density)
        if (grain[0] == null) {
            val px = IntArray(size * size)
            for (g in 0 until 3) {
                for (i in px.indices) {
                    val v = rng.nextFloat()
                    px[i] = when {
                        v < 0.05f -> ((18 + rng.nextInt(40)) shl 24) or 0x1A1512
                        v < 0.075f -> ((20 + rng.nextInt(40)) shl 24) or 0xF4ECDD
                        else -> 0
                    }
                }
                // paper fibres
                repeat(26) {
                    var x = rng.nextFloat() * size; var y = rng.nextFloat() * size
                    val a = rng.nextFloat() * 6.28f
                    val col = ((22 + rng.nextInt(25)) shl 24) or 0x1A1512
                    repeat(6 + rng.nextInt(10)) {
                        val xi = ((x.toInt() % size) + size) % size; val yi = ((y.toInt() % size) + size) % size
                        px[yi * size + xi] = col
                        x += cos(a); y += sin(a)
                    }
                }
                val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                b.setPixels(px, 0, size, 0, 0, size, size)
                grain[g] = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        }
        vig.shader = RadialGradient(w / 2f, h / 2f, hypot(w, h) * 0.58f,
            intArrayOf(0x00000000, 0x00000000, 0x3A140E08, 0xA0140E08.toInt()),
            floatArrayOf(0f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
        for (k in 0 until nMotes) { mx[k] = rng.nextFloat() * w; my[k] = rng.nextFloat() * h; mv[k] = 0.3f + rng.nextFloat(); ms[k] = 0.6f + rng.nextFloat() * 1.6f }
        ready = true
    }

    fun update(dt: Float) {
        clock += dt
        if (clock > 1f / 12f) {
            clock = 0f
            idx = rng.nextInt(3)
            ox = rng.nextFloat() * size; oy = rng.nextFloat() * size
        }
        for (k in 0 until nMotes) {
            my[k] -= mv[k] * 8f * dp * dt
            mx[k] += sin(my[k] * 0.01f + k) * 6f * dp * dt
            if (my[k] < -10f) { my[k] = h + 10f; mx[k] = rng.nextFloat() * w }
        }
    }

    fun draw(c: Canvas, motes: Float) {
        if (!ready) return
        val sh = grain[idx] ?: return
        val sc = max(1f, dp / 1.4f)
        m.setScale(sc, sc)
        m.postTranslate(-ox * sc, -oy * sc)
        sh.setLocalMatrix(m)
        gp.shader = sh
        c.drawRect(0f, 0f, w, h, gp)
        if (motes > 0.01f) {
            for (k in 0 until nMotes) {
                motePaint.color = withAlpha(Pal.AMBER_HI, (70 * motes * (0.5f + 0.5f * sin(my[k] * 0.02f))).toInt())
                c.drawCircle(mx[k], my[k], ms[k] * dp, motePaint)
            }
        }
        c.drawRect(0f, 0f, w, h, vig)
    }
}

// ====================================================================== pictograms

object Icon {
    // items
    const val POLE = 1
    const val OILCAN = 2
    const val COIN = 3
    const val VALVE = 4
    const val FISH = 5
    const val GEAR = 6
    const val LENS = 7
    const val KEY = 8
    // pictograms
    const val STOOL = 20
    const val DOOR = 21
    const val LAMP = 22
    const val TOWER = 23
    const val FLAME = 24
    const val HEART = 25
    const val QUESTION = 26
    const val NO = 27
    const val ZZZ = 28
    const val BRIDGE = 29
    const val CLOCK = 30
    const val TELESCOPE = 31
    const val STAR = 32
    const val CROW = 33
    const val CAT = 34
    const val YES = 35
    const val ARROW_R = 36
    const val MIRROR = 37
    const val HILL = 38
    // ui
    const val UI_PAUSE = 50
    const val UI_PLAY = 51
    const val UI_SOUND = 52
    const val UI_MUSIC = 53
    const val UI_HOME = 54
    const val UI_CLOSE = 55
    const val UI_RESTART = 56
    const val FIREFLY = 57

    private val p = Path()
    private val r = RectF()

    /** Draws a pictogram centred at (x,y) fitting roughly a box of size s. */
    fun draw(c: Canvas, id: Int, x: Float, y: Float, s: Float, ink: Int = Pal.INK, fillCol: Int = Pal.BRASS, time: Float = 0f, off: Boolean = false) {
        val f = Draw.fill
        val st = Draw.stroke
        val lw = s * 0.07f
        st.color = ink; st.strokeWidth = lw
        f.color = fillCol
        when (id) {
            POLE -> {
                st.strokeWidth = lw * 1.2f
                c.drawLine(x - s * 0.4f, y + s * 0.42f, x + s * 0.3f, y - s * 0.32f, st)
                r.set(x + s * 0.22f, y - s * 0.48f, x + s * 0.46f, y - s * 0.24f)
                c.drawArc(r, 180f, 200f, false, st)
                Draw.flame(c, x + s * 0.3f, y - s * 0.3f, s * 0.1f, time)
            }
            OILCAN -> {
                p.reset()
                p.moveTo(x - s * 0.32f, y + s * 0.38f); p.lineTo(x + s * 0.2f, y + s * 0.38f)
                p.lineTo(x + s * 0.16f, y - s * 0.02f); p.lineTo(x - s * 0.28f, y - s * 0.02f); p.close()
                c.drawPath(p, f); c.drawPath(p, st)
                c.drawLine(x + s * 0.12f, y + s * 0.05f, x + s * 0.46f, y - s * 0.36f, st)
                r.set(x - s * 0.34f, y - s * 0.26f, x - s * 0.02f, y + s * 0.12f)
                c.drawArc(r, 180f, 180f, false, st)
                f.color = ink; c.drawCircle(x - s * 0.06f, y - s * 0.06f, s * 0.05f, f)
            }
            COIN -> {
                c.drawCircle(x, y, s * 0.36f, f); c.drawCircle(x, y, s * 0.36f, st)
                c.drawCircle(x, y, s * 0.24f, st)
                Draw.fill.color = withAlpha(Pal.PAPER_HI, 200)
                c.drawCircle(x - s * 0.12f, y - s * 0.12f, s * 0.06f, Draw.fill)
            }
            VALVE -> {
                c.drawCircle(x, y, s * 0.38f, st)
                st.strokeWidth = lw * 0.8f
                for (k in 0 until 4) {
                    val a = k * PI / 2 + PI / 4
                    c.drawLine(x, y, x + (cos(a) * s * 0.38f).toFloat(), y + (sin(a) * s * 0.38f).toFloat(), st)
                }
                c.drawCircle(x, y, s * 0.1f, f); c.drawCircle(x, y, s * 0.1f, st)
            }
            FISH -> {
                p.reset()
                p.moveTo(x - s * 0.34f, y)
                p.quadTo(x - s * 0.05f, y - s * 0.3f, x + s * 0.24f, y)
                p.quadTo(x - s * 0.05f, y + s * 0.3f, x - s * 0.34f, y)
                p.close()
                c.drawPath(p, f); c.drawPath(p, st)
                p.reset()
                p.moveTo(x + s * 0.2f, y); p.lineTo(x + s * 0.44f, y - s * 0.18f); p.lineTo(x + s * 0.44f, y + s * 0.18f); p.close()
                c.drawPath(p, f); c.drawPath(p, st)
                Draw.fill.color = ink; c.drawCircle(x - s * 0.2f, y - s * 0.04f, s * 0.04f, Draw.fill)
            }
            GEAR -> gear(c, x, y, s * 0.3f, s * 0.4f, 10, time * 0.5f, fillCol, ink, lw)
            LENS -> {
                r.set(x - s * 0.22f, y - s * 0.4f, x + s * 0.22f, y + s * 0.4f)
                Draw.fill.color = withAlpha(Pal.PAPER_HI, 200)
                c.drawOval(r, Draw.fill); c.drawOval(r, st)
                st.strokeWidth = lw * 0.6f
                r.set(x - s * 0.13f, y - s * 0.3f, x + s * 0.1f, y + s * 0.3f)
                c.drawArc(r, 120f, 100f, false, st)
            }
            KEY -> {
                c.drawCircle(x - s * 0.22f, y, s * 0.16f, f); c.drawCircle(x - s * 0.22f, y, s * 0.16f, st)
                c.drawCircle(x - s * 0.22f, y, s * 0.06f, st)
                st.strokeWidth = lw * 1.2f
                c.drawLine(x - s * 0.06f, y, x + s * 0.42f, y, st)
                c.drawLine(x + s * 0.3f, y, x + s * 0.3f, y + s * 0.14f, st)
                c.drawLine(x + s * 0.4f, y, x + s * 0.4f, y + s * 0.18f, st)
            }
            STOOL -> {
                r.set(x - s * 0.34f, y - s * 0.22f, x + s * 0.34f, y - s * 0.1f)
                c.drawRect(r, f); c.drawRect(r, st)
                c.drawLine(x - s * 0.26f, y - s * 0.1f, x - s * 0.32f, y + s * 0.38f, st)
                c.drawLine(x + s * 0.26f, y - s * 0.1f, x + s * 0.32f, y + s * 0.38f, st)
                c.drawLine(x - s * 0.28f, y + s * 0.14f, x + s * 0.28f, y + s * 0.14f, st)
            }
            DOOR -> {
                r.set(x - s * 0.24f, y - s * 0.42f, x + s * 0.24f, y + s * 0.42f)
                c.drawRect(r, f); c.drawRect(r, st)
                Draw.fill.color = ink; c.drawCircle(x + s * 0.14f, y + s * 0.04f, s * 0.04f, Draw.fill)
            }
            LAMP -> {
                c.drawLine(x, y + s * 0.46f, x, y - s * 0.1f, st)
                p.reset()
                p.moveTo(x - s * 0.16f, y - s * 0.1f); p.lineTo(x + s * 0.16f, y - s * 0.1f)
                p.lineTo(x + s * 0.2f, y - s * 0.38f); p.lineTo(x - s * 0.2f, y - s * 0.38f); p.close()
                Draw.fill.color = if (off) Pal.T3 else Pal.AMBER_HI
                c.drawPath(p, Draw.fill); c.drawPath(p, st)
                c.drawLine(x - s * 0.26f, y - s * 0.4f, x + s * 0.26f, y - s * 0.4f, st)
            }
            TOWER -> {
                p.reset()
                p.moveTo(x - s * 0.2f, y + s * 0.46f); p.lineTo(x - s * 0.16f, y - s * 0.18f)
                p.lineTo(x, y - s * 0.46f); p.lineTo(x + s * 0.16f, y - s * 0.18f); p.lineTo(x + s * 0.2f, y + s * 0.46f); p.close()
                c.drawPath(p, f); c.drawPath(p, st)
                Draw.fill.color = Pal.PAPER_HI
                c.drawCircle(x, y - s * 0.02f, s * 0.11f, Draw.fill); c.drawCircle(x, y - s * 0.02f, s * 0.11f, st)
                c.drawLine(x, y - s * 0.02f, x, y - s * 0.1f, st)
            }
            FLAME -> Draw.flame(c, x, y + s * 0.34f, s * 0.36f, time)
            HEART -> {
                p.reset()
                p.moveTo(x, y + s * 0.34f)
                p.cubicTo(x - s * 0.5f, y, x - s * 0.3f, y - s * 0.42f, x, y - s * 0.16f)
                p.cubicTo(x + s * 0.3f, y - s * 0.42f, x + s * 0.5f, y, x, y + s * 0.34f)
                p.close()
                Draw.fill.color = Pal.AMBER
                c.drawPath(p, Draw.fill); c.drawPath(p, st)
            }
            QUESTION -> {
                st.strokeWidth = lw * 1.4f
                r.set(x - s * 0.2f, y - s * 0.4f, x + s * 0.2f, y)
                c.drawArc(r, 180f, 270f, false, st)
                c.drawLine(x, y, x, y + s * 0.14f, st)
                Draw.fill.color = ink; c.drawCircle(x, y + s * 0.34f, s * 0.05f, Draw.fill)
            }
            NO -> {
                st.strokeWidth = lw * 1.5f
                c.drawLine(x - s * 0.3f, y - s * 0.3f, x + s * 0.3f, y + s * 0.3f, st)
                c.drawLine(x + s * 0.3f, y - s * 0.3f, x - s * 0.3f, y + s * 0.3f, st)
            }
            YES -> {
                st.strokeWidth = lw * 1.5f
                c.drawLine(x - s * 0.32f, y, x - s * 0.08f, y + s * 0.26f, st)
                c.drawLine(x - s * 0.08f, y + s * 0.26f, x + s * 0.36f, y - s * 0.3f, st)
            }
            ZZZ -> {
                st.strokeWidth = lw
                for (k in 0 until 3) {
                    val zs = s * (0.14f + k * 0.05f)
                    val zx = x - s * 0.25f + k * s * 0.22f
                    val zy = y + s * 0.2f - k * s * 0.22f
                    c.drawLine(zx - zs, zy - zs, zx + zs, zy - zs, st)
                    c.drawLine(zx + zs, zy - zs, zx - zs, zy + zs, st)
                    c.drawLine(zx - zs, zy + zs, zx + zs, zy + zs, st)
                }
            }
            BRIDGE -> {
                st.strokeWidth = lw
                r.set(x - s * 0.44f, y - s * 0.2f, x + s * 0.44f, y + s * 0.6f)
                c.drawArc(r, 180f, 180f, false, st)
                c.drawLine(x - s * 0.46f, y + s * 0.2f, x + s * 0.46f, y + s * 0.2f, st)
                c.drawLine(x - s * 0.46f, y - s * 0.04f, x + s * 0.46f, y - s * 0.04f, st)
            }
            CLOCK -> {
                Draw.fill.color = Pal.PAPER_HI
                c.drawCircle(x, y, s * 0.4f, Draw.fill); c.drawCircle(x, y, s * 0.4f, st)
                c.drawLine(x, y, x, y - s * 0.26f, st)
                c.drawLine(x, y, x + s * 0.18f, y + s * 0.08f, st)
            }
            TELESCOPE -> {
                st.strokeWidth = lw * 1.8f
                c.drawLine(x - s * 0.36f, y + s * 0.1f, x + s * 0.3f, y - s * 0.3f, st)
                st.strokeWidth = lw
                c.drawLine(x - s * 0.02f, y - s * 0.08f, x - s * 0.2f, y + s * 0.44f, st)
                c.drawLine(x - s * 0.02f, y - s * 0.08f, x + s * 0.16f, y + s * 0.44f, st)
            }
            STAR, FIREFLY -> {
                p.reset()
                for (k in 0 until 10) {
                    val a = -PI / 2 + k * PI / 5
                    val rr = if (k % 2 == 0) s * 0.42f else s * 0.18f
                    val px = x + (cos(a) * rr).toFloat(); val py = y + (sin(a) * rr).toFloat()
                    if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                Draw.fill.color = if (id == FIREFLY) Pal.AMBER else Pal.AMBER_HI
                c.drawPath(p, Draw.fill)
                if (id == STAR) c.drawPath(p, st)
            }
            CROW -> {
                Draw.fill.color = ink
                r.set(x - s * 0.3f, y - s * 0.16f, x + s * 0.2f, y + s * 0.2f)
                c.drawOval(r, Draw.fill)
                c.drawCircle(x + s * 0.22f, y - s * 0.16f, s * 0.14f, Draw.fill)
                p.reset(); p.moveTo(x + s * 0.32f, y - s * 0.2f); p.lineTo(x + s * 0.48f, y - s * 0.12f); p.lineTo(x + s * 0.32f, y - s * 0.1f); p.close()
                c.drawPath(p, Draw.fill)
                p.reset(); p.moveTo(x - s * 0.26f, y); p.lineTo(x - s * 0.48f, y - s * 0.1f); p.lineTo(x - s * 0.44f, y + s * 0.12f); p.close()
                c.drawPath(p, Draw.fill)
            }
            CAT -> {
                Draw.fill.color = ink
                r.set(x - s * 0.24f, y - s * 0.05f, x + s * 0.24f, y + s * 0.42f)
                c.drawOval(r, Draw.fill)
                c.drawCircle(x, y - s * 0.16f, s * 0.18f, Draw.fill)
                p.reset(); p.moveTo(x - s * 0.17f, y - s * 0.24f); p.lineTo(x - s * 0.14f, y - s * 0.44f); p.lineTo(x - s * 0.02f, y - s * 0.3f); p.close()
                p.moveTo(x + s * 0.17f, y - s * 0.24f); p.lineTo(x + s * 0.14f, y - s * 0.44f); p.lineTo(x + s * 0.02f, y - s * 0.3f); p.close()
                c.drawPath(p, Draw.fill)
                Draw.fill.color = Pal.AMBER
                c.drawCircle(x - s * 0.07f, y - s * 0.17f, s * 0.035f, Draw.fill)
                c.drawCircle(x + s * 0.07f, y - s * 0.17f, s * 0.035f, Draw.fill)
            }
            ARROW_R -> {
                st.strokeWidth = lw * 1.5f
                c.drawLine(x - s * 0.36f, y, x + s * 0.3f, y, st)
                c.drawLine(x + s * 0.3f, y, x + s * 0.08f, y - s * 0.2f, st)
                c.drawLine(x + s * 0.3f, y, x + s * 0.08f, y + s * 0.2f, st)
            }
            MIRROR -> {
                st.strokeWidth = lw * 1.8f
                c.drawLine(x - s * 0.3f, y + s * 0.3f, x + s * 0.3f, y - s * 0.3f, st)
                Draw.stroke.color = Pal.AMBER; Draw.stroke.strokeWidth = lw
                c.drawLine(x - s * 0.44f, y - s * 0.02f, x, y - s * 0.02f, Draw.stroke)
                c.drawLine(x, y - s * 0.02f, x, y - s * 0.44f, Draw.stroke)
            }
            HILL -> {
                p.reset(); p.moveTo(x - s * 0.46f, y + s * 0.34f); p.quadTo(x, y - s * 0.5f, x + s * 0.46f, y + s * 0.34f); p.close()
                c.drawPath(p, f); c.drawPath(p, st)
                Draw.flame(c, x, y - s * 0.2f, s * 0.1f, time)
            }
            UI_PAUSE -> {
                Draw.fill.color = ink
                r.set(x - s * 0.26f, y - s * 0.3f, x - s * 0.08f, y + s * 0.3f); c.drawRoundRect(r, s * 0.04f, s * 0.04f, Draw.fill)
                r.set(x + s * 0.08f, y - s * 0.3f, x + s * 0.26f, y + s * 0.3f); c.drawRoundRect(r, s * 0.04f, s * 0.04f, Draw.fill)
            }
            UI_PLAY -> {
                p.reset(); p.moveTo(x - s * 0.2f, y - s * 0.32f); p.lineTo(x + s * 0.32f, y); p.lineTo(x - s * 0.2f, y + s * 0.32f); p.close()
                Draw.fill.color = ink; c.drawPath(p, Draw.fill)
            }
            UI_SOUND -> {
                p.reset()
                p.moveTo(x - s * 0.36f, y - s * 0.12f); p.lineTo(x - s * 0.18f, y - s * 0.12f); p.lineTo(x + s * 0.04f, y - s * 0.32f)
                p.lineTo(x + s * 0.04f, y + s * 0.32f); p.lineTo(x - s * 0.18f, y + s * 0.12f); p.lineTo(x - s * 0.36f, y + s * 0.12f); p.close()
                Draw.fill.color = ink; c.drawPath(p, Draw.fill)
                if (!off) {
                    r.set(x - s * 0.1f, y - s * 0.22f, x + s * 0.3f, y + s * 0.22f); c.drawArc(r, -50f, 100f, false, st)
                    r.set(x - s * 0.1f, y - s * 0.38f, x + s * 0.44f, y + s * 0.38f); c.drawArc(r, -50f, 100f, false, st)
                }
            }
            UI_MUSIC -> {
                Draw.fill.color = ink
                c.drawCircle(x - s * 0.16f, y + s * 0.22f, s * 0.13f, Draw.fill)
                c.drawCircle(x + s * 0.24f, y + s * 0.14f, s * 0.13f, Draw.fill)
                c.drawLine(x - s * 0.04f, y + s * 0.22f, x - s * 0.04f, y - s * 0.32f, st)
                c.drawLine(x + s * 0.36f, y + s * 0.14f, x + s * 0.36f, y - s * 0.4f, st)
                st.strokeWidth = lw * 2f
                c.drawLine(x - s * 0.04f, y - s * 0.3f, x + s * 0.36f, y - s * 0.38f, st)
            }
            UI_HOME -> {
                p.reset(); p.moveTo(x - s * 0.36f, y - s * 0.02f); p.lineTo(x, y - s * 0.36f); p.lineTo(x + s * 0.36f, y - s * 0.02f)
                c.drawPath(p, st)
                r.set(x - s * 0.24f, y - s * 0.08f, x + s * 0.24f, y + s * 0.32f); c.drawRect(r, st)
            }
            UI_CLOSE -> {
                st.strokeWidth = lw * 1.4f
                c.drawLine(x - s * 0.24f, y - s * 0.24f, x + s * 0.24f, y + s * 0.24f, st)
                c.drawLine(x + s * 0.24f, y - s * 0.24f, x - s * 0.24f, y + s * 0.24f, st)
            }
            UI_RESTART -> {
                r.set(x - s * 0.3f, y - s * 0.3f, x + s * 0.3f, y + s * 0.3f)
                c.drawArc(r, 200f, 280f, false, st)
                val a = Math.toRadians(200.0)
                val ax = x + (cos(a) * s * 0.3f).toFloat(); val ay = y + (sin(a) * s * 0.3f).toFloat()
                p.reset(); p.moveTo(ax - s * 0.16f, ay - s * 0.02f); p.lineTo(ax + s * 0.16f, ay - s * 0.02f); p.lineTo(ax, ay + s * 0.2f); p.close()
                Draw.fill.color = ink; c.drawPath(p, Draw.fill)
            }
        }
        if (off && id != UI_SOUND && id != LAMP) {
            st.color = ink; st.strokeWidth = lw * 1.2f
            c.drawLine(x - s * 0.4f, y - s * 0.4f, x + s * 0.4f, y + s * 0.4f, st)
        } else if (off && id == UI_SOUND) {
            st.color = ink; st.strokeWidth = lw * 1.2f
            c.drawLine(x + s * 0.12f, y - s * 0.16f, x + s * 0.4f, y + s * 0.16f, st)
            c.drawLine(x + s * 0.4f, y - s * 0.16f, x + s * 0.12f, y + s * 0.16f, st)
        }
    }

    /** A toothed gear. */
    fun gear(c: Canvas, x: Float, y: Float, rIn: Float, rOut: Float, teeth: Int, angle: Float, fillCol: Int, ink: Int, lw: Float) {
        p.reset()
        val n = teeth * 4
        for (k in 0 until n) {
            val a = angle + k * 2 * PI.toFloat() / n
            val rr = if ((k / 2) % 2 == 0) rOut else rIn
            val px = x + cos(a) * rr; val py = y + sin(a) * rr
            if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        Draw.fill.color = fillCol
        c.drawPath(p, Draw.fill)
        Draw.stroke.color = ink; Draw.stroke.strokeWidth = lw
        c.drawPath(p, Draw.stroke)
        c.drawCircle(x, y, rIn * 0.35f, Draw.stroke)
        Draw.stroke.strokeWidth = lw * 0.7f
        for (k in 0 until 3) {
            val a = angle + k * 2.094f
            c.drawLine(x + cos(a) * rIn * 0.35f, y + sin(a) * rIn * 0.35f, x + cos(a) * rIn * 0.85f, y + sin(a) * rIn * 0.85f, Draw.stroke)
        }
    }
}

/** Thought bubble made of cloud puffs with trailing dots towards the speaker. */
object Bubble {
    private val p = Path()
    fun draw(c: Canvas, x: Float, y: Float, icons: IntArray, s: Float, appear: Float, tailX: Float, tailY: Float, time: Float) {
        if (appear <= 0.01f || icons.isEmpty()) return
        val sc = smooth(appear)
        val n = icons.size
        val w = s * (0.8f + n * 0.95f)
        val h = s * 1.35f
        c.save()
        c.scale(sc, sc, tailX, tailY)
        // trail dots
        Draw.fill.color = Pal.PAPER_HI
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = s * 0.05f
        for (k in 0 until 3) {
            val t = (k + 1) / 4f
            val dx = lerp(tailX, x, t); val dy = lerp(tailY, y + h * 0.4f, t)
            val rr = s * (0.07f + k * 0.05f)
            c.drawCircle(dx, dy, rr, Draw.fill); c.drawCircle(dx, dy, rr, Draw.stroke)
        }
        // cloud
        p.reset()
        val puffs = 4 + n * 2
        for (k in 0 until puffs) {
            val a = k * 2 * PI / puffs
            val px = x + (cos(a) * w * 0.5f).toFloat()
            val py = y + (sin(a) * h * 0.5f).toFloat()
            val pr = s * (0.42f + 0.06f * sin(k * 2.3f + time * 2f))
            p.addCircle(px, py, pr, Path.Direction.CW)
        }
        Draw.r1.set(x - w * 0.5f, y - h * 0.5f, x + w * 0.5f, y + h * 0.5f)
        p.addOval(Draw.r1, Path.Direction.CW)
        // stroke first (thick), then fill: gives a clean cloud outline around the union
        Draw.stroke.strokeWidth = s * 0.1f
        c.drawPath(p, Draw.stroke)
        c.drawPath(p, Draw.fill)
        for (k in 0 until n) {
            val ix = x - (n - 1) * s * 0.5f + k * s
            Icon.draw(c, icons[k], ix, y, s * 0.85f, Pal.INK, Pal.BRASS, time)
        }
        c.restore()
    }
}
