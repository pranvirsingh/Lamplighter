package com.pranvir.lamplighter

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Wick, the little tin lamplighter with a flame in his head-lantern. */
class Wick {
    companion object {
        const val IDLE = 0
        const val WALK = 1
        const val REACH_UP = 2
        const val REACH_DOWN = 3
        const val PUSH = 4
        const val RAISE_POLE = 5
        const val GIVE = 6
        const val SHAKE = 7
        const val NOD = 8
        const val HAPPY = 9
        const val SLEEP = 10
        const val OIL = 11
        const val CRANK = 12
        const val SPEED = 250f
    }

    var x = 300f
    var ground = 760f
    var yOff = 0f
    var face = 1
    var targetX = 300f
    var walking = false
    private var walkPhase = 0f
    var pose = IDLE
        private set
    private var poseT = 0f
    var hasPole = false
    var poleFlame = 0f
    var flame = 1f
    var flameTarget = 1f
    var stepEvent = false

    // smoothed pose parameters
    private var aFront = 0.25f
    private var aBack = -0.15f
    private var lean = 0f
    private var crouch = 0f
    private var lift = 0f
    private var headTilt = 0f
    private var eyesClosed = 0f
    private var hop = 0f

    fun setPose(p: Int) { if (pose != p) { pose = p; poseT = 0f } }

    fun walkTo(tx: Float) {
        targetX = tx
        if (abs(tx - x) > 2f) {
            walking = true
            face = if (tx > x) 1 else -1
        }
    }

    fun update(dt: Float, time: Float) {
        poseT += dt
        stepEvent = false
        if (walking) {
            val d = targetX - x
            val step = SPEED * dt
            if (abs(d) <= step) { x = targetX; walking = false }
            else { x += step * (if (d > 0) 1 else -1); face = if (d > 0) 1 else -1 }
            val before = walkPhase
            walkPhase += dt * 9f
            if ((before / PI.toFloat()).toInt() != (walkPhase / PI.toFloat()).toInt()) stepEvent = true
        } else walkPhase = 0f

        var tf = 0.25f; var tb = -0.15f; var tl = 0f; var tc = 0f; var tlift = 0f; var th = 0f; var te = 0f; var thop = 0f
        val breathe = sin(time * 2.2f) * 0.04f
        if (walking) {
            tf = sin(walkPhase) * 0.6f; tb = -sin(walkPhase) * 0.6f; tl = 0.06f
        } else when (pose) {
            IDLE -> { tf = 0.2f + breathe; tb = -0.2f - breathe }
            REACH_UP -> { tf = 2.9f; tb = -0.3f; tc = -8f }
            REACH_DOWN -> { tf = 1.2f; tb = 0.2f; tc = 22f; tl = 0.25f; th = 0.25f }
            PUSH -> { tf = 1.45f; tb = 1.35f; tl = 0.28f }
            RAISE_POLE -> { tf = 2.55f; tb = -0.3f; tlift = 1f; th = -0.3f }
            GIVE -> { tf = 1.4f; tb = -0.2f; tl = 0.08f }
            SHAKE -> { th = sin(poseT * 16f) * 0.22f; tf = 0.1f; tb = -0.1f }
            NOD -> { th = abs(sin(poseT * 9f)) * 0.25f }
            HAPPY -> { tf = 2.6f + sin(time * 12f) * 0.25f; tb = -2.6f - sin(time * 12f) * 0.25f; thop = abs(sin(poseT * 7f)) * 22f }
            SLEEP -> { tf = 0.05f; tb = -0.05f; tc = 10f; th = 0.35f; te = 1f }
            OIL -> { tf = 1.1f + sin(poseT * 8f) * 0.1f; tb = -0.2f; tl = 0.1f }
            CRANK -> { tf = 1.3f + sin(poseT * 6f) * 0.5f; tb = 1.1f + cos(poseT * 6f) * 0.4f; tl = 0.12f }
        }
        val k = min(1f, dt * 12f)
        aFront += (tf - aFront) * k; aBack += (tb - aBack) * k; lean += (tl - lean) * k; crouch += (tc - crouch) * k
        lift += (tlift - lift) * k; headTilt += (th - headTilt) * k; eyesClosed += (te - eyesClosed) * k; hop += (thop - hop) * k
        flame += (flameTarget - flame) * min(1f, dt * 3f)
    }

