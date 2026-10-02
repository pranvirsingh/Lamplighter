package com.pranvir.lamplighter

import java.io.ByteArrayOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

object Sfx {
    const val PICKUP = 0; const val BUBBLE = 1; const val OPEN = 2; const val CLOSE = 3; const val WHOOSH = 4
    const val LAMP_ON = 5; const val CLICK = 6; const val NOPE = 7; const val IGNITE = 8; const val TWINKLE = 9
    const val STEP = 10; const val SOLVE = 11; const val HISS = 12; const val RATCHET = 13; const val CLUNK = 14
    const val CLINK = 15; const val SCRAPE = 16; const val SQUEAK = 17; const val DOOR = 18; const val RATTLE = 19
    const val KNOCK = 20; const val CAW = 21; const val RUSTLE = 22; const val CLANK = 23; const val SNORE = 24
    const val MEOW = 25; const val RUMBLE = 26; const val UNLOCK = 27; const val CHIME = 28
    const val COUNT = 29

    const val AMB_NONE = 0; const val AMB_WIND = 1; const val AMB_WORKSHOP = 2; const val AMB_WATER = 3
    const val AMB_CRICKETS = 4; const val AMB_TICK = 5; const val AMB_COUNT = 6
}

/** Everything you hear is synthesised here. */
object Synth {
    const val RATE = 22050
    private const val TAU = (2 * PI).toFloat()
    const val STEMS = 5

    fun hz(m: Float) = (440.0 * 2.0.pow((m - 69.0) / 12.0)).toFloat()
    private fun buf(sec: Float) = FloatArray((sec * RATE).toInt().coerceAtLeast(1))
    private fun env(t: Float, a: Float, d: Float) = (if (t < a) t / a else 1f) * exp(-(t - a).coerceAtLeast(0f) * d)

    fun pcm(f: FloatArray, gain: Float, normalize: Boolean = true): ShortArray {
        var peak = 0.0001f
        if (normalize) for (v in f) { val a = abs(v); if (a > peak) peak = a } else peak = 1f
        val g = gain / peak
        return ShortArray(f.size) { (tanh((f[it] * g).toDouble()) * 30000).toInt().toShort() }
    }

    private fun noiseBurst(b: FloatArray, start: Float, len: Float, amp: Float, lp: Float, rnd: Random) {
        val o = (start * RATE).toInt(); val n = (len * RATE).toInt()
        var y = 0f
        for (j in 0 until n) {
            val i = o + j; if (i >= b.size) break
            val t = j / RATE.toFloat()
            y += ((rnd.nextFloat() - 0.5f) - y) * lp
            b[i] += y * amp * exp(-t * 6f / len)
        }
    }

    private fun tone(b: FloatArray, start: Float, f: Float, amp: Float, decay: Float, partials: FloatArray, pamps: FloatArray, attack: Float = 0.003f) {
        val o = (start * RATE).toInt()
        for (i in o until b.size) {
            val t = (i - o) / RATE.toFloat()
            val e = env(t, attack, decay)
            if (t > attack && e < 0.0005f) break
            var s = 0f
            for (k in partials.indices) s += pamps[k] * sin(TAU * f * partials[k] * t)
            b[i] += s * e * amp
        }
    }

    private val BELL_P = floatArrayOf(1f, 2f, 2.76f, 5.4f)
    private val BELL_A = floatArrayOf(1f, 0.45f, 0.3f, 0.1f)
    private val BOX_P = floatArrayOf(1f, 2f, 4.2f)
    private val BOX_A = floatArrayOf(1f, 0.25f, 0.15f)

