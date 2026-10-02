package com.pranvir.lamplighter

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A close-up machine puzzle drawn on a card. Panel units are 1000 x 620. */
abstract class Mini(val g: Game) {
    var solved = false
        protected set
    var closeRequested = false
    protected var doneT = 0f
    protected val panel = RectF()
    protected var ps = 1f
    protected var u = 1f
    protected var sw = 1f
    protected var sh = 1f
    protected var appear = 0f
    private var closeDown = false
    open val solvedHold = 1.5f

    fun layout(w: Float, h: Float, uu: Float) {
        sw = w; sh = h; u = uu
        val pw = min(w * 0.88f, h * 0.9f * 1000f / 620f)
        ps = pw / 1000f
        val ph = 620f * ps
        panel.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
    }

    fun px(x: Float) = panel.left + x * ps
    fun py(y: Float) = panel.top + y * ps
    fun lx(x: Float) = (x - panel.left) / ps
    fun ly(y: Float) = (y - panel.top) / ps

    private fun closeX() = px(1000f) - u * 1f
    private fun closeY() = py(0f) + u * 1f
    private fun closeR() = u * 5.5f

    open fun update(dt: Float, time: Float) {
        appear = min(1f, appear + dt * 4f)
        if (solved) {
            doneT += dt
            if (doneT > solvedHold) closeRequested = true
        }
    }