    /** Head lantern flame position (world). */
    fun flameX() = x + face * (-2f)
    fun flameY() = ground + yOff - 186f + crouch - hop
    fun headTopY() = ground + yOff - 215f + crouch - hop
    fun poleTipX() = x + face * 62f
    fun poleTipY() = ground + yOff - 340f

    fun draw(c: Canvas, time: Float, lookX: Float = 0f) {
        val st = Draw.stroke
        val f = Draw.fill
        c.save()
        c.translate(x, ground + yOff - hop)
        c.scale(face.toFloat(), 1f)
        val bob = if (walking) abs(sin(walkPhase)) * -4f else sin(time * 2.2f) * 1.2f
        // head glow
        Draw.glow(c, -2f, -186f + crouch + bob, 150f * flame, 0.55f * flame)

        // pole on the back
        if (hasPole && lift < 0.5f) {
            st.color = Pal.INK; st.strokeWidth = 5f
            c.drawLine(-34f, -34f + crouch, -8f, -232f + crouch, st)
            st.strokeWidth = 3f
            Draw.r1.set(-18f, -250f + crouch, 2f, -228f + crouch)
            c.drawArc(Draw.r1, 180f, 200f, false, st)
        }

        // legs
        val swing = if (walking) sin(walkPhase) * 14f else 0f
        st.color = Pal.INK; st.strokeWidth = 5f
        for (s in -1..1 step 2) {
            val fx = s * 11f + swing * s
            val lift2 = if (walking) max(0f, sin(walkPhase * 1f + (if (s > 0) 0f else PI.toFloat()))) * 6f else 0f
            c.drawLine(s * 11f, -38f + crouch * 0.6f + bob, fx, -lift2, st)
            f.color = Pal.INK
            Draw.r1.set(fx - 9f, -8f - lift2, fx + 14f, 2f - lift2)
            c.drawOval(Draw.r1, f)
        }

        c.save()
        c.translate(0f, crouch + bob)
        c.rotate(lean * 40f, 0f, -38f)
        // back arm
        arm(c, -26f, -102f, aBack, false)
        // body (a tin can)
        Draw.r1.set(-32f, -120f, 32f, -36f)
        f.color = Pal.TIN
        c.drawRoundRect(Draw.r1, 12f, 12f, f)
        f.color = withAlpha(Pal.INK, 40)
        Draw.r2.set(-32f, -120f, -14f, -36f)
        c.drawRoundRect(Draw.r2, 10f, 10f, f)
        st.color = Pal.INK; st.strokeWidth = 3.5f
        c.drawRoundRect(Draw.r1, 12f, 12f, st)
        st.strokeWidth = 2f; st.color = Pal.T3
        c.drawLine(-30f, -96f, 30f, -96f, st)
        c.drawLine(-30f, -58f, 30f, -58f, st)
        // chest dial
        f.color = Pal.PAPER_HI
        c.drawCircle(12f, -78f, 9f, f)
        st.color = Pal.INK; st.strokeWidth = 2f
        c.drawCircle(12f, -78f, 9f, st)
        c.drawLine(12f, -78f, 12f + cos(time * 0.7f) * 6f, -78f + sin(time * 0.7f) * 6f, st)
        f.color = Pal.INK
        for (ry in intArrayOf(-110, -46)) for (rx in intArrayOf(-22, 22)) c.drawCircle(rx.toFloat(), ry.toFloat(), 2.2f, f)
        // spring neck
        st.strokeWidth = 3f
        Draw.path.reset()
        Draw.path.moveTo(0f, -120f)
        for (k in 1..5) Draw.path.lineTo(if (k % 2 == 0) -6f else 6f, -120f - k * 3f)
        c.drawPath(Draw.path, st)

        // head
        c.save()
        c.rotate(headTilt * 57f, 0f, -134f)
        Draw.r1.set(-30f, -176f, 30f, -128f)
        f.color = Pal.TIN
        c.drawOval(Draw.r1, f)
        st.color = Pal.INK; st.strokeWidth = 3.5f
        c.drawOval(Draw.r1, st)
        // eyes
        val blink = if (((time + 1.3f) % 4.1f) < 0.12f) 1f else eyesClosed
        eye(c, 6f, -153f, 8.5f, blink, lookX)
        eye(c, 22f, -151f, 7.5f, blink, lookX)
        // lantern on top
        f.color = Pal.T4
        Draw.r2.set(-14f, -180f, 14f, -172f)
        c.drawRoundRect(Draw.r2, 3f, 3f, f)
        Draw.path.reset()
        Draw.path.moveTo(-11f, -180f); Draw.path.lineTo(-9f, -206f); Draw.path.lineTo(9f, -206f); Draw.path.lineTo(11f, -180f); Draw.path.close()
        f.color = lerpColor(withAlpha(Pal.PAPER_HI, 120), withAlpha(Pal.AMBER_HI, 160), flame)
        c.drawPath(Draw.path, f)
        Draw.flame(c, -1f, -181f, 7.5f * flame, time, 0.3f)
        st.strokeWidth = 2.5f
        c.drawPath(Draw.path, st)
        c.drawLine(0f, -206f, 0f, -180f, Draw.stroke.also { it.strokeWidth = 1.2f })
        st.strokeWidth = 2.5f
        f.color = Pal.T4
        Draw.path.reset()
        Draw.path.moveTo(-13f, -206f); Draw.path.lineTo(0f, -216f); Draw.path.lineTo(13f, -206f); Draw.path.close()
        c.drawPath(Draw.path, f); c.drawPath(Draw.path, st)
        Draw.r2.set(-4f, -224f, 4f, -214f)
        c.drawOval(Draw.r2, st)
        c.restore()

        // front arm (with pole when raised)
        val hand = arm(c, 28f, -102f, aFront, true)
        if (hasPole && lift >= 0.5f) {
            st.color = Pal.INK; st.strokeWidth = 5f
            val tx = 62f; val ty = -340f - crouch - bob
            c.drawLine(hand[0], hand[1], tx, ty, st)
            st.strokeWidth = 3f
            Draw.r2.set(tx - 10f, ty - 12f, tx + 10f, ty + 8f)
            c.drawArc(Draw.r2, 180f, 200f, false, st)
            if (poleFlame > 0.01f) {
                Draw.glow(c, tx, ty, 120f * poleFlame, 0.7f * poleFlame)
                Draw.flame(c, tx, ty + 2f, 8f * poleFlame, time, 1.7f)
            }
        }
        c.restore()
        c.restore()
    }