    fun render(id: Int): ShortArray {
        val rnd = Random(4000L + id)
        return when (id) {
            Sfx.PICKUP -> { val b = buf(0.7f); tone(b, 0f, hz(81f), 0.8f, 7f, BELL_P, BELL_A); tone(b, 0.09f, hz(88f), 0.8f, 6f, BELL_P, BELL_A); pcm(b, 0.8f) }
            Sfx.BUBBLE -> {
                val b = buf(0.16f); var ph = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); ph += TAU * (300f + 900f * t / 0.16f) / RATE; b[i] = sin(ph) * env(t, 0.005f, 22f) }
                pcm(b, 0.5f)
            }
            Sfx.OPEN -> { val b = buf(0.5f); tone(b, 0f, hz(74f), 0.6f, 8f, BOX_P, BOX_A); tone(b, 0.07f, hz(79f), 0.6f, 8f, BOX_P, BOX_A); noiseBurst(b, 0f, 0.15f, 0.4f, 0.2f, rnd); pcm(b, 0.6f) }
            Sfx.CLOSE -> { val b = buf(0.4f); tone(b, 0f, hz(79f), 0.6f, 9f, BOX_P, BOX_A); tone(b, 0.07f, hz(72f), 0.6f, 9f, BOX_P, BOX_A); pcm(b, 0.55f) }
            Sfx.WHOOSH -> {
                val b = buf(0.6f); var y = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); val lp = 0.02f + 0.25f * sin(PI.toFloat() * t / 0.6f); y += ((rnd.nextFloat() - 0.5f) - y) * lp; b[i] = y * sin(PI.toFloat() * t / 0.6f) }
                pcm(b, 0.7f)
            }
            Sfx.LAMP_ON -> {
                val b = buf(2.2f)
                var y = 0f
                for (i in 0 until (0.5f * RATE).toInt()) { val t = i / RATE.toFloat(); y += ((rnd.nextFloat() - 0.5f) - y) * 0.08f; b[i] += y * 2f * env(t, 0.02f, 7f) + 0.5f * sin(TAU * 70f * t) * env(t, 0.01f, 9f) }
                for ((k, m) in floatArrayOf(69f, 73f, 76f, 81f).withIndex()) tone(b, 0.12f + k * 0.08f, hz(m), 0.55f, 2.2f, BELL_P, BELL_A)
                pcm(b, 0.85f)
            }
            Sfx.CLICK -> { val b = buf(0.05f); for (i in b.indices) { val t = i / RATE.toFloat(); b[i] = (sin(TAU * 1900f * t) + (rnd.nextFloat() - 0.5f)) * env(t, 0.0005f, 120f) }; pcm(b, 0.45f) }
            Sfx.NOPE -> { val b = buf(0.3f); tone(b, 0f, hz(50f), 0.8f, 10f, floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 0.4f, 0.2f)); tone(b, 0.12f, hz(47f), 0.8f, 10f, floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 0.4f, 0.2f)); pcm(b, 0.6f) }
            Sfx.IGNITE -> {
                val b = buf(0.8f); var y = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); y += ((rnd.nextFloat() - 0.5f) - y) * (0.05f + 0.3f * exp(-t * 8f)); b[i] = y * env(t, 0.03f, 4f) * 2f }
                tone(b, 0.1f, hz(84f), 0.3f, 4f, BELL_P, BELL_A)
                pcm(b, 0.7f)
            }
            Sfx.TWINKLE -> { val b = buf(0.6f); for ((k, m) in floatArrayOf(88f, 91f, 96f).withIndex()) tone(b, k * 0.06f, hz(m), 0.5f, 7f, BELL_P, BELL_A); pcm(b, 0.55f) }
            Sfx.STEP -> {
                val b = buf(0.09f)
                for (i in b.indices) { val t = i / RATE.toFloat(); b[i] = (0.6f * sin(TAU * 520f * t) + 0.4f * sin(TAU * 1310f * t) + (rnd.nextFloat() - 0.5f) * 0.6f) * env(t, 0.001f, 60f) }
                pcm(b, 0.5f)
            }
            Sfx.SOLVE -> { val b = buf(1.6f); for ((k, m) in floatArrayOf(69f, 72f, 76f, 81f, 84f).withIndex()) tone(b, k * 0.1f, hz(m), 0.6f, 3f, BELL_P, BELL_A); pcm(b, 0.8f) }
            Sfx.HISS -> {
                val b = buf(0.9f); var y = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); val n = rnd.nextFloat() - 0.5f; y = n - y * 0.6f; b[i] = y * env(t, 0.02f, 3.5f) }
                pcm(b, 0.55f)
            }
            Sfx.RATCHET -> { val b = buf(0.05f); for (i in b.indices) { val t = i / RATE.toFloat(); b[i] = (rnd.nextFloat() - 0.5f + 0.6f * sin(TAU * 2600f * t)) * env(t, 0.0005f, 150f) }; pcm(b, 0.45f) }
            Sfx.CLUNK -> { val b = buf(0.25f); tone(b, 0f, 110f, 1f, 18f, floatArrayOf(1f, 2.3f, 3.9f), floatArrayOf(1f, 0.5f, 0.3f)); noiseBurst(b, 0f, 0.05f, 0.8f, 0.3f, rnd); pcm(b, 0.7f) }
            Sfx.CLINK -> { val b = buf(0.5f); tone(b, 0f, 1850f, 0.7f, 12f, floatArrayOf(1f, 2.71f, 4.1f), floatArrayOf(1f, 0.5f, 0.3f)); tone(b, 0.11f, 2100f, 0.4f, 14f, floatArrayOf(1f, 2.71f), floatArrayOf(1f, 0.4f)); pcm(b, 0.55f) }
            Sfx.SCRAPE -> {
                val b = buf(1.5f); var y = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); y += ((rnd.nextFloat() - 0.5f) - y) * 0.15f; val am = 0.6f + 0.4f * sin(TAU * 11f * t); b[i] = y * am * (if (t < 0.1f) t / 0.1f else if (t > 1.3f) (1.5f - t) / 0.2f else 1f) }
                pcm(b, 0.5f)
            }
            Sfx.SQUEAK -> {
                val b = buf(1.1f); var ph = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); val f = 900f + 500f * sin(TAU * 1.6f * t) + 80f * sin(TAU * 31f * t); ph += TAU * f / RATE; b[i] = (sin(ph) + 0.3f * sin(ph * 3f)) * sin(PI.toFloat() * t / 1.1f) }
                pcm(b, 0.35f)
            }
            Sfx.DOOR -> {
                val b = buf(1.2f); var ph = 0f
                for (i in 0 until (0.7f * RATE).toInt()) { val t = i / RATE.toFloat(); ph += TAU * (260f - 120f * t) / RATE; b[i] += (sin(ph) + 0.4f * sin(ph * 2.3f)) * sin(PI.toFloat() * t / 0.7f) * 0.5f }
                tone(b, 0.75f, 90f, 1f, 14f, floatArrayOf(1f, 2.4f), floatArrayOf(1f, 0.4f)); noiseBurst(b, 0.75f, 0.1f, 0.6f, 0.2f, rnd)
                pcm(b, 0.6f)
            }
            Sfx.RATTLE -> { val b = buf(0.5f); for (k in 0 until 5) { tone(b, k * 0.08f, 180f + k * 10f, 0.7f, 30f, floatArrayOf(1f, 2.7f), floatArrayOf(1f, 0.6f)); noiseBurst(b, k * 0.08f, 0.03f, 0.5f, 0.4f, rnd) }; pcm(b, 0.55f) }
            Sfx.KNOCK -> { val b = buf(0.5f); for (k in 0 until 3) { tone(b, k * 0.15f, 140f, 1f, 30f, floatArrayOf(1f, 2.1f), floatArrayOf(1f, 0.4f)); noiseBurst(b, k * 0.15f, 0.03f, 0.5f, 0.3f, rnd) }; pcm(b, 0.6f) }
            Sfx.CAW -> {
                val b = buf(0.8f)
                for (c in 0 until 2) {
                    val o = (c * 0.34f * RATE).toInt(); var ph = 0f; var y = 0f
                    for (j in 0 until (0.28f * RATE).toInt()) {
                        val i = o + j; if (i >= b.size) break
                        val t = j / RATE.toFloat()
                        val f = 520f + 160f * sin(PI.toFloat() * t / 0.28f) - 120f * t
                        ph += TAU * f / RATE
                        val saw = 2f * ((ph / TAU) % 1f) - 1f
                        y += (saw * 0.8f + (rnd.nextFloat() - 0.5f) * 0.6f - y) * 0.35f
                        b[i] += y * sin(PI.toFloat() * t / 0.28f)
                    }
                }
                pcm(b, 0.55f)
            }
            Sfx.RUSTLE -> { val b = buf(0.6f); for (k in 0 until 8) noiseBurst(b, k * 0.06f + rnd.nextFloat() * 0.03f, 0.05f, 0.6f, 0.5f, rnd); pcm(b, 0.45f) }
            Sfx.CLANK -> { val b = buf(0.7f); tone(b, 0f, 220f, 1f, 7f, floatArrayOf(1f, 2.76f, 5.4f, 8.9f), floatArrayOf(1f, 0.6f, 0.4f, 0.2f)); noiseBurst(b, 0f, 0.05f, 0.8f, 0.4f, rnd); pcm(b, 0.65f) }
            Sfx.SNORE -> {
                val b = buf(1.4f); var y = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); y += ((rnd.nextFloat() - 0.5f) - y) * 0.06f; val buzz = sin(TAU * 38f * t) * 0.5f + 0.5f; b[i] = y * buzz * sin(PI.toFloat() * (t / 1.4f)) * 3f }
                pcm(b, 0.4f)
            }
            Sfx.MEOW -> {
                val b = buf(0.7f); var ph = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat(); val p = t / 0.7f
                    val f = 480f + 380f * sin(PI.toFloat() * p) - 150f * p
                    ph += TAU * f / RATE
                    val formant = 0.5f + 0.5f * sin(PI.toFloat() * p)
                    b[i] = (sin(ph) + formant * 0.6f * sin(ph * 2f) + formant * 0.35f * sin(ph * 3f)) * sin(PI.toFloat() * p).pow(0.6f)
                }
                pcm(b, 0.45f)
            }
            Sfx.RUMBLE -> {
                val b = buf(2.8f); var y = 0f
                for (i in b.indices) { val t = i / RATE.toFloat(); y += ((rnd.nextFloat() - 0.5f) - y) * 0.03f; val cl = if ((t * 9f) % 1f < 0.1f) 0.4f else 0f; b[i] = (y * 3f + cl * (rnd.nextFloat() - 0.5f)) * (if (t < 0.2f) t / 0.2f else if (t > 2.4f) (2.8f - t) / 0.4f else 1f) }
                pcm(b, 0.7f)
            }
            Sfx.UNLOCK -> { val b = buf(0.6f); tone(b, 0f, 900f, 0.6f, 25f, floatArrayOf(1f, 2.4f), floatArrayOf(1f, 0.5f)); tone(b, 0.16f, 600f, 0.8f, 20f, floatArrayOf(1f, 2.4f, 3.3f), floatArrayOf(1f, 0.5f, 0.3f)); noiseBurst(b, 0.16f, 0.04f, 0.5f, 0.4f, rnd); pcm(b, 0.6f) }
            Sfx.CHIME -> {
                val b = buf(3.5f)
                val notes = floatArrayOf(76f, 72f, 74f, 67f, 67f, 74f, 76f, 72f)
                for ((k, m) in notes.withIndex()) tone(b, k * 0.35f, hz(m - 12f), 0.6f, 1.2f, BELL_P, BELL_A)
                pcm(b, 0.75f)
            }
            else -> ShortArray(1)
        }
    }

    // ================================================================== ambience (seamless loops)

    private fun loopify(b: FloatArray, fade: Int): FloatArray {
        // cross-fade the tail into the head so the loop has no seam
        val n = b.size - fade
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = b[i]
        for (i in 0 until fade) {
            val t = i / fade.toFloat()
            out[i] = b[i] * t + b[n + i] * (1f - t)
        }
        return out
    }

    fun renderAmbient(id: Int): ShortArray {
        val rnd = Random(9000L + id)
        val fade = RATE / 2
        return when (id) {
            Sfx.AMB_WIND -> {
                val b = FloatArray(RATE * 8 + fade); var y = 0f; var y2 = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    y += ((rnd.nextFloat() - 0.5f) - y) * 0.02f
                    y2 += (y - y2) * 0.2f
                    val gust = 0.5f + 0.35f * sin(TAU * t / 8f) + 0.15f * sin(TAU * t / 2.7f)
                    b[i] = y2 * gust * 6f
                }
                pcm(loopify(b, fade), 0.45f)
            }
            Sfx.AMB_WORKSHOP -> {
                val b = FloatArray(RATE * 4 + fade)
                var y = 0f
                for (i in b.indices) { y += ((rnd.nextFloat() - 0.5f) - y) * 0.01f; b[i] = y * 1.5f }
                for (k in 0 until 4) {
                    tone(b, k * 1f + 0.02f, if (k % 2 == 0) 1300f else 1050f, 0.25f, 60f, floatArrayOf(1f, 2.3f), floatArrayOf(1f, 0.4f))
                }
                pcm(loopify(b, fade), 0.3f)
            }
            Sfx.AMB_WATER -> {
                val b = FloatArray(RATE * 6 + fade); var y = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    y += ((rnd.nextFloat() - 0.5f) - y) * 0.05f
                    val lap = (0.5f + 0.5f * sin(TAU * t * 0.55f + sin(TAU * t * 0.23f) * 2f)).pow(3f)
                    b[i] = y * lap * 4f
                }
                for (k in 0 until 5) {
                    val s = rnd.nextFloat() * 5.5f
                    val o = (s * RATE).toInt(); var ph = 0f
                    for (j in 0 until (0.08f * RATE).toInt()) { val i = o + j; if (i >= b.size) break; val t = j / RATE.toFloat(); ph += TAU * (600f + 1800f * t / 0.08f) / RATE; b[i] += sin(ph) * env(t, 0.003f, 40f) * 0.25f }
                }
                pcm(loopify(b, fade), 0.45f)
            }
            Sfx.AMB_CRICKETS -> {
                val b = FloatArray(RATE * 5 + fade); var y = 0f
                for (i in b.indices) { y += ((rnd.nextFloat() - 0.5f) - y) * 0.015f; b[i] = y * 2f }
                var t0 = 0.1f
                while (t0 < 5f) {
                    val f = 4200f + rnd.nextFloat() * 700f
                    for (k in 0 until 3) {
                        val o = ((t0 + k * 0.045f) * RATE).toInt()
                        for (j in 0 until (0.03f * RATE).toInt()) { val i = o + j; if (i >= b.size) break; val t = j / RATE.toFloat(); b[i] += sin(TAU * f * t) * sin(PI.toFloat() * t / 0.03f) * 0.12f }
                    }
                    t0 += 0.35f + rnd.nextFloat() * 0.6f
                }
                pcm(loopify(b, fade), 0.35f)
            }
            Sfx.AMB_TICK -> {
                val b = FloatArray(RATE * 2 + fade)
                for (i in b.indices) { val t = i / RATE.toFloat(); b[i] = 0.05f * sin(TAU * 55f * t) + 0.03f * sin(TAU * 110f * t) }
                for (k in 0 until 3) {
                    tone(b, k * 1f, if (k % 2 == 0) 700f else 560f, 0.9f, 35f, floatArrayOf(1f, 2.4f, 3.7f), floatArrayOf(1f, 0.6f, 0.3f))
                    tone(b, k * 1f + 0.01f, 90f, 0.6f, 9f, floatArrayOf(1f), floatArrayOf(1f))
                }
                pcm(loopify(b, fade), 0.4f)
            }
            else -> ShortArray(RATE / 10)
        }
    }

    // ================================================================== the waltz

    private const val BEAT = 60f / 88f
    private const val BARS = 16
    val LOOP_SEC = BARS * 3 * BEAT

    private val ROOT = intArrayOf(45, 41, 48, 43, 45, 41, 40, 40, 45, 41, 48, 43, 50, 40, 45, 40)
    private val CHORD = arrayOf(
        intArrayOf(57, 60, 64), intArrayOf(57, 60, 65), intArrayOf(55, 60, 64), intArrayOf(55, 59, 62),
        intArrayOf(57, 60, 64), intArrayOf(57, 60, 65), intArrayOf(56, 59, 62), intArrayOf(56, 59, 64),
        intArrayOf(57, 60, 64), intArrayOf(57, 60, 65), intArrayOf(55, 60, 64), intArrayOf(55, 59, 62),
        intArrayOf(57, 62, 65), intArrayOf(56, 59, 62), intArrayOf(57, 60, 64), intArrayOf(56, 59, 62))
    // melody: bar, slot (eighths, 6 per bar), length (eighths), midi
    private val MEL = intArrayOf(
        0, 0, 2, 76, 0, 2, 1, 72, 0, 3, 1, 74, 0, 4, 2, 76,
        1, 0, 3, 77, 1, 3, 1, 76, 1, 4, 2, 72,
        2, 0, 2, 79, 2, 2, 2, 76, 2, 4, 2, 72,
        3, 0, 4, 74, 3, 4, 1, 71, 3, 5, 1, 74,
        4, 0, 2, 76, 4, 2, 1, 81, 4, 3, 1, 79, 4, 4, 2, 76,
        5, 0, 2, 77, 5, 2, 2, 81, 5, 4, 2, 84,
        6, 0, 3, 83, 6, 3, 1, 80, 6, 4, 2, 76,
        7, 0, 4, 74, 7, 4, 2, 68,
        8, 0, 2, 69, 8, 2, 2, 72, 8, 4, 2, 76,
        9, 0, 3, 81, 9, 3, 1, 79, 9, 4, 2, 77,
        10, 0, 2, 76, 10, 2, 2, 79, 10, 4, 2, 84,
        11, 0, 4, 83, 11, 4, 2, 79,
        12, 0, 2, 81, 12, 2, 2, 77, 12, 4, 2, 74,
        13, 0, 2, 80, 13, 2, 2, 83, 13, 4, 2, 86,
        14, 0, 4, 84, 14, 4, 1, 83, 14, 5, 1, 81,
        15, 0, 4, 80, 15, 4, 2, 76)
    private val COUNTER = intArrayOf(72, 69, 67, 71, 72, 69, 68, 71, 72, 72, 76, 74, 69, 68, 69, 68)

    /** Renders one of five stems: 0 music box, 1 pizzicato bass, 2 harmonium, 3 whistle counter-melody, 4 celesta. */
    fun renderStem(stem: Int): ShortArray {
        val n = (LOOP_SEC * RATE).toInt()
        val b = FloatArray(n)
        val rnd = Random(700L + stem)
        val bar = 3 * BEAT
        val eighth = BEAT / 2f
        when (stem) {
            0 -> {
                var k = 0
                while (k + 3 < MEL.size) {
                    val t = MEL[k] * bar + MEL[k + 1] * eighth
                    wrapTone(b, t, hz(MEL[k + 3].toFloat()), 0.6f, 2.6f, BOX_P, BOX_A)
                    k += 4
                }
            }
            1 -> for (i in 0 until BARS) pluck(b, i * bar, hz(ROOT[i].toFloat()), 0.9f, rnd)
            2 -> for (i in 0 until BARS) for (beat in 1..2) for (m in CHORD[i]) reed(b, i * bar + beat * BEAT, BEAT * 0.7f, hz(m.toFloat()), 0.22f)
            3 -> for (i in 0 until BARS) whistle(b, i * bar + BEAT * 0.1f, bar * 0.92f, hz(COUNTER[i].toFloat()), 0.5f)
            4 -> for (i in 0 until BARS step 2) {
                val c = CHORD[i]
                for ((j, m) in intArrayOf(c[0] + 24, c[1] + 24, c[2] + 24, c[1] + 36).withIndex())
                    wrapTone(b, i * bar + j * eighth * 0.5f, hz(m.toFloat()), 0.35f, 3f, BELL_P, BELL_A)
            }
        }
        return pcm(b, 0.9f)
    }

    private fun wrapTone(b: FloatArray, start: Float, f: Float, amp: Float, decay: Float, parts: FloatArray, pa: FloatArray) {
        val n = b.size
        val o = (start * RATE).toInt()
        val len = (RATE * 3f).toInt()
        for (j in 0 until len) {
            val t = j / RATE.toFloat()
            val e = env(t, 0.004f, decay)
            if (e < 0.0005f && t > 0.01f) break
            var s = 0f
            for (k in parts.indices) s += pa[k] * sin(TAU * f * parts[k] * t) * exp(-t * k * 1.5f)
            b[(o + j) % n] += s * e * amp
        }
    }

    /** Karplus-Strong plucked string. */
    private fun pluck(b: FloatArray, start: Float, f: Float, amp: Float, rnd: Random) {
        val period = (RATE / f).toInt().coerceAtLeast(2)
        val ring = FloatArray(period) { rnd.nextFloat() - 0.5f }
        val n = b.size
        val o = (start * RATE).toInt()
        var idx = 0
        for (j in 0 until (RATE * 1.6f).toInt()) {
            val nxt = (idx + 1) % period
            val v = ring[idx]
            ring[idx] = 0.497f * (ring[idx] + ring[nxt])
            b[(o + j) % n] += v * amp * (if (j < 40) j / 40f else 1f)
            idx = nxt
        }
    }

    private fun reed(b: FloatArray, start: Float, dur: Float, f: Float, amp: Float) {
        val n = b.size
        val o = (start * RATE).toInt()
        val len = ((dur + 0.08f) * RATE).toInt()
        var ph = 0f; var ph2 = 0f
        for (j in 0 until len) {
            val t = j / RATE.toFloat()
            ph += TAU * f / RATE; ph2 += TAU * f * 1.004f / RATE
            var s = 0f
            for (k in 1..5) s += (sin(ph * k) + sin(ph2 * k)) / (k * 1.3f)
            val e = (if (t < 0.04f) t / 0.04f else 1f) * (if (t > dur) ((dur + 0.08f - t) / 0.08f).coerceAtLeast(0f) else 1f)
            b[(o + j) % n] += s * e * amp * 0.5f
        }
    }

    private fun whistle(b: FloatArray, start: Float, dur: Float, f: Float, amp: Float) {
        val n = b.size
        val o = (start * RATE).toInt()
        val len = (dur * RATE).toInt()
        var ph = 0f
        for (j in 0 until len) {
            val t = j / RATE.toFloat()
            val vib = 1f + 0.006f * sin(TAU * 5.2f * t) * clamp01((t - 0.15f) * 3f)
            ph += TAU * f * vib / RATE
            val e = clamp01(t / 0.18f) * clamp01((dur - t) / 0.25f)
            b[(o + j) % n] += (sin(ph) + 0.2f * sin(ph * 2f) + 0.08f * sin(ph * 3f)) * e * amp
        }
    }

    // ================================================================== wav

    fun wav(pcm: ShortArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size * 2)
        fun i32(v: Int) { out.write(v and 255); out.write((v shr 8) and 255); out.write((v shr 16) and 255); out.write((v shr 24) and 255) }
        fun i16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }
        out.write("RIFF".toByteArray()); i32(36 + pcm.size * 2); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(pcm.size * 2)
        for (s in pcm) i16(s.toInt())
        return out.toByteArray()
    }
}