    fun draw(c: Canvas, time: Float) {
        Draw.fill.color = withAlpha(0xFF120E0B.toInt(), (190 * appear).toInt())
        c.drawRect(0f, 0f, sw, sh, Draw.fill)
        c.save()
        val s = 0.9f + 0.1f * smooth(appear)
        c.scale(s, s, sw / 2f, sh / 2f)
        // card
        Draw.fill.color = withAlpha(Pal.INK, 200)
        c.drawRoundRect(panel.left + u * 1.2f, panel.top + u * 1.6f, panel.right + u * 1.2f, panel.bottom + u * 1.6f, u * 3f, u * 3f, Draw.fill)
        Draw.fill.color = Pal.PAPER
        c.drawRoundRect(panel.left, panel.top, panel.right, panel.bottom, u * 3f, u * 3f, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = u * 0.6f
        c.drawRoundRect(panel.left, panel.top, panel.right, panel.bottom, u * 3f, u * 3f, Draw.stroke)
        Draw.stroke.strokeWidth = u * 0.25f
        c.drawRoundRect(panel.left + u * 1.5f, panel.top + u * 1.5f, panel.right - u * 1.5f, panel.bottom - u * 1.5f, u * 2f, u * 2f, Draw.stroke)
        c.save()
        c.clipRect(panel)
        drawContent(c, time)
        c.restore()
        // close button
        if (!solved) {
            Draw.fill.color = Pal.PAPER_HI
            c.drawCircle(closeX(), closeY(), closeR(), Draw.fill)
            Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = u * 0.5f
            c.drawCircle(closeX(), closeY(), closeR(), Draw.stroke)
            Icon.draw(c, Icon.UI_CLOSE, closeX(), closeY(), closeR() * 1.3f, Pal.INK)
        }
        c.restore()
    }

    abstract fun drawContent(c: Canvas, time: Float)

    fun down(x: Float, y: Float) {
        closeDown = !solved && hypot(x - closeX(), y - closeY()) < closeR() + u * 2f
        if (!closeDown && !solved) onDown(lx(x), ly(y))
    }
    fun move(x: Float, y: Float) { if (!closeDown && !solved) onMove(lx(x), ly(y)) }
    fun up(x: Float, y: Float) {
        if (closeDown) {
            closeDown = false
            if (hypot(x - closeX(), y - closeY()) < closeR() + u * 3f) closeRequested = true
            return
        }
        if (!solved) onUp(lx(x), ly(y))
    }
    open fun cancel() { closeDown = false; onCancel() }

    open fun onDown(x: Float, y: Float) {}
    open fun onMove(x: Float, y: Float) {}
    open fun onUp(x: Float, y: Float) {}
    open fun onCancel() {}
    abstract fun onSolved()

    /** Test hook. */
    fun debugSolve() = markSolved()

    protected fun markSolved() {
        if (solved) return
        solved = true
        doneT = 0f
        g.sfx(Sfx.SOLVE)
        g.host.haptic(true)
    }

    /** Brass plate background with rivets. */
    protected fun plate(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        Draw.fill.color = Pal.BRASS
        c.drawRoundRect(px(l), py(t), px(r), py(b), 18f * ps, 18f * ps, Draw.fill)
        Draw.fill.color = withAlpha(Pal.BRASS_DK, 90)
        c.drawRoundRect(px(l), py((t + b) / 2f), px(r), py(b), 18f * ps, 18f * ps, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 4f * ps
        c.drawRoundRect(px(l), py(t), px(r), py(b), 18f * ps, 18f * ps, Draw.stroke)
        Draw.fill.color = Pal.BRASS_DK
        for (x in floatArrayOf(l + 20f, r - 20f)) for (y in floatArrayOf(t + 20f, b - 20f)) {
            c.drawCircle(px(x), py(y), 7f * ps, Draw.fill)
            c.drawCircle(px(x), py(y), 7f * ps, Draw.stroke)
        }
    }
}

// ====================================================================== 1. pressure valve

class PressureMini(g: Game, private val done: () -> Unit) : Mini(g) {
    private var v = 0f
    private var p = 0f
    private var wheelA = 0f
    private var progress = 0f
    private var dragging = false
    private var lastA = 0f
    private var clickAcc = 0f
    private var touched = false
    private var burstT = 0f
    private var lastLit = 0
    private val gx = 310f; private val gy = 300f; private val gr = 190f
    private val wx = 745f; private val wy = 300f; private val wr = 150f
    val need = 2.4f
    private val lo = 58f; private val hi = 74f

    override fun update(dt: Float, time: Float) {
        super.update(dt, time)
        if (solved) return
        val surge = (10f * sin(time * 1.3f) + 6f * sin(time * 2.9f + 1f)) * v
        val target = v * 105f + surge
        p += (target - p) * min(1f, dt * 1.8f)
        p = p.coerceIn(0f, 110f)
        if (p > 96f) {
            // too much! steam blows out and the valve kicks back
            v = 0.25f; p = 62f; burstT = 1f; progress = 0f
            g.sfx(Sfx.HISS, 0.9f)
            g.host.haptic(true)
        }
        burstT = max(0f, burstT - dt * 1.2f)
        if (p in lo..hi) progress += dt else progress = max(0f, progress - dt * 0.7f)
        val lit = (progress / need * 5f).toInt().coerceIn(0, 5)
        if (lit > lastLit) g.sfx(Sfx.TWINKLE, 0.5f, 0.8f + lit * 0.1f)
        lastLit = lit
        if (progress >= need) markSolved()
    }

    override fun onDown(x: Float, y: Float) {
        if (hypot(x - wx, y - wy) < wr * 1.35f) {
            dragging = true
            touched = true
            lastA = atan2(y - wy, x - wx)
        }
    }

    override fun onMove(x: Float, y: Float) {
        if (!dragging) return
        val a = atan2(y - wy, x - wx)
        var d = a - lastA
        if (d > PI) d -= (2 * PI).toFloat()
        if (d < -PI) d += (2 * PI).toFloat()
        lastA = a
        val nv = (v + d / (2f * PI.toFloat() * 1.2f)).coerceIn(0f, 1f)
        val applied = (nv - v) * (2f * PI.toFloat() * 1.2f)
        v = nv
        wheelA += applied
        clickAcc += abs(applied)
        if (clickAcc > 0.35f) { clickAcc = 0f; g.sfx(Sfx.RATCHET, 0.4f, 0.9f + v * 0.3f) }
    }

    override fun onUp(x: Float, y: Float) { dragging = false }
    override fun onCancel() { dragging = false }
    override fun onSolved() = done()

    val pressure get() = p
    fun wheel(out: FloatArray) { out[0] = px(wx); out[1] = py(wy); }
    fun wheelRadius() = wr * ps

    override fun drawContent(c: Canvas, time: Float) {
        plate(c, 40f, 40f, 960f, 580f)
        // pipe between gauge and wheel
        Draw.fill.color = Pal.T3
        c.drawRect(px(gx), py(gy - 28f), px(wx), py(gy + 28f), Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 4f * ps
        c.drawRect(px(gx), py(gy - 28f), px(wx), py(gy + 28f), Draw.stroke)
        // gauge
        Draw.fill.color = Pal.PAPER_HI
        c.drawCircle(px(gx), py(gy), gr * ps, Draw.fill)
        Draw.stroke.strokeWidth = 8f * ps
        c.drawCircle(px(gx), py(gy), gr * ps, Draw.stroke)
        Draw.r1.set(px(gx - gr * 0.8f), py(gy - gr * 0.8f), px(gx + gr * 0.8f), py(gy + gr * 0.8f))
        // danger arc and sweet band
        Draw.stroke.strokeWidth = 22f * ps
        Draw.stroke.color = Pal.T3
        c.drawArc(Draw.r1, 135f + 0.9f * 270f, 0.2f * 270f, false, Draw.stroke)
        Draw.stroke.color = if (p in lo..hi) Pal.AMBER_HI else Pal.AMBER
        c.drawArc(Draw.r1, 135f + lo / 100f * 270f, (hi - lo) / 100f * 270f, false, Draw.stroke)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 3f * ps
        for (k in 0..10) {
            val a = Math.toRadians((135.0 + k * 27.0)).toFloat()
            val r0 = gr * (if (k % 5 == 0) 0.62f else 0.7f)
            c.drawLine(px(gx + cos(a) * r0), py(gy + sin(a) * r0), px(gx + cos(a) * gr * 0.9f), py(gy + sin(a) * gr * 0.9f), Draw.stroke)
        }
        // needle (with a nervous jitter)
        val jit = sin(time * 40f) * 0.6f * v
        val na = Math.toRadians((135.0 + (p + jit) / 100.0 * 270.0)).toFloat()
        Draw.stroke.strokeWidth = 7f * ps; Draw.stroke.color = Pal.INK
        c.drawLine(px(gx), py(gy), px(gx + cos(na) * gr * 0.82f), py(gy + sin(na) * gr * 0.82f), Draw.stroke)
        Draw.fill.color = Pal.BRASS_DK
        c.drawCircle(px(gx), py(gy), 20f * ps, Draw.fill)
        c.drawCircle(px(gx), py(gy), 20f * ps, Draw.stroke.also { it.strokeWidth = 4f * ps })
        // valve wheel
        c.save()
        c.rotate(Math.toDegrees(wheelA.toDouble()).toFloat(), px(wx), py(wy))
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 26f * ps
        c.drawCircle(px(wx), py(wy), wr * ps, Draw.stroke)
        Draw.stroke.color = Pal.T3; Draw.stroke.strokeWidth = 16f * ps
        c.drawCircle(px(wx), py(wy), wr * ps, Draw.stroke)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 14f * ps
        for (k in 0 until 5) {
            val a = k * 2f * PI.toFloat() / 5f
            c.drawLine(px(wx), py(wy), px(wx + cos(a) * wr), py(wy + sin(a) * wr), Draw.stroke)
        }
        for (k in 0 until 10) {
            val a = k * 2f * PI.toFloat() / 10f
            Draw.fill.color = Pal.INK
            c.drawCircle(px(wx + cos(a) * wr), py(wy + sin(a) * wr), 16f * ps, Draw.fill)
        }
        Draw.fill.color = Pal.BRASS_DK
        c.drawCircle(px(wx), py(wy), 34f * ps, Draw.fill)
        Draw.stroke.strokeWidth = 5f * ps
        c.drawCircle(px(wx), py(wy), 34f * ps, Draw.stroke)
        c.restore()
        // turning hint
        if (!touched) {
            val a0 = time * 2f
            Draw.stroke.color = withAlpha(Pal.AMBER_DK, (160 + 80 * sin(time * 4f)).toInt())
            Draw.stroke.strokeWidth = 8f * ps
            Draw.r2.set(px(wx - wr * 1.25f), py(wy - wr * 1.25f), px(wx + wr * 1.25f), py(wy + wr * 1.25f))
            c.drawArc(Draw.r2, Math.toDegrees(a0.toDouble()).toFloat(), 80f, false, Draw.stroke)
            val ea = a0 + 80f * PI.toFloat() / 180f
            Draw.fill.color = Draw.stroke.color
            c.drawCircle(px(wx + cos(ea) * wr * 1.25f), py(wy + sin(ea) * wr * 1.25f), 12f * ps, Draw.fill)
        }
        // steam burst
        if (burstT > 0f) {
            for (k in 0 until 10) {
                val a = -PI.toFloat() / 2f + (k - 5) * 0.2f
                val d = (1f - burstT) * 200f + k * 6f
                Draw.fill.color = withAlpha(Pal.PAPER_HI, (200 * burstT).toInt())
                c.drawCircle(px(gx + 100f + cos(a) * d), py(gy - 60f + sin(a) * d), (18f + (1f - burstT) * 30f) * ps, Draw.fill)
            }
        }
        // progress: five little flames
        val lit = (progress / need * 5f).coerceIn(0f, 5f)
        for (k in 0 until 5) {
            val x = 380f + k * 60f
            val y = 540f
            val on = clamp01(lit - k)
            Draw.fill.color = Pal.T3
            c.drawCircle(px(x), py(y + 4f), 10f * ps, Draw.fill)
            if (on > 0f) {
                Draw.glow(c, px(x), py(y - 10f), 40f * ps * on, on)
                Draw.flame(c, px(x), py(y), 14f * ps * on, time, k.toFloat())
            }
        }
        if (solved) Draw.glow(c, px(500f), py(300f), 600f * ps, 0.8f * clamp01(doneT * 2f))
    }
}

// ====================================================================== 2. gear train

class GearsMini(g: Game, private val hasLarge: Boolean, private val done: () -> Unit) : Mini(g) {
    private val cx0 = 170f; private val cy0 = 330f; private val cr = 100f
    private val dx0 = 858.9f; private val dy0 = 307.7f; private val dr = 110f
    private val pegX = floatArrayOf(348.5f, 538.8f, 694.7f)
    private val pegY = floatArrayOf(265f, 353.7f, 263.7f)
    private val radii = floatArrayOf(60f, 90f, 120f) // S, M, L
    private val trayX = floatArrayOf(330f, 500f, 680f)
    private val trayY = 548f
    private val trayScale = 0.42f
    val onPeg = intArrayOf(-1, -1, -1)       // gear index on each peg
    private val gearPeg = intArrayOf(-1, -1, -1) // peg for each gear
    private var drag = -1
    private var dragX = 0f; private var dragY = 0f
    private var cranking = false
    private var crankA = 0f
    private var lastA = 0f
    private var total = 0f
    private var jam = 0f
    private var jamCd = 0f

    fun chainOk() = onPeg[0] == 1 && onPeg[1] == 2 && onPeg[2] == 0

    override fun update(dt: Float, time: Float) {
        super.update(dt, time)
        jam = max(0f, jam - dt * 3f)
        jamCd -= dt
    }

    private fun gearAt(x: Float, y: Float): Int {
        for (gi in 0..2) {
            if (gi == 2 && !hasLarge) continue
            val p = gearPeg[gi]
            val gx = if (p >= 0) pegX[p] else trayX[gi]
            val gy = if (p >= 0) pegY[p] else trayY
            val r = radii[gi] * (if (p >= 0) 1f else trayScale)
            if (hypot(x - gx, y - gy) < max(r, 45f)) return gi
        }
        return -1
    }

    override fun onDown(x: Float, y: Float) {
        val hx = cx0 + cos(crankA) * 70f; val hy = cy0 + sin(crankA) * 70f
        if (hypot(x - hx, y - hy) < 60f || hypot(x - cx0, y - cy0) < cr * 0.9f) {
            cranking = true
            lastA = atan2(y - cy0, x - cx0)
            return
        }
        val gi = gearAt(x, y)
        if (gi >= 0) {
            drag = gi
            val p = gearPeg[gi]
            if (p >= 0) { onPeg[p] = -1; gearPeg[gi] = -1 }
            dragX = x; dragY = y
            g.sfx(Sfx.CLICK, 0.5f)
        }
    }

    override fun onMove(x: Float, y: Float) {
        if (drag >= 0) { dragX = x; dragY = y; return }
        if (!cranking) return
        val a = atan2(y - cy0, x - cx0)
        var d = a - lastA
        if (d > PI) d -= (2 * PI).toFloat()
        if (d < -PI) d += (2 * PI).toFloat()
        lastA = a
        if (chainOk()) {
            crankA += d
            total = max(0f, total + d)
            if (abs(d) > 0.02f && ((crankA * 3f).toInt() != ((crankA - d) * 3f).toInt())) g.sfx(Sfx.RATCHET, 0.45f, 0.8f)
            if (total >= 4f * PI.toFloat()) markSolved()
        } else if (abs(d) > 0.01f) {
            jam = 1f
            if (jamCd <= 0f) { jamCd = 0.45f; g.sfx(Sfx.CLUNK, 0.7f); g.host.haptic(false) }
        }
    }

    override fun onUp(x: Float, y: Float) {
        cranking = false
        val gi = drag
        drag = -1
        if (gi < 0) return
        var best = -1; var bd = 80f
        for (p in 0..2) {
            if (onPeg[p] >= 0) continue
            val d = hypot(x - pegX[p], y - pegY[p])
            if (d < bd) { bd = d; best = p }
        }
        if (best >= 0) {
            onPeg[best] = gi; gearPeg[gi] = best
            g.sfx(Sfx.CLINK, 0.8f)
        }
    }

    override fun onCancel() { drag = -1; cranking = false }
    override fun onSolved() = done()

    /** Angle of each gear given the crank angle (only meaningful when the chain is complete). */
    private fun gearAngle(gi: Int, p: Int): Float {
        if (!chainOk()) return 0f
        // alternate direction along the chain, speed ratio by radius
        val sign = if (p % 2 == 0) -1f else 1f
        return sign * crankA * cr / radii[gi]
    }

    override fun drawContent(c: Canvas, time: Float) {
        plate(c, 30f, 30f, 970f, 480f)
        val lw = 3.5f * ps
        val shake = if (jam > 0f) sin(time * 60f) * 4f * jam else 0f
        // pegs
        for (p in 0..2) {
            Draw.fill.color = Pal.BRASS_DK
            c.drawCircle(px(pegX[p]), py(pegY[p]), 14f * ps, Draw.fill)
            Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = lw
            c.drawCircle(px(pegX[p]), py(pegY[p]), 14f * ps, Draw.stroke)
            if (onPeg[p] < 0 && drag >= 0) {
                Draw.stroke.color = withAlpha(Pal.AMBER_DK, 180)
                c.drawCircle(px(pegX[p]), py(pegY[p]), 40f * ps + sin(time * 6f) * 4f * ps, Draw.stroke)
            }
        }
        // drum (bridge) gear
        val drumA = if (chainOk()) crankA * cr / dr * 1f else 0f
        Icon.gear(c, px(dx0), py(dy0), (dr - 14f) * ps, dr * ps, 11, drumA, Pal.T2, Pal.INK, lw)
        Draw.fill.color = Pal.T4
        c.drawCircle(px(dx0), py(dy0), 30f * ps, Draw.fill)
        // placed gears
        for (p in 0..2) {
            val gi = onPeg[p]
            if (gi < 0) continue
            val r = radii[gi]
            Icon.gear(c, px(pegX[p] + shake), py(pegY[p]), (r - 14f) * ps, r * ps, (r / 10f).toInt(), gearAngle(gi, p), Pal.BRASS, Pal.INK, lw)
        }
        // crank gear + handle
        Icon.gear(c, px(cx0 + shake), py(cy0), (cr - 14f) * ps, cr * ps, 10, crankA, Pal.T1, Pal.INK, lw)
        val hx = cx0 + cos(crankA) * 70f; val hy = cy0 + sin(crankA) * 70f
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 12f * ps
        c.drawLine(px(cx0), py(cy0), px(hx), py(hy), Draw.stroke)
        Draw.fill.color = Pal.T4
        c.drawCircle(px(hx), py(hy), 24f * ps, Draw.fill)
        Draw.stroke.strokeWidth = lw
        c.drawCircle(px(hx), py(hy), 24f * ps, Draw.stroke)
        if (chainOk() && total < 0.3f) {
            Draw.stroke.color = withAlpha(Pal.AMBER_DK, (150 + 90 * sin(time * 4f)).toInt())
            Draw.stroke.strokeWidth = 7f * ps
            Draw.r2.set(px(cx0 - 125f), py(cy0 - 125f), px(cx0 + 125f), py(cy0 + 125f))
            c.drawArc(Draw.r2, Math.toDegrees(time * 2.0).toFloat(), 90f, false, Draw.stroke)
        }
        // bridge progress picture (top right)
        val prog = clamp01(total / (4f * PI.toFloat()))
        val bx = 560f; val by = 130f
        Draw.fill.color = withAlpha(Pal.PAPER_HI, 150)
        c.drawRoundRect(px(bx - 30f), py(by - 90f), px(bx + 120f), py(by + 40f), 12f * ps, 12f * ps, Draw.fill)
        Draw.fill.color = withAlpha(Pal.T3, 160)
        c.drawRect(px(bx), py(by + 12f), px(bx + 90f), py(by + 40f), Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 6f * ps
        val ang = -70f * (1f - prog)
        c.save()
        c.rotate(ang, px(bx), py(by))
        c.drawLine(px(bx), py(by), px(bx + 88f), py(by), Draw.stroke)
        c.restore()
        c.drawLine(px(bx - 12f), py(by + 4f), px(bx - 12f), py(by + 40f), Draw.stroke)
        c.drawLine(px(bx + 100f), py(by + 4f), px(bx + 100f), py(by + 40f), Draw.stroke)
        // tray
        Draw.fill.color = Pal.T2
        c.drawRoundRect(px(220f), py(495f), px(790f), py(605f), 16f * ps, 16f * ps, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = lw
        c.drawRoundRect(px(220f), py(495f), px(790f), py(605f), 16f * ps, 16f * ps, Draw.stroke)
        for (gi in 0..2) {
            if (gearPeg[gi] >= 0 || drag == gi) continue
            val r = radii[gi] * trayScale
            if (gi == 2 && !hasLarge) {
                // missing piece: dotted outline
                Draw.stroke.color = withAlpha(Pal.INK, 150); Draw.stroke.strokeWidth = 2.5f * ps
                for (k in 0 until 16) {
                    val a = k * 2f * PI.toFloat() / 16f
                    c.drawArc(px(trayX[gi] - r), py(trayY - r), px(trayX[gi] + r), py(trayY + r), Math.toDegrees(a.toDouble()).toFloat(), 11f, false, Draw.stroke)
                }
                Icon.draw(c, Icon.QUESTION, px(trayX[gi]), py(trayY), 50f * ps, withAlpha(Pal.INK, 150))
                continue
            }
            Icon.gear(c, px(trayX[gi]), py(trayY), (r - 6f) * ps, r * ps, (radii[gi] / 10f).toInt(), time * 0.2f, Pal.BRASS, Pal.INK, lw)
        }
        if (drag >= 0) {
            val r = radii[drag]
            Icon.gear(c, px(dragX), py(dragY), (r - 14f) * ps, r * ps, (r / 10f).toInt(), 0f, withAlpha(Pal.BRASS, 230), Pal.INK, lw)
        }
        if (solved) Draw.glow(c, px(500f), py(300f), 600f * ps, 0.7f * clamp01(doneT * 2f))
    }

    // test hooks
    fun debugPlace(gi: Int, peg: Int) { onPeg[peg] = gi; gearPeg[gi] = peg }
    fun debugCrank(rad: Float) { if (chainOk()) { crankA += rad; total += rad; if (total >= 4f * PI.toFloat()) markSolved() } }
    fun pegPos(p: Int, out: FloatArray) { out[0] = px(pegX[p]); out[1] = py(pegY[p]) }
    fun trayPos(gi: Int, out: FloatArray) { out[0] = px(trayX[gi]); out[1] = py(trayY) }
    fun crankCenter(out: FloatArray) { out[0] = px(cx0); out[1] = py(cy0) }
    fun crankRadius() = 70f * ps
}

// ====================================================================== 3. mirrors & beam

class MirrorMini(g: Game, private val done: () -> Unit) : Mini(g) {
    companion object {
        const val COLS = 6
        const val ROWS = 5
        // found by exhaustive search: exactly one beam path reaches the lens, using 7 mirrors
        val MIRRORS = intArrayOf(1, 3, 5, 2, 5, 3, 2, 2, 2, 3, 4, 2, 0, 0, 0, 4, 1, 0)
        val BLOCKS = intArrayOf(1, 4, 3, 3, 5, 1)
        const val SRC_ROW = 4
        const val TARGET_COL = 4
    }

    val state = IntArray(MIRRORS.size / 2) { 1 } // 0 = '/', 1 = '\'
    private val shown = FloatArray(state.size) { if (state[it] == 0) 0f else 90f }
    private val cell = 88f
    private val gx0 = 236f
    private val gy0 = 92f
    private val beamX = FloatArray(64)
    private val beamY = FloatArray(64)
    private var beamN = 0
    var hit = false
        private set
    private var hitT = 0f

    init {
        // make sure the starting position isn't already solved
        state[0] = 1; state[1] = 0; state[6] = 1
        for (i in state.indices) shown[i] = if (state[i] == 0) 0f else 90f
        trace()
        if (hit) { state[0] = 1 - state[0]; trace() }
    }

    private fun mirrorAt(cx: Int, cy: Int): Int {
        for (i in state.indices) if (MIRRORS[i * 2] == cx && MIRRORS[i * 2 + 1] == cy) return i
        return -1
    }

    private fun blocked(cx: Int, cy: Int): Boolean {
        var k = 0
        while (k < BLOCKS.size) { if (BLOCKS[k] == cx && BLOCKS[k + 1] == cy) return true; k += 2 }
        return false
    }

    fun trace() {
        var x = -1; var y = SRC_ROW; var dx = 1; var dy = 0
        beamN = 0
        beamX[beamN] = gx0 - cell * 0.9f; beamY[beamN] = gy0 + (y + 0.5f) * cell; beamN++
        hit = false
        for (step in 0 until 60) {
            x += dx; y += dy
            if (x !in 0 until COLS || y !in 0 until ROWS) {
                val ex = gx0 + (x + 0.5f) * cell - dx * cell * 0.5f + dx * cell * 0.3f
                val ey = gy0 + (y + 0.5f) * cell - dy * cell * 0.5f + dy * cell * 0.3f
                beamX[beamN] = ex; beamY[beamN] = ey; beamN++
                hit = y < 0 && x == TARGET_COL
                return
            }
            if (blocked(x, y)) {
                beamX[beamN] = gx0 + (x + 0.5f) * cell - dx * cell * 0.3f
                beamY[beamN] = gy0 + (y + 0.5f) * cell - dy * cell * 0.3f
                beamN++
                return
            }
            val m = mirrorAt(x, y)
            if (m >= 0) {
                beamX[beamN] = gx0 + (x + 0.5f) * cell; beamY[beamN] = gy0 + (y + 0.5f) * cell; beamN++
                if (beamN >= beamX.size - 2) return
                if (state[m] == 0) { val t = dx; dx = -dy; dy = -t } // '/'
                else { val t = dx; dx = dy; dy = t }                // '\'
            }
        }
    }

    override fun update(dt: Float, time: Float) {
        super.update(dt, time)
        for (i in state.indices) {
            val target = if (state[i] == 0) 0f else 90f
            shown[i] += (target - shown[i]) * min(1f, dt * 14f)
        }
        if (hit && !solved) { hitT += dt; if (hitT > 0.5f) markSolved() } else if (!hit) hitT = 0f
    }

    override fun onUp(x: Float, y: Float) {
        val cx = kotlin.math.floor((x - gx0) / cell).toInt()
        val cy = kotlin.math.floor((y - gy0) / cell).toInt()
        if (cx !in 0 until COLS || cy !in 0 until ROWS) return
        val m = mirrorAt(cx, cy)
        if (m < 0) return
        state[m] = 1 - state[m]
        g.sfx(Sfx.CLICK, 0.7f, 1.1f)
        trace()
        if (hit) g.sfx(Sfx.TWINKLE, 0.7f)
    }

    fun tapCell(cx: Int, cy: Int, out: FloatArray) { out[0] = px(gx0 + (cx + 0.5f) * cell); out[1] = py(gy0 + (cy + 0.5f) * cell) }

    override fun onSolved() = done()

    override fun drawContent(c: Canvas, time: Float) {
        plate(c, 150f, 40f, 850f, 580f)
        val lw = 3f * ps
        // grid
        Draw.stroke.color = withAlpha(Pal.INK, 70); Draw.stroke.strokeWidth = 1.5f * ps
        for (i in 0..COLS) c.drawLine(px(gx0 + i * cell), py(gy0), px(gx0 + i * cell), py(gy0 + ROWS * cell), Draw.stroke)
        for (j in 0..ROWS) c.drawLine(px(gx0), py(gy0 + j * cell), px(gx0 + COLS * cell), py(gy0 + j * cell), Draw.stroke)
        // blocks (gear housings)
        var k = 0
        while (k < BLOCKS.size) {
            val bx = gx0 + (BLOCKS[k] + 0.5f) * cell; val by = gy0 + (BLOCKS[k + 1] + 0.5f) * cell
            Icon.gear(c, px(bx), py(by), 26f * ps, 36f * ps, 8, time * 0.3f + k, Pal.T3, Pal.INK, lw)
            k += 2
        }
        // source lantern (left)
        val sx = gx0 - cell * 0.9f; val sy = gy0 + (SRC_ROW + 0.5f) * cell
        Draw.glow(c, px(sx), py(sy), 90f * ps, 0.9f)
        Icon.draw(c, Icon.LAMP, px(sx), py(sy + 6f), 70f * ps, Pal.INK, Pal.BRASS, time)
        // target lens (top)
        val tx = gx0 + (TARGET_COL + 0.5f) * cell; val ty = gy0 - cell * 0.55f
        if (hit) Draw.glow(c, px(tx), py(ty), 200f * ps, 0.9f)
        c.save()
        c.rotate(90f, px(tx), py(ty))
        Icon.draw(c, Icon.LENS, px(tx), py(ty), 70f * ps, Pal.INK, Pal.BRASS, time)
        c.restore()
        // beam
        if (beamN >= 2) {
            val pulse = 0.75f + 0.25f * sin(time * 9f)
            Draw.stroke.color = withAlpha(Pal.AMBER, (120 * pulse).toInt()); Draw.stroke.strokeWidth = 22f * ps
            for (i in 0 until beamN - 1) c.drawLine(px(beamX[i]), py(beamY[i]), px(beamX[i + 1]), py(beamY[i + 1]), Draw.stroke)
            Draw.stroke.color = Pal.AMBER_HI; Draw.stroke.strokeWidth = 6f * ps
            for (i in 0 until beamN - 1) c.drawLine(px(beamX[i]), py(beamY[i]), px(beamX[i + 1]), py(beamY[i + 1]), Draw.stroke)
            Draw.glow(c, px(beamX[beamN - 1]), py(beamY[beamN - 1]), 50f * ps, 0.8f)
        }
        // mirrors
        for (i in state.indices) {
            val mx = gx0 + (MIRRORS[i * 2] + 0.5f) * cell; val my = gy0 + (MIRRORS[i * 2 + 1] + 0.5f) * cell
            Draw.fill.color = Pal.T2
            c.drawCircle(px(mx), py(my), 34f * ps, Draw.fill)
            Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = lw
            c.drawCircle(px(mx), py(my), 34f * ps, Draw.stroke)
            c.save()
            c.rotate(-45f + shown[i], px(mx), py(my))
            Draw.fill.color = Pal.PAPER_HI
            c.drawRect(px(mx - 34f), py(my - 6f), px(mx + 34f), py(my + 6f), Draw.fill)
            Draw.stroke.strokeWidth = lw
            c.drawRect(px(mx - 34f), py(my - 6f), px(mx + 34f), py(my + 6f), Draw.stroke)
            Draw.fill.color = Pal.INK
            c.drawRect(px(mx - 34f), py(my + 6f), px(mx + 34f), py(my + 10f), Draw.fill)
            c.restore()
        }
        if (solved) Draw.glow(c, px(500f), py(300f), 600f * ps, 0.8f * clamp01(doneT * 2f))
    }
}

// ====================================================================== 4. clock rings

object Glyph {
    const val BIRD = 0; const val MOON = 1; const val STAR = 2; const val FISH = 3
    const val KEY = 4; const val EYE = 5; const val LEAF = 6; const val SUN = 7
    private val p = Path()

    fun draw(c: Canvas, id: Int, x: Float, y: Float, s: Float, ink: Int, fillCol: Int, time: Float) {
        val st = Draw.stroke; val f = Draw.fill
        st.color = ink; st.strokeWidth = s * 0.08f
        f.color = fillCol
        when (id) {
            BIRD -> {
                p.reset()
                p.moveTo(x - s * 0.45f, y - s * 0.1f)
                p.quadTo(x - s * 0.2f, y - s * 0.35f, x, y + s * 0.05f)
                p.quadTo(x + s * 0.2f, y - s * 0.35f, x + s * 0.45f, y - s * 0.1f)
                p.quadTo(x + s * 0.15f, y - s * 0.12f, x, y + s * 0.2f)
                p.quadTo(x - s * 0.15f, y - s * 0.12f, x - s * 0.45f, y - s * 0.1f)
                p.close()
                c.drawPath(p, f); c.drawPath(p, st)
            }
            MOON -> {
                p.reset()
                p.moveTo(x + s * 0.05f, y - s * 0.4f)
                p.cubicTo(x - s * 0.5f, y - s * 0.35f, x - s * 0.5f, y + s * 0.35f, x + s * 0.05f, y + s * 0.4f)
                p.cubicTo(x - s * 0.22f, y + s * 0.22f, x - s * 0.22f, y - s * 0.22f, x + s * 0.05f, y - s * 0.4f)
                p.close()
                c.drawPath(p, f); c.drawPath(p, st)
            }
            STAR -> Icon.draw(c, Icon.STAR, x, y, s, ink, fillCol, time)
            FISH -> Icon.draw(c, Icon.FISH, x, y, s, ink, fillCol, time)
            KEY -> Icon.draw(c, Icon.KEY, x, y, s, ink, fillCol, time)
            EYE -> {
                p.reset()
                p.moveTo(x - s * 0.42f, y); p.quadTo(x, y - s * 0.34f, x + s * 0.42f, y); p.quadTo(x, y + s * 0.34f, x - s * 0.42f, y); p.close()
                c.drawPath(p, f); c.drawPath(p, st)
                f.color = ink; c.drawCircle(x, y, s * 0.11f, f)
            }
            LEAF -> {
                p.reset()
                p.moveTo(x - s * 0.36f, y + s * 0.3f); p.quadTo(x - s * 0.3f, y - s * 0.34f, x + s * 0.36f, y - s * 0.3f)
                p.quadTo(x + s * 0.3f, y + s * 0.34f, x - s * 0.36f, y + s * 0.3f); p.close()
                c.drawPath(p, f); c.drawPath(p, st)
                c.drawLine(x - s * 0.36f, y + s * 0.3f, x + s * 0.2f, y - s * 0.16f, st)
            }
            SUN -> {
                c.drawCircle(x, y, s * 0.2f, f); c.drawCircle(x, y, s * 0.2f, st)
                for (k in 0 until 8) {
                    val a = k * PI.toFloat() / 4f
                    c.drawLine(x + cos(a) * s * 0.28f, y + sin(a) * s * 0.28f, x + cos(a) * s * 0.42f, y + sin(a) * s * 0.42f, st)
                }
            }
        }
    }
}

class RingsMini(g: Game, private val done: () -> Unit) : Mini(g) {
    companion object {
        val RINGS = arrayOf(
            intArrayOf(Glyph.STAR, Glyph.EYE, Glyph.SUN, Glyph.FISH, Glyph.LEAF, Glyph.MOON, Glyph.KEY, Glyph.BIRD), // inner
            intArrayOf(Glyph.KEY, Glyph.MOON, Glyph.FISH, Glyph.SUN, Glyph.BIRD, Glyph.LEAF, Glyph.STAR, Glyph.EYE), // middle
            intArrayOf(Glyph.SUN, Glyph.LEAF, Glyph.BIRD, Glyph.KEY, Glyph.EYE, Glyph.STAR, Glyph.FISH, Glyph.MOON)  // outer
        )
        /** What the telescope shows: inner fish, middle moon, outer bird. */
        val TARGET = intArrayOf(Glyph.FISH, Glyph.MOON, Glyph.BIRD)
    }

    val rot = intArrayOf(0, 0, 0)
    private val shownA = floatArrayOf(0f, 0f, 0f)
    private val cx = 500f; private val cy = 318f
    private val bands = floatArrayOf(70f, 145f, 215f, 285f)

    fun topSymbol(ring: Int) = RINGS[ring][rot[ring]]
    fun aligned() = (0..2).all { topSymbol(it) == TARGET[it] }

    override fun update(dt: Float, time: Float) {
        super.update(dt, time)
        for (r in 0..2) {
            val target = -rot[r] * 45f
            // shortest animation towards the target angle
            var d = target - shownA[r]
            while (d > 180f) d -= 360f
            while (d < -180f) d += 360f
            shownA[r] += d * min(1f, dt * 12f)
        }
    }

    override fun onUp(x: Float, y: Float) {
        val d = hypot(x - cx, y - cy)
        for (r in 0..2) {
            if (d >= bands[r] && d < bands[r + 1]) {
                rot[r] = (rot[r] + 1) % 8
                g.sfx(Sfx.CLICK, 0.8f, 0.85f + r * 0.12f)
                if (topSymbol(r) == TARGET[r]) g.sfx(Sfx.TWINKLE, 0.5f, 0.9f + r * 0.15f)
                if (aligned()) markSolved()
                return
            }
        }
    }

    fun ringTapPoint(r: Int, out: FloatArray) {
        val rr = (bands[r] + bands[r + 1]) / 2f
        out[0] = px(cx + rr * 0.707f); out[1] = py(cy + rr * 0.707f)
    }

    override fun onSolved() = done()

    override fun drawContent(c: Canvas, time: Float) {
        plate(c, 150f, 20f, 850f, 600f)
        val lw = 3.5f * ps
        for (r in 2 downTo 0) {
            val outer = bands[r + 1]
            Draw.fill.color = when (r) { 2 -> Pal.BRASS_DK; 1 -> Pal.T2; else -> Pal.BRASS }
            c.drawCircle(px(cx), py(cy), outer * ps, Draw.fill)
            Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = lw
            c.drawCircle(px(cx), py(cy), outer * ps, Draw.stroke)
            val mid = (bands[r] + bands[r + 1]) / 2f
            val gs = (bands[r + 1] - bands[r]) * 0.9f
            for (k in 0 until 8) {
                val a = Math.toRadians((-90.0 + k * 45.0 + shownA[r])).toFloat()
                val x = cx + cos(a) * mid; val y = cy + sin(a) * mid
                val settle = ((shownA[r] + rot[r] * 45f) % 360f + 360f) % 360f
                val top = k == rot[r] && (settle < 3f || settle > 357f)
                val ok = top && RINGS[r][k] == TARGET[r]
                if (ok) Draw.glow(c, px(x), py(y), gs * 1.1f * ps, 0.8f)
                Glyph.draw(c, RINGS[r][k], px(x), py(y), gs * ps, Pal.INK, if (ok) Pal.AMBER_HI else Pal.PAPER_HI, time)
            }
            // tick marks between symbols
            Draw.stroke.strokeWidth = 2f * ps
            for (k in 0 until 8) {
                val a = Math.toRadians((-90.0 + 22.5 + k * 45.0 + shownA[r])).toFloat()
                c.drawLine(px(cx + cos(a) * bands[r]), py(cy + sin(a) * bands[r]), px(cx + cos(a) * outer), py(cy + sin(a) * outer), Draw.stroke)
            }
        }
        // hub
        Draw.fill.color = Pal.T4
        c.drawCircle(px(cx), py(cy), bands[0] * ps, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = lw
        c.drawCircle(px(cx), py(cy), bands[0] * ps, Draw.stroke)
        Draw.glow(c, px(cx), py(cy), 80f * ps, if (solved) 1f else 0.5f)
        Draw.flame(c, px(cx), py(cy + 18f), 22f * ps * (if (solved) 1.4f else 1f), time)
        // pointer at 12 o'clock
        val ptY = cy - bands[3] - 6f
        Draw.path.reset()
        Draw.path.moveTo(px(cx), py(ptY + 16f)); Draw.path.lineTo(px(cx - 18f), py(ptY - 16f)); Draw.path.lineTo(px(cx + 18f), py(ptY - 16f)); Draw.path.close()
        Draw.fill.color = Pal.AMBER_DK
        c.drawPath(Draw.path, Draw.fill)
        c.drawPath(Draw.path, Draw.stroke)
        if (solved) Draw.glow(c, px(500f), py(300f), 600f * ps, 0.8f * clamp01(doneT * 2f))
    }
}

// ====================================================================== telescope (clue)

class TelescopeMini(g: Game) : Mini(g) {
    override val solvedHold = 0f
    override fun onSolved() {}
    override fun onUp(x: Float, y: Float) { closeRequested = true }

    override fun drawContent(c: Canvas, time: Float) {
        Draw.fill.color = Pal.INK
        c.drawRect(panel, Draw.fill)
        val cx = 500f; val cy = 310f; val r = 285f
        Draw.fill.color = 0xFF191B24.toInt()
        c.drawCircle(px(cx), py(cy), r * ps, Draw.fill)
        // faint stars
        val rnd = java.util.Random(11)
        for (k in 0 until 90) {
            val a = rnd.nextFloat() * 6.283f; val d = kotlin.math.sqrt(rnd.nextFloat()) * r
            val tw = 0.5f + 0.5f * sin(time * (1f + rnd.nextFloat() * 2f) + k)
            Draw.fill.color = withAlpha(Pal.PAPER_HI, (40 + 110 * tw).toInt())
            c.drawCircle(px(cx + cos(a) * d), py(cy + sin(a) * d), (1f + rnd.nextFloat() * 1.8f) * ps, Draw.fill)
        }
        // three bands, same as the clock rings
        val bands = floatArrayOf(70f, 145f, 215f, 285f)
        Draw.stroke.color = withAlpha(Pal.PAPER_HI, 50); Draw.stroke.strokeWidth = 2f * ps
        for (b in bands) c.drawCircle(px(cx), py(cy), b * ps, Draw.stroke)
        // constellations drawn like an old star chart, each at the top of its band
        for (ring in 0..2) {
            val mid = (bands[ring] + bands[ring + 1]) / 2f
            val gs = (bands[ring + 1] - bands[ring]) * 0.9f
            val x = cx; val y = cy - mid
            val pulse = 0.6f + 0.4f * sin(time * 2f + ring)
            Draw.glow(c, px(x), py(y), gs * ps, 0.35f * pulse)
            Glyph.draw(c, RingsMini.TARGET[ring], px(x), py(y), gs * ps, withAlpha(Pal.AMBER_HI, (200 * pulse).toInt()), withAlpha(Pal.AMBER, 40), time)
        }
        // pointer notch like the clock
        Draw.path.reset()
        Draw.path.moveTo(px(cx), py(cy - r + 14f)); Draw.path.lineTo(px(cx - 14f), py(cy - r - 12f)); Draw.path.lineTo(px(cx + 14f), py(cy - r - 12f)); Draw.path.close()
        Draw.fill.color = Pal.AMBER_DK
        c.drawPath(Draw.path, Draw.fill)
        // brass rim
        Draw.stroke.color = Pal.BRASS; Draw.stroke.strokeWidth = 26f * ps
        c.drawCircle(px(cx), py(cy), (r + 13f) * ps, Draw.stroke)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 3f * ps
        c.drawCircle(px(cx), py(cy), (r + 26f) * ps, Draw.stroke)
        c.drawCircle(px(cx), py(cy), r * ps, Draw.stroke)
    }
}