    private val handOut = FloatArray(2)

    private fun arm(c: Canvas, sx: Float, sy: Float, a: Float, front: Boolean): FloatArray {
        val st = Draw.stroke
        val ex = sx + sin(a) * 24f
        val ey = sy + cos(a) * 24f
        val a2 = a + 0.35f
        val hx = ex + sin(a2) * 24f
        val hy = ey + cos(a2) * 24f
        st.color = Pal.INK; st.strokeWidth = 4.5f
        c.drawLine(sx, sy, ex, ey, st)
        c.drawLine(ex, ey, hx, hy, st)
        Draw.fill.color = if (front) Pal.TIN else Pal.TIN_DK
        c.drawCircle(hx, hy, 6.5f, Draw.fill)
        st.strokeWidth = 2.5f
        c.drawCircle(hx, hy, 6.5f, st)
        handOut[0] = hx; handOut[1] = hy
        return handOut
    }

    private fun eye(c: Canvas, x: Float, y: Float, r: Float, closed: Float, look: Float) {
        Draw.fill.color = Pal.PAPER_HI
        c.drawCircle(x, y, r, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = 2f
        c.drawCircle(x, y, r, Draw.stroke)
        if (closed > 0.6f) {
            Draw.fill.color = Pal.TIN
            c.drawCircle(x, y, r * 0.95f, Draw.fill)
            c.drawLine(x - r, y + 1f, x + r, y + 1f, Draw.stroke)
            return
        }
        Draw.fill.color = Pal.INK
        c.drawCircle(x + 2f + look * 1.5f, y + 0.5f, r * 0.5f, Draw.fill)
        Draw.fill.color = Pal.PAPER_HI
        c.drawCircle(x + 3.5f, y - 2f, r * 0.17f, Draw.fill)
        if (closed > 0.05f) {
            Draw.fill.color = Pal.TIN
            Draw.r2.set(x - r, y - r, x + r, y - r + 2 * r * closed)
            c.drawRect(Draw.r2, Draw.fill)
        }
    }
}

/** The townsfolk and critters. All drawn with the same charcoal hand. */
object Folk {
    private val p = Path()

    fun crow(c: Canvas, x: Float, y: Float, time: Float, caw: Float, flap: Float, face: Int = -1) {
        c.save()
        c.translate(x, y)
        c.scale(face.toFloat(), 1f)
        val bob = if (flap > 0f) 0f else abs(sin(time * 1.3f)) * 3f
        val f = Draw.fill; val st = Draw.stroke
        f.color = Pal.INK
        // tail
        p.reset(); p.moveTo(-18f, -14f); p.lineTo(-44f, -4f); p.lineTo(-40f, 4f); p.lineTo(-14f, -4f); p.close()
        c.drawPath(p, f)
        Draw.r1.set(-24f, -32f, 16f, -2f)
        c.drawOval(Draw.r1, f)
        // head
        c.drawCircle(16f, -36f - bob, 12f, f)
        // beak
        p.reset()
        val open = caw * 10f
        p.moveTo(24f, -40f - bob); p.lineTo(44f, -36f - bob - open * 0.2f); p.lineTo(26f, -34f - bob); p.close()
        c.drawPath(p, f)
        p.reset(); p.moveTo(26f, -34f - bob); p.lineTo(42f, -30f - bob + open); p.lineTo(24f, -31f - bob); p.close()
        c.drawPath(p, f)
        f.color = Pal.PAPER_HI
        c.drawCircle(19f, -39f - bob, 2.6f, f)
        // wing
        f.color = Pal.T5
        if (flap > 0f) {
            val w = sin(time * 28f) * 30f
            p.reset(); p.moveTo(-8f, -22f); p.lineTo(-20f, -60f - w); p.lineTo(10f, -24f); p.close()
            c.drawPath(p, f)
        } else {
            Draw.r1.set(-18f, -26f, 8f, -10f)
            c.drawOval(Draw.r1, f)
        }
        // legs
        st.color = Pal.INK; st.strokeWidth = 2.5f
        if (flap <= 0f) {
            c.drawLine(-4f, -4f, -6f, 6f, st); c.drawLine(4f, -4f, 4f, 6f, st)
        }
        c.restore()
    }

    fun cat(c: Canvas, x: Float, y: Float, time: Float, mode: Int, lit: Float) {
        // mode 0 = playing with gear, 1 = eating, 2 = curled asleep (happy)
        val f = Draw.fill; val st = Draw.stroke
        c.save()
        c.translate(x, y)
        f.color = Pal.T5
        // tail
        st.color = Pal.T5; st.strokeWidth = 9f
        p.reset(); p.moveTo(-26f, -10f)
        val sw = sin(time * 2.4f) * 16f
        p.quadTo(-62f, -20f + sw, -50f, -58f + sw)
        c.drawPath(p, st)
        if (mode == 2) {
            Draw.r1.set(-40f, -38f, 40f, 0f); c.drawOval(Draw.r1, f)
            c.drawCircle(26f, -26f, 18f, f)
            ears(c, 26f, -26f)
            st.color = Pal.PAPER_HI; st.strokeWidth = 2f
            c.drawLine(18f, -28f, 24f, -27f, st); c.drawLine(30f, -27f, 36f, -28f, st)
            c.restore(); return
        }
        Draw.r1.set(-34f, -62f, 26f, 0f)
        c.drawOval(Draw.r1, f)
        val hy = if (mode == 1) -34f + abs(sin(time * 6f)) * 4f else -74f
        val hx = if (mode == 1) 30f else 6f
        c.drawCircle(hx, hy, 22f, f)
        ears(c, hx, hy)
        // eyes: amber slits
        f.color = lerpColor(Pal.T2, Pal.AMBER, 0.6f + lit * 0.4f)
        if (mode == 1) {
            st.color = Pal.PAPER_HI; st.strokeWidth = 2f
            c.drawLine(hx - 10f, hy - 3f, hx - 3f, hy - 2f, st); c.drawLine(hx + 3f, hy - 2f, hx + 10f, hy - 3f, st)
        } else {
            Draw.r2.set(hx - 12f, hy - 6f, hx - 3f, hy + 3f); c.drawOval(Draw.r2, f)
            Draw.r2.set(hx + 3f, hy - 6f, hx + 12f, hy + 3f); c.drawOval(Draw.r2, f)
            f.color = Pal.INK
            c.drawRect(hx - 8.5f, hy - 5f, hx - 6.5f, hy + 2f, f)
            c.drawRect(hx + 6.5f, hy - 5f, hx + 8.5f, hy + 2f, f)
        }
        // whiskers
        st.color = Pal.T2; st.strokeWidth = 1.2f
        c.drawLine(hx - 6f, hy + 8f, hx - 26f, hy + 5f, st); c.drawLine(hx + 6f, hy + 8f, hx + 26f, hy + 5f, st)
        // batting paw
        if (mode == 0) {
            f.color = Pal.T5
            val pw = sin(time * 3f)
            c.drawCircle(30f + pw * 6f, -20f - max(0f, pw) * 14f, 9f, f)
        }
        c.restore()
    }

    private fun ears(c: Canvas, hx: Float, hy: Float) {
        p.reset()
        p.moveTo(hx - 18f, hy - 8f); p.lineTo(hx - 14f, hy - 32f); p.lineTo(hx - 2f, hy - 18f); p.close()
        p.moveTo(hx + 18f, hy - 8f); p.lineTo(hx + 14f, hy - 32f); p.lineTo(hx + 2f, hy - 18f); p.close()
        c.drawPath(p, Draw.fill)
    }

    /** Round tin fishmonger behind a counter. */
    fun fishmonger(c: Canvas, x: Float, y: Float, time: Float, awake: Float) {
        if (awake < 0.02f) return
        val f = Draw.fill; val st = Draw.stroke
        c.save()
        c.translate(x, y + (1f - smooth(awake)) * 80f)
        f.color = Pal.TIN
        Draw.r1.set(-44f, -110f, 44f, 0f)
        c.drawRoundRect(Draw.r1, 36f, 36f, f)
        st.color = Pal.INK; st.strokeWidth = 3.5f
        c.drawRoundRect(Draw.r1, 36f, 36f, st)
        // apron
        f.color = Pal.PAPER_HI
        Draw.r2.set(-26f, -60f, 26f, 0f); c.drawRect(Draw.r2, f); c.drawRect(Draw.r2, st)
        // cap
        f.color = Pal.T4
        Draw.r2.set(-34f, -128f, 34f, -104f); c.drawRoundRect(Draw.r2, 10f, 10f, f)
        Draw.r2.set(-6f, -118f, 50f, -106f); c.drawRoundRect(Draw.r2, 6f, 6f, f)
        // eyes + moustache
        f.color = Pal.INK
        c.drawCircle(-12f, -86f, 5f, f); c.drawCircle(12f, -86f, 5f, f)
        st.strokeWidth = 5f
        p.reset(); p.moveTo(-22f, -66f); p.quadTo(-10f, -76f, 0f, -70f); p.quadTo(10f, -76f, 22f, -66f)
        c.drawPath(p, st)
        // waving arm
        st.strokeWidth = 5f
        val wave = sin(time * 6f) * 0.5f
        val ax = 42f + cos(-1.2f + wave) * 40f; val ay = -76f + sin(-1.2f + wave) * 40f
        c.drawLine(40f, -70f, ax, ay, st)
        f.color = Pal.TIN; c.drawCircle(ax, ay, 8f, f); st.strokeWidth = 2.5f; c.drawCircle(ax, ay, 8f, st)
        c.restore()
    }

    fun boatman(c: Canvas, x: Float, y: Float, time: Float, awake: Float) {
        val f = Draw.fill; val st = Draw.stroke
        c.save()
        c.translate(x, y)
        val a = smooth(awake)
        // body
        f.color = Pal.T3
        Draw.r1.set(-26f, -70f + a * -16f, 26f, 0f)
        c.drawRoundRect(Draw.r1, 14f, 14f, f)
        st.color = Pal.INK; st.strokeWidth = 3f
        c.drawRoundRect(Draw.r1, 14f, 14f, st)
        // head
        val hy = -84f - a * 20f
        f.color = Pal.TIN
        c.drawCircle(0f, hy, 18f, f); c.drawCircle(0f, hy, 18f, st)
        if (a > 0.5f) {
            f.color = Pal.INK
            c.drawCircle(-6f, hy - 2f, 3.5f, f); c.drawCircle(8f, hy - 2f, 3.5f, f)
            // arm up with the lens
            st.strokeWidth = 4.5f
            c.drawLine(20f, -60f - a * 16f, 44f, -110f, st)
            Icon.draw(c, Icon.LENS, 50f, -126f, 40f, Pal.INK, Pal.BRASS, time)
        }
        // wide straw hat, tipped over the face while asleep
        c.save()
        c.rotate((1f - a) * 24f, 0f, hy)
        f.color = Pal.T2
        Draw.r2.set(-40f, hy - 16f + (1f - a) * 12f, 40f, hy - 4f + (1f - a) * 12f); c.drawOval(Draw.r2, f); c.drawOval(Draw.r2, st)
        p.reset(); p.moveTo(-18f, hy - 12f + (1f - a) * 12f); p.lineTo(0f, hy - 34f + (1f - a) * 12f); p.lineTo(18f, hy - 12f + (1f - a) * 12f); p.close()
        c.drawPath(p, f); c.drawPath(p, st)
        c.restore()
        c.restore()
    }

    fun astronomer(c: Canvas, x: Float, y: Float, time: Float, awake: Float) {
        val f = Draw.fill; val st = Draw.stroke
        val a = smooth(awake)
        c.save()
        c.translate(x, y)
        // chair
        st.color = Pal.INK; st.strokeWidth = 4f
        c.drawLine(-30f, 0f, -30f, -60f, st); c.drawLine(30f, 0f, 30f, -60f, st)
        c.drawLine(-36f, -60f, 36f, -60f, st); c.drawLine(-30f, -60f, -36f, -140f, st)
        // long body
        c.save()
        c.rotate((1f - a) * -14f, 0f, -60f)
        f.color = Pal.T3
        Draw.r1.set(-22f, -170f, 22f, -60f); c.drawRoundRect(Draw.r1, 12f, 12f, f)
        st.strokeWidth = 3f; c.drawRoundRect(Draw.r1, 12f, 12f, st)
        // head
        f.color = Pal.TIN
        c.drawCircle(0f, -190f, 20f, f); c.drawCircle(0f, -190f, 20f, st)
        // spectacles
        st.strokeWidth = 2f
        c.drawCircle(-8f, -192f, 6f, st); c.drawCircle(8f, -192f, 6f, st)
        if (a > 0.5f) { f.color = Pal.INK; c.drawCircle(-8f, -192f, 2.5f, f); c.drawCircle(8f, -192f, 2.5f, f) }
        else { c.drawLine(-13f, -191f, -3f, -191f, st); c.drawLine(3f, -191f, 13f, -191f, st) }
        // spring beard
        st.strokeWidth = 2.5f
        p.reset(); p.moveTo(-8f, -174f)
        for (k in 1..8) p.lineTo(if (k % 2 == 0) -8f else 8f, -174f + k * 8f)
        c.drawPath(p, st)
        // pointy hat with stars
        f.color = Pal.T4
        p.reset(); p.moveTo(-24f, -204f); p.lineTo(6f, -270f); p.lineTo(24f, -204f); p.close()
        c.drawPath(p, f); c.drawPath(p, st)
        Icon.draw(c, Icon.STAR, -2f, -228f, 16f, Pal.INK, Pal.AMBER, time)
        if (a > 0.5f) {
            // wave hello
            st.strokeWidth = 4.5f
            val w = sin(time * 5f) * 0.4f
            c.drawLine(18f, -150f, 18f + cos(-1f + w) * 44f, -150f + sin(-1f + w) * 44f, st)
        }
        c.restore()
        c.restore()
    }

    /** Silhouette townsfolk who come out once the lamps are lit. */
    fun towns(c: Canvas, x: Float, y: Float, time: Float, kind: Int, appear: Float) {
        if (appear < 0.02f) return
        val f = Draw.fill
        c.save()
        c.translate(x, y + (1f - smooth(appear)) * 30f)
        val sway = sin(time * 1.4f + kind) * 3f
        c.rotate(sway * 0.6f, 0f, 0f)
        val col = withAlpha(Pal.T5, (255 * smooth(appear)).toInt())
        f.color = col
        val hgt = 120f + (kind % 3) * 18f
        Draw.r1.set(-20f, -hgt, 20f, 0f)
        c.drawRoundRect(Draw.r1, 16f, 16f, f)
        c.drawCircle(0f, -hgt - 14f, 15f, f)
        when (kind % 4) {
            0 -> { Draw.r2.set(-14f, -hgt - 46f, 14f, -hgt - 22f); c.drawRect(Draw.r2, f); Draw.r2.set(-24f, -hgt - 26f, 24f, -hgt - 20f); c.drawRect(Draw.r2, f) }
            1 -> { Draw.r2.set(-24f, -hgt - 30f, 24f, -hgt - 16f); c.drawOval(Draw.r2, f) }
            2 -> { Draw.stroke.color = col; Draw.stroke.strokeWidth = 3f; c.drawLine(-10f, -hgt - 26f, -18f, -hgt - 44f, Draw.stroke); c.drawLine(10f, -hgt - 26f, 18f, -hgt - 44f, Draw.stroke) }
        }
        // little hand lantern
        if (kind % 2 == 0) {
            Draw.stroke.color = col; Draw.stroke.strokeWidth = 3f
            c.drawLine(18f, -hgt * 0.55f, 30f, -hgt * 0.4f, Draw.stroke)
            Draw.glow(c, 30f, -hgt * 0.32f, 60f, 0.6f * appear)
            Draw.fill.color = alphaF(Pal.AMBER, appear)
            c.drawCircle(30f, -hgt * 0.32f, 6f, Draw.fill)
        }
        // eyes glint
        Draw.fill.color = alphaF(Pal.PAPER_HI, appear)
        c.drawCircle(-5f, -hgt - 16f, 2.2f, Draw.fill); c.drawCircle(5f, -hgt - 16f, 2.2f, Draw.fill)
        c.restore()
    }
}
