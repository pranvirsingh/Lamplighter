package com.pranvir.lamplighter

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.util.ArrayDeque
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

interface Host {
    fun loadString(key: String): String?
    fun saveString(key: String, v: String?)
    fun loadInt(key: String, def: Int): Int
    fun saveInt(key: String, v: Int)
    fun sound(id: Int, vol: Float = 1f, rate: Float = 1f)
    fun setAudio(soundOn: Boolean, musicOn: Boolean)
    /** Music layers 0..4 and the ambient loop for the current scene. */
    fun setMusicState(layers: Int, ambient: Int)
    fun haptic(strong: Boolean)
}

/** Story flags. */
object F {
    const val AWAKE = 0
    const val GOT_POLE = 1
    const val STOOL_MOVED = 2
    const val GOT_OIL = 3
    const val DOOR_OPEN = 4
    const val GOT_COIN = 5
    const val COIN_GIVEN = 6
    const val GOT_VALVE = 7
    const val GAS_ON = 8
    const val LAMP1 = 9
    const val GOT_FISH = 10
    const val FISH_GIVEN = 11
    const val GOT_GEAR = 12
    const val BRIDGE_DOWN = 13
    const val LAMP2 = 14
    const val GOT_LENS = 15
    const val LENS_IN = 16
    const val LAMP3 = 17
    const val GOT_KEY = 18
    const val TOWER_OPEN = 19
    const val CLOCK_RUN = 20
    const val LAMP4 = 21
    const val ENDING_SEEN = 22
    const val SAW_STARS = 23
    const val COUNT = 32
}

// ====================================================================== actions

abstract class Act {
    var started = false
    open fun start() {}
    /** Returns true when finished. */
    abstract fun update(dt: Float): Boolean
}

class Hot(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val standX: Float,
    val visible: () -> Boolean = { true },
    val onItem: ((Int) -> Boolean)? = null,
    val exit: Boolean = false,
    val onTap: () -> Unit
) {
    fun contains(x: Float, y: Float) = x in x0..x1 && y in y0..y1
    val cx get() = (x0 + x1) / 2f
    val cy get() = (y0 + y1) / 2f
}

class Btn(val id: Int, var icon: Int) {
    var x = 0f; var y = 0f; var r = 0f
    var visible = false
    var off = false
    var press = 0f
    fun hit(px: Float, py: Float, slop: Float) = visible && hypot(px - x, py - y) <= r + slop
}

class Game(val host: Host) {
    companion object {
        const val MODE_TITLE = 0
        const val MODE_PLAY = 1
        const val MODE_ENDING = 2
        const val WORLD_W = 1600f
        const val WORLD_H = 900f
        const val SAVE_KEY = "save_v1"
        val ITEMS = intArrayOf(Icon.OILCAN, Icon.COIN, Icon.VALVE, Icon.FISH, Icon.GEAR, Icon.LENS, Icon.KEY)

        const val B_PAUSE = 1; const val B_RESUME = 2; const val B_SOUND = 3; const val B_MUSIC = 4; const val B_HOME = 5
        const val B_PLAY = 6; const val B_NEW = 7; const val B_NEW_YES = 8; const val B_T_SOUND = 9; const val B_T_MUSIC = 10
    }

    val rng = Random()
    val paper = Paper()
    val wick = Wick()
    var time = 0f; private set

    // screen mapping
    var w = 1f; private set
    var h = 1f; private set
    private var dens = 1f
    var sc = 1f; private set
    var ox = 0f; private set
    var oy = 0f; private set
    private var u = 1f
    private var insetL = 0f; private var insetR = 0f; private var insetT = 0f; private var insetB = 0f

    // state
    var mode = MODE_TITLE; private set
    val flags = BooleanArray(F.COUNT)
    val inv = ArrayList<Int>()
    var selected = -1; private set
    var fireflies = 0L; private set
    val warmth = FloatArray(6)
    var soundOn = true; private set
    var musicOn = true; private set
    var paused = false; private set
    private var hasSave = false

    // scenes
    lateinit var scenes: Array<Scene>
    var sceneIdx = 0; private set
    val scene get() = scenes[sceneIdx]

    // action queue
    private val acts = ArrayDeque<Act>()
    val busy get() = acts.isNotEmpty() || fadePhase != 0 || mini != null

    // bubble
    private var bubIcons = IntArray(0)
    private var bubT = 0f
    private var bubLife = 0f
    private var bubX = 0f
    private var bubY = 0f
    private var bubNpc = false

    // mini game
    var mini: Mini? = null; private set

    // fade
    private var fadePhase = 0
    private var fadeT = 0f
    private var fadeAction: (() -> Unit)? = null
    private var fadeDur = 0.4f

    // input
    private var down = false
    private var downX = 0f
    private var downY = 0f
    private var pressedBtn: Btn? = null
    private var tapHint = 0f
    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleT = 9f

    // particles (screen space sparkle + world space)
    private val pMax = 200
    private val px = FloatArray(pMax); private val py = FloatArray(pMax); private val pvx = FloatArray(pMax); private val pvy = FloatArray(pMax)
    private val pl = FloatArray(pMax); private val pml = FloatArray(pMax); private val ps = FloatArray(pMax); private val pt = IntArray(pMax)
    private var pn = 0

    // fireflies flying to the jar
    private var jarPulse = 0f

    // ui
    private val bPause = Btn(B_PAUSE, Icon.UI_PAUSE)
    private val bResume = Btn(B_RESUME, Icon.UI_PLAY)
    private val bSound = Btn(B_SOUND, Icon.UI_SOUND)
    private val bMusic = Btn(B_MUSIC, Icon.UI_MUSIC)
    private val bHome = Btn(B_HOME, Icon.UI_HOME)
    private val bPlay = Btn(B_PLAY, Icon.UI_PLAY)
    private val bNew = Btn(B_NEW, Icon.UI_RESTART)
    private val bNewYes = Btn(B_NEW_YES, Icon.YES)
    private val bTSound = Btn(B_T_SOUND, Icon.UI_SOUND)
    private val bTMusic = Btn(B_T_MUSIC, Icon.UI_MUSIC)
    private val allBtns = arrayOf(bPause, bResume, bSound, bMusic, bHome, bPlay, bNew, bNewYes, bTSound, bTMusic)
    private var newArmed = 0f
    private val slotR = RectF()

    // ending
    private var endT = 0f
    private lateinit var endArt: Boil
    private lateinit var titleArt: Boil

    // misc
    private val skyPaint = Paint()
    private var skyH = -1f
    private val tmpPath = Path()
    private var musicLayers = -1
    private var ambientId = -1
    var boilFrame = 0; private set
    private var boilClock = 0f

    init {
        soundOn = host.loadInt("sound", 1) == 1
        musicOn = host.loadInt("music", 1) == 1
        host.setAudio(soundOn, musicOn)
        scenes = Scenes.create(this)
        endArt = Scenes.endingArt()
        titleArt = Scenes.titleArt()
        hasSave = load()
        for (i in warmth.indices) warmth[i] = if (lampLit(i)) 1f else 0f
        updateAudioState()
        refreshButtons()
    }

    // ================================================================== flags / items

    fun flag(f: Int) = flags[f]
    fun set(f: Int) { flags[f] = true; save() }
    fun has(item: Int) = inv.contains(item)
    fun give(item: Int) {
        if (!inv.contains(item)) inv.add(item)
        sfx(Sfx.PICKUP)
        save()
    }
    fun take(item: Int) {
        inv.remove(item)
        if (selected == item) selected = -1
        save()
    }

    fun lampLit(group: Int): Boolean = when (group) {
        0 -> true
        1 -> flags[F.LAMP1]
        2 -> flags[F.LAMP2]
        3 -> flags[F.LAMP3]
        4, 5 -> flags[F.LAMP4]
        else -> false
    }

    fun lampsLit(): Int = (if (flags[F.LAMP1]) 1 else 0) + (if (flags[F.LAMP2]) 1 else 0) + (if (flags[F.LAMP3]) 1 else 0) + (if (flags[F.LAMP4]) 1 else 0)

    fun updateAudioState() {
        val layers = lampsLit()
        val amb = if (mode == MODE_PLAY) scene.ambient else if (mode == MODE_ENDING) Sfx.AMB_NONE else Sfx.AMB_WIND
        if (layers != musicLayers || amb != ambientId) {
            musicLayers = layers; ambientId = amb
            host.setMusicState(layers, amb)
        }
    }

    // ================================================================== save / load

    private var dirty = false

    /** Marks the game for saving. The write happens only at a safe moment (no scripted sequence running). */
    fun save() { dirty = true }

    private fun flushSave() {
        dirty = false
        if (mode == MODE_TITLE && !hasSave) return
        val sb = StringBuilder("1|")
        sb.append(sceneIdx).append('|').append(wick.x.toInt()).append('|')
        for (f in flags) sb.append(if (f) '1' else '0')
        sb.append('|')
        sb.append(inv.joinToString(","))
        sb.append('|').append(fireflies)
        host.saveString(SAVE_KEY, sb.toString())
        hasSave = true
    }

    private fun load(): Boolean {
        val s = host.loadString(SAVE_KEY) ?: return false
        return try {
            val p = s.split('|')
            if (p.size != 6 || p[0] != "1") return false
            val si = p[1].toInt()
            if (si !in scenes.indices) return false
            val fx = p[2].toFloat()
            if (p[3].length != F.COUNT) return false
            for (i in 0 until F.COUNT) flags[i] = p[3][i] == '1'
            inv.clear()
            if (p[4].isNotEmpty()) for (t in p[4].split(',')) { val v = t.toInt(); if (v in ITEMS && !inv.contains(v)) inv.add(v) }
            fireflies = p[5].toLong()
            sceneIdx = si
            wick.x = fx.coerceIn(scene.minX, scene.maxX)
            wick.targetX = wick.x
            wick.hasPole = flags[F.GOT_POLE]
            true
        } catch (e: Exception) {
            for (i in flags.indices) flags[i] = false
            inv.clear(); fireflies = 0L; sceneIdx = 0
            false
        }
    }

    private fun newGame() {
        for (i in flags.indices) flags[i] = false
        inv.clear()
        selected = -1
        fireflies = 0L
        sceneIdx = 0
        for (i in warmth.indices) warmth[i] = if (i == 0) 1f else 0f
        wick.x = 430f; wick.targetX = 430f; wick.face = 1; wick.hasPole = false
        wick.poleFlame = 0f
        for (s in scenes) s.reset()
        hasSave = true
        flushSave()
    }

    /** Instantly finishes any running scripted sequence (used when leaving to the title mid-animation). */
    private fun drainActs() {
        mini = null
        var guard = 0
        while ((acts.isNotEmpty() || fadePhase != 0) && guard < 200000) {
            guard++
            if (fadePhase == 1) { fadeAction?.invoke(); fadeAction = null; fadePhase = 0; continue }
            if (fadePhase == 2) { fadePhase = 0; continue }
            val a = acts.peekFirst() ?: break
            if (!a.started) { a.started = true; a.start() }
            if (mini != null) mini = null
            wick.update(0.05f, time)
            if (a.update(0.05f)) acts.pollFirst()
        }
        if (acts.isNotEmpty()) acts.clear()
        wick.walking = false
        wick.yOff = 0f
        wick.setPose(Wick.IDLE)
        bubLife = 0f
    }

    // ================================================================== sizing

    fun resize(width: Int, height: Int, density: Float) {
        w = max(1, width).toFloat(); h = max(1, height).toFloat(); dens = max(0.5f, density)
        paper.resize(width, height, density)
        layout()
    }

    fun setInsets(l: Int, t: Int, r: Int, b: Int) {
        insetL = l.toFloat(); insetT = t.toFloat(); insetR = r.toFloat(); insetB = b.toFloat()
        layout()
    }

    private fun layout() {
        sc = min(w / WORLD_W, h / WORLD_H)
        ox = (w - WORLD_W * sc) / 2f
        oy = (h - WORLD_H * sc) / 2f
        u = min(w, h) / 100f
        val r = u * 6.2f
        bPause.x = insetL + u * 3f + r; bPause.y = insetT + u * 3f + r; bPause.r = r
        // pause menu buttons in a row
        val cy = h / 2f + u * 6f
        val gap = u * 17f
        val big = u * 8f
        bResume.x = w / 2f - gap * 1.5f; bSound.x = w / 2f - gap * 0.5f; bMusic.x = w / 2f + gap * 0.5f; bHome.x = w / 2f + gap * 1.5f
        for (b in arrayOf(bResume, bSound, bMusic, bHome)) { b.y = cy; b.r = big }
        // title
        bPlay.x = w * 0.5f; bPlay.y = h * 0.66f; bPlay.r = u * 10f
        bNew.x = w - insetR - u * 10f; bNew.y = h - insetB - u * 10f; bNew.r = u * 5.5f
        bNewYes.x = bNew.x - u * 13f; bNewYes.y = bNew.y; bNewYes.r = u * 5.5f
        bTSound.x = insetL + u * 10f; bTSound.y = h - insetB - u * 10f; bTSound.r = u * 5.5f
        bTMusic.x = insetL + u * 23f; bTMusic.y = bTSound.y; bTMusic.r = u * 5.5f
        skyH = -1f
        mini?.layout(w, h, u)
    }

    fun toWorldX(x: Float) = (x - ox) / sc
    fun toWorldY(y: Float) = (y - oy) / sc
    fun toScreenX(x: Float) = ox + x * sc
    fun toScreenY(y: Float) = oy + y * sc

    // ================================================================== actions API (used by scenes)

    fun queue(a: Act) { acts.add(a) }
    fun clearActs() { acts.clear() }

    fun walkTo(x: Float) = queue(object : Act() {
        override fun start() { wick.walkTo(x.coerceIn(scene.minX, scene.maxX)) }
        override fun update(dt: Float) = !wick.walking
    })

    fun faceTo(x: Float) { if (x > wick.x + 2f) wick.face = 1 else if (x < wick.x - 2f) wick.face = -1 }

    fun face(x: Float) = queue(object : Act() {
        override fun start() { faceTo(x) }
        override fun update(dt: Float) = true
    })

    fun pose(p: Int, dur: Float) = queue(object : Act() {
        var t = 0f
        override fun start() { wick.setPose(p) }
        override fun update(dt: Float): Boolean {
            t += dt
            if (t >= dur) { wick.setPose(Wick.IDLE); return true }
            return false
        }
    })

    fun wait(dur: Float) = queue(object : Act() {
        var t = 0f
        override fun update(dt: Float): Boolean { t += dt; return t >= dur }
    })

    fun then(block: () -> Unit) = queue(object : Act() {
        override fun start() { block() }
        override fun update(dt: Float) = true
    })

    /** Tween a value over time (used for pushing stools, climbing, bridges...). */
    fun tween(dur: Float, fn: (Float) -> Unit) = queue(object : Act() {
        var t = 0f
        override fun update(dt: Float): Boolean {
            t += dt
            fn(clamp01(t / dur))
            return t >= dur
        }
    })

    /** Thought bubble over Wick. */
    fun think(dur: Float, vararg icons: Int) = queue(object : Act() {
        var t = 0f
        override fun start() { showBubble(icons, dur, false, 0f, 0f) }
        override fun update(dt: Float): Boolean { t += dt; return t >= dur * 0.8f }
    })

    /** Thought bubble over an NPC at world (x, y). Does not block for long. */
    fun npcThink(x: Float, y: Float, dur: Float, vararg icons: Int) = queue(object : Act() {
        var t = 0f
        override fun start() { showBubble(icons, dur, true, x, y) }
        override fun update(dt: Float): Boolean { t += dt; return t >= dur * 0.8f }
    })

    fun sfxAct(id: Int, vol: Float = 1f, rate: Float = 1f) = then { sfx(id, vol, rate) }

    fun showBubble(icons: IntArray, dur: Float, npc: Boolean, x: Float, y: Float) {
        bubIcons = icons
        bubLife = dur
        bubT = 0f
        bubNpc = npc
        bubX = x; bubY = y
        sfx(Sfx.BUBBLE, 0.5f)
    }

    fun sfx(id: Int, vol: Float = 1f, rate: Float = 1f) {
        if (soundOn) host.sound(id, vol.coerceIn(0f, 1f), rate.coerceIn(0.5f, 2f))
    }

    fun openMini(m: Mini) = queue(object : Act() {
        override fun start() {
            mini = m
            m.layout(w, h, u)
            sfx(Sfx.OPEN, 0.7f)
            refreshButtons()
        }
        override fun update(dt: Float) = mini == null
    })

    fun closeMini() {
        val m = mini ?: return
        mini = null
        refreshButtons()
        if (m.solved) m.onSolved() else sfx(Sfx.CLOSE, 0.6f)
    }

    /** Standard lamp-lighting ritual. */
    fun lightLamp(flagId: Int, lampX: Float, onLit: () -> Unit) {
        face(lampX)
        then { wick.setPose(Wick.RAISE_POLE) }
        wait(0.45f)
        sfxAct(Sfx.WHOOSH, 0.7f)
        tween(0.5f) { wick.poleFlame = it }
        wait(0.25f)
        then {
            flags[flagId] = true
            sfx(Sfx.LAMP_ON)
            host.haptic(true)
            onLit()
            save()
            updateAudioState()
        }
        tween(0.4f) { wick.poleFlame = 1f - it }
        then { wick.setPose(Wick.HAPPY) }
        wait(1.4f)
        then { wick.setPose(Wick.IDLE) }
    }

    fun goScene(idx: Int, x: Float, face: Int) {
        if (fadePhase != 0) return
        fadePhase = 1
        fadeT = 0f
        fadeDur = 0.4f
        fadeAction = {
            sceneIdx = idx
            wick.x = x.coerceIn(scene.minX, scene.maxX)
            wick.targetX = wick.x
            wick.walking = false
            wick.face = face
            wick.ground = scene.ground
            wick.yOff = 0f
            selected = -1
            bubLife = 0f
            scene.enter()
            save()
            updateAudioState()
        }
    }

    // ================================================================== hints

    fun hintIcons(): IntArray {
        val f = flags
        return when {
            !f[F.GOT_POLE] -> intArrayOf(Icon.POLE, Icon.QUESTION)
            !f[F.DOOR_OPEN] -> when {
                has(Icon.OILCAN) -> intArrayOf(Icon.OILCAN, Icon.DOOR)
                !f[F.STOOL_MOVED] -> intArrayOf(Icon.STOOL, Icon.ARROW_R, Icon.OILCAN)
                else -> intArrayOf(Icon.OILCAN, Icon.QUESTION)
            }
            !f[F.LAMP1] -> when {
                f[F.GAS_ON] -> intArrayOf(Icon.LAMP, Icon.FLAME)
                has(Icon.VALVE) -> intArrayOf(Icon.VALVE, Icon.LAMP)
                has(Icon.COIN) -> intArrayOf(Icon.COIN, Icon.CROW)
                !f[F.COIN_GIVEN] && !f[F.GOT_COIN] -> intArrayOf(Icon.CROW, Icon.COIN, Icon.QUESTION)
                else -> intArrayOf(Icon.VALVE, Icon.QUESTION)
            }
            !f[F.LAMP2] -> when {
                f[F.BRIDGE_DOWN] -> intArrayOf(Icon.LAMP, Icon.FLAME)
                has(Icon.GEAR) -> intArrayOf(Icon.GEAR, Icon.BRIDGE)
                has(Icon.FISH) -> intArrayOf(Icon.FISH, Icon.CAT)
                !f[F.GOT_FISH] -> intArrayOf(Icon.FISH, Icon.QUESTION)
                else -> intArrayOf(Icon.GEAR, Icon.QUESTION)
            }
            !f[F.LAMP3] -> when {
                f[F.LENS_IN] -> intArrayOf(Icon.MIRROR, Icon.LAMP)
                has(Icon.LENS) -> intArrayOf(Icon.LENS, Icon.HILL)
                else -> intArrayOf(Icon.LENS, Icon.QUESTION)
            }
            !f[F.TOWER_OPEN] -> if (has(Icon.KEY)) intArrayOf(Icon.KEY, Icon.TOWER) else intArrayOf(Icon.KEY, Icon.QUESTION)
            !f[F.CLOCK_RUN] -> intArrayOf(Icon.TELESCOPE, Icon.CLOCK)
            !f[F.LAMP4] -> intArrayOf(Icon.TOWER, Icon.FLAME)
            else -> intArrayOf(Icon.HEART, Icon.FIREFLY)
        }
    }

    // ================================================================== input

    fun touchDown(x: Float, y: Float) {
        down = true
        downX = x; downY = y
        pressedBtn = null
        for (b in allBtns) if (b.hit(x, y, u * 1.5f)) { pressedBtn = b; break }
        if (pressedBtn == null && mode == MODE_PLAY && !paused) mini?.down(x, y)
    }

    fun touchMove(x: Float, y: Float) {
        if (!down) return
        if (pressedBtn == null && mode == MODE_PLAY && !paused) mini?.move(x, y)
    }

    fun touchUp(x: Float, y: Float) {
        if (!down) return
        down = false
        val b = pressedBtn
        pressedBtn = null
        if (b != null) {
            if (b.hit(x, y, u * 3f)) onButton(b)
            return
        }
        when (mode) {
            MODE_TITLE -> {}
            MODE_ENDING -> if (endT > 5f) {
                sfx(Sfx.BUBBLE, 0.6f)
                mode = MODE_TITLE
                updateAudioState()
                refreshButtons()
            }
            MODE_PLAY -> {
                if (paused || fadePhase != 0) return
                val m = mini
                if (m != null) { m.up(x, y); if (m.closeRequested) closeMini(); return }
                tapWorld(x, y)
            }
        }
    }

    fun touchCancel() {
        down = false
        pressedBtn = null
        mini?.cancel()
    }

    fun onBack(): Boolean {
        when (mode) {
            MODE_TITLE -> return false
            MODE_ENDING -> { if (endT > 2f) { mode = MODE_TITLE; updateAudioState(); refreshButtons() }; return true }
            MODE_PLAY -> {
                if (mini != null) { closeMini(); return true }
                paused = !paused
                refreshButtons()
                return true
            }
        }
        return true
    }

    fun onPause() {
        if (mode == MODE_PLAY && !paused) { paused = true; refreshButtons() }
        down = false
        pressedBtn = null
        mini?.cancel()
        if (dirty && !busy) flushSave()
    }

    private fun onButton(b: Btn) {
        sfx(Sfx.CLICK, 0.7f)
        host.haptic(false)
        when (b.id) {
            B_PAUSE -> { paused = true }
            B_RESUME -> paused = false
            B_SOUND, B_T_SOUND -> { soundOn = !soundOn; host.saveInt("sound", if (soundOn) 1 else 0); host.setAudio(soundOn, musicOn) }
            B_MUSIC, B_T_MUSIC -> { musicOn = !musicOn; host.saveInt("music", if (musicOn) 1 else 0); host.setAudio(soundOn, musicOn) }
            B_HOME -> { paused = false; drainActs(); flushSave(); mode = MODE_TITLE; updateAudioState() }
            B_PLAY -> startPlay(false)
            B_NEW -> { if (!hasSave) startPlay(true) else newArmed = 3f }
            B_NEW_YES -> { newArmed = 0f; startPlay(true) }
        }
        refreshButtons()
    }

    private fun startPlay(fresh: Boolean) {
        if (fresh || !hasSave) newGame()
        mode = MODE_PLAY
        paused = false
        mini = null
        clearActs()
        wick.ground = scene.ground
        wick.hasPole = flags[F.GOT_POLE]
        wick.flame = if (flags[F.AWAKE]) 1f else 0.25f
        wick.flameTarget = wick.flame
        wick.setPose(if (flags[F.AWAKE]) Wick.IDLE else Wick.SLEEP)
        for (i in warmth.indices) warmth[i] = if (lampLit(i)) 1f else 0f
        scene.enter()
        fadePhase = 2; fadeT = 0f; fadeDur = 0.6f
        updateAudioState()
        refreshButtons()
    }

    private fun refreshButtons() {
        for (b in allBtns) b.visible = false
        when (mode) {
            MODE_TITLE -> {
                bPlay.visible = true; bNew.visible = true; bTSound.visible = true; bTMusic.visible = true
                bNewYes.visible = newArmed > 0f
                bTSound.off = !soundOn; bTMusic.off = !musicOn
            }
            MODE_PLAY -> {
                if (paused) {
                    bResume.visible = true; bSound.visible = true; bMusic.visible = true; bHome.visible = true
                    bSound.off = !soundOn; bMusic.off = !musicOn
                } else if (mini == null) bPause.visible = true
            }
        }
    }

    private fun invSlot(i: Int, out: RectF) {
        val s = u * 11f
        val gap = u * 1.4f
        val right = w - insetR - u * 3f
        val top = insetT + u * 3f
        val x1 = right - i * (s + gap)
        out.set(x1 - s, top, x1, top + s)
    }

    private fun tapWorld(sx: Float, sy: Float) {
        // inventory
        for (i in inv.indices) {
            invSlot(i, slotR)
            if (slotR.contains(sx, sy) || (sx >= slotR.left - u && sx <= slotR.right + u && sy >= slotR.top - u && sy <= slotR.bottom + u)) {
                if (busy) return
                val item = inv[i]
                selected = if (selected == item) -1 else item
                sfx(Sfx.CLICK, 0.6f, if (selected >= 0) 1.2f else 0.9f)
                return
            }
        }
        if (busy) return
        val x = toWorldX(sx); val y = toWorldY(sy)
        // Wakes up on first tap
        if (!flags[F.AWAKE]) { wakeUp(); return }
        // fireflies (generous radius)
        val ff = scene.fireflyAt(x, y, time)
        if (ff >= 0) { catchFirefly(ff, x, y); return }
        // Wick himself: shows what to do next
        if (abs(x - wick.x) < 48f && y > wick.ground + wick.yOff - 235f && y < wick.ground + wick.yOff + 10f) {
            if (selected >= 0) { selected = -1; return }
            wick.face = if (x < wick.x) -1 else wick.face
            think(2.6f, *hintIcons())
            return
        }
        // hotspots, last defined wins
        val hs = scene.hotspots
        var found: Hot? = null
        for (hot in hs) if (hot.exit && hot.visible() && hot.contains(x, y)) { found = hot; break }
        if (found == null) for (k in hs.indices.reversed()) { val hot = hs[k]; if (hot.visible() && hot.contains(x, y)) { found = hot; break } }
        if (found != null) {
            val hot = found
            val item = selected
            walkTo(hot.standX)
            face(hot.cx)
            if (item >= 0) {
                selected = -1
                then {
                    val handled = hot.onItem?.invoke(item) ?: false
                    if (!handled) { pose(Wick.SHAKE, 0.8f); think(1.6f, item, Icon.NO); then { sfx(Sfx.NOPE, 0.6f) } }
                }
            } else then { hot.onTap() }
            return
        }
        // plain walk
        selected = -1
        rippleX = x.coerceIn(scene.minX, scene.maxX); rippleY = scene.ground; rippleT = 0f
        walkTo(x)
    }

    private fun wakeUp() {
        flags[F.AWAKE] = true
        then { sfx(Sfx.IGNITE, 0.8f) }
        tween(1.2f) { wick.flameTarget = 0.25f + it * 0.75f; wick.flame = wick.flameTarget }
        then { wick.setPose(Wick.IDLE) }
        wait(0.4f)
        pose(Wick.HAPPY, 0.8f)
        think(2.4f, Icon.TOWER, Icon.LAMP, Icon.QUESTION)
        think(2.6f, Icon.POLE, Icon.FLAME, Icon.LAMP)
        then { save() }
    }

    private fun catchFirefly(id: Int, x: Float, y: Float) {
        if (fireflies and (1L shl id) != 0L) return
        fireflies = fireflies or (1L shl id)
        sfx(Sfx.TWINKLE, 0.8f, 0.9f + rng.nextFloat() * 0.3f)
        host.haptic(false)
        burst(toScreenX(x), toScreenY(y), 14, 1)
        jarPulse = 1f
        save()
    }

    fun fireflyCount(): Int = java.lang.Long.bitCount(fireflies)
    val fireflyTotal = 18

    // ================================================================== update

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        time += dt
        paper.update(dt)
        boilClock += dt
        if (boilClock > 1f / 7f) { boilClock = 0f; boilFrame = (boilFrame + 1) % 3 }
        for (b in allBtns) b.press = approach(b.press, if (b === pressedBtn && down) 1f else 0f, dt * 12f)
        if (newArmed > 0f) { newArmed -= dt; if (newArmed <= 0f) refreshButtons() else bNewYes.visible = true }
        jarPulse = max(0f, jarPulse - dt * 1.5f)
        updateParticles(dt)
        // fade
        if (fadePhase == 1) {
            fadeT += dt
            if (fadeT >= fadeDur) { fadeAction?.invoke(); fadeAction = null; fadePhase = 2; fadeT = 0f }
        } else if (fadePhase == 2) {
            fadeT += dt
            if (fadeT >= fadeDur) { fadePhase = 0; fadeT = 0f }
        }
        when (mode) {
            MODE_TITLE -> {}
            MODE_ENDING -> endT += dt
            MODE_PLAY -> if (!paused) updatePlay(dt)
        }
        if (dirty && !busy) flushSave()
    }

    private fun updatePlay(dt: Float) {
        for (i in warmth.indices) warmth[i] = approach(warmth[i], if (lampLit(i)) 1f else 0f, dt * 0.6f)
        wick.update(dt, time)
        if (wick.stepEvent) sfx(Sfx.STEP, 0.25f, 0.9f + rng.nextFloat() * 0.25f)
        val m = mini
        if (m != null) {
            m.update(dt, time)
            if (m.closeRequested) closeMini()
            return
        }
        // action queue
        var guard = 0
        while (acts.isNotEmpty() && guard < 20) {
            val a = acts.peekFirst()!!
            if (!a.started) { a.started = true; a.start() }
            if (mini != null) break
            if (a.update(dt)) { acts.pollFirst(); guard++ } else break
        }
        if (bubLife > 0f) {
            bubT += dt
            if (bubT > bubLife) bubLife = 0f
        }
        scene.update(dt, time)
        tapHint += dt
    }

    // ================================================================== particles

    private fun burst(x: Float, y: Float, n: Int, type: Int) {
        for (k in 0 until n) {
            val i = pn; pn = (pn + 1) % pMax
            val a = rng.nextFloat() * 6.283f
            val sp = (0.3f + rng.nextFloat()) * u * 30f
            px[i] = x; py[i] = y; pvx[i] = cos(a) * sp; pvy[i] = sin(a) * sp
            pml[i] = 0.6f + rng.nextFloat() * 0.6f; pl[i] = pml[i]; ps[i] = u * (0.4f + rng.nextFloat() * 0.7f); pt[i] = type
        }
    }

    fun burstWorld(x: Float, y: Float, n: Int) = burst(toScreenX(x), toScreenY(y), n, 1)

    private fun updateParticles(dt: Float) {
        for (i in 0 until pMax) {
            if (pl[i] <= 0f) continue
            pl[i] -= dt
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt
            pvx[i] *= (1f - dt * 2f); pvy[i] = pvy[i] * (1f - dt * 2f) - u * 4f * dt
        }
    }

    // ================================================================== draw

    fun draw(c: Canvas) {
        when (mode) {
            MODE_TITLE -> drawTitle(c)
            MODE_PLAY -> drawPlay(c)
            MODE_ENDING -> drawEnding(c)
        }
        // particles
        for (i in 0 until pMax) {
            if (pl[i] <= 0f) continue
            val f = clamp01(pl[i] / pml[i])
            Draw.glow(c, px[i], py[i], ps[i] * 5f, f * 0.6f)
            Draw.fill.color = alphaF(Pal.AMBER_HI, f)
            c.drawCircle(px[i], py[i], ps[i] * f, Draw.fill)
        }
        paper.draw(c, if (mode == MODE_PLAY) 0.3f + lampsLit() * 0.15f else 0.5f)
        // fade
        if (fadePhase != 0) {
            val f = clamp01(fadeT / fadeDur)
            val a = if (fadePhase == 1) smooth(f) else 1f - smooth(f)
            Draw.fill.color = withAlpha(0xFF15110E.toInt(), (255 * a).toInt())
            c.drawRect(0f, 0f, w, h, Draw.fill)
        }
    }

    private fun drawSky(c: Canvas, warm: Float) {
        if (skyH != h) {
            skyH = h
            skyPaint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Pal.SKY_TOP, Pal.SKY_MID, Pal.SKY_LOW),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, skyPaint)
        if (warm > 0.01f) {
            Draw.fill.color = withAlpha(Pal.AMBER_DK, (38 * warm).toInt())
            c.drawRect(0f, h * 0.45f, w, h, Draw.fill)
        }
    }

    fun drawStars(c: Canvas, density: Float) {
        // in world space
        val r = Random(99)
        for (k in 0 until (60 * density).toInt()) {
            val sx = -400f + r.nextFloat() * 2400f
            val sy = -300f + r.nextFloat() * 700f
            val tw = 0.5f + 0.5f * sin(time * (1f + r.nextFloat() * 2f) + k)
            Draw.fill.color = withAlpha(Pal.PAPER_HI, (60 + 140 * tw).toInt())
            c.drawCircle(sx, sy, 1.2f + r.nextFloat() * 1.8f, Draw.fill)
        }
    }

    private fun drawPlay(c: Canvas) {
        val s = scene
        drawSky(c, warmth[s.warmGroup] * 0.7f + warmth[4] * 0.3f)
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        s.drawBehind(c, time)
        Draw.shapes(c, s.art.variants[boilFrame], warmth)
        s.drawBack(c, time)
        wick.draw(c, time)
        s.drawFront(c, time)
        s.drawFireflies(c, time, fireflies)
        if (rippleT < 0.6f) {
            rippleT += 1f / 60f
            val f = rippleT / 0.6f
            Draw.stroke.color = withAlpha(Pal.AMBER_HI, (200 * (1f - f)).toInt())
            Draw.stroke.strokeWidth = 4f
            Draw.r2.set(rippleX - 20f - f * 50f, rippleY - 6f - f * 12f, rippleX + 20f + f * 50f, rippleY + 6f + f * 12f)
            c.drawOval(Draw.r2, Draw.stroke)
        }
        // tap-to-wake hint
        if (!flags[F.AWAKE] && !busy) {
            val pr = 0.5f + 0.5f * sin(time * 3f)
            Draw.stroke.color = withAlpha(Pal.AMBER_HI, (120 + 100 * pr).toInt())
            Draw.stroke.strokeWidth = 3f
            c.drawCircle(wick.flameX(), wick.flameY() - 10f, 40f + pr * 18f, Draw.stroke)
        }
        // bubble
        if (bubLife > 0f) {
            val appear = min(1f, bubT / 0.25f) * min(1f, (bubLife - bubT) / 0.25f)
            val bs = 72f
            val ax: Float; val ay: Float
            if (bubNpc) { ax = bubX; ay = bubY } else { ax = wick.x + wick.face * 20f; ay = wick.headTopY() }
            val bx = (ax + 70f).coerceIn(bs * 2f, WORLD_W - bs * 2f - bubIcons.size * bs * 0.5f)
            val by = max(bs * 1.3f, ay - 110f)
            Bubble.draw(c, bx, by, bubIcons, bs, appear, ax, ay, time)
        }
        c.restore()
        drawHud(c)
        mini?.draw(c, time)
        if (paused) drawPause(c)
    }

    private fun drawHud(c: Canvas) {
        if (mini != null) return
        drawButton(c, bPause)
        // inventory slots
        for (i in inv.indices) {
            invSlot(i, slotR)
            val sel = inv[i] == selected
            Draw.fill.color = withAlpha(Pal.INK, 150)
            c.drawRoundRect(slotR.left + u * 0.5f, slotR.top + u * 0.6f, slotR.right + u * 0.5f, slotR.bottom + u * 0.6f, u * 2f, u * 2f, Draw.fill)
            Draw.fill.color = if (sel) Pal.AMBER_HI else Pal.PAPER
            c.drawRoundRect(slotR.left, slotR.top - (if (sel) u else 0f), slotR.right, slotR.bottom - (if (sel) u else 0f), u * 2f, u * 2f, Draw.fill)
            Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = u * 0.45f
            c.drawRoundRect(slotR.left, slotR.top - (if (sel) u else 0f), slotR.right, slotR.bottom - (if (sel) u else 0f), u * 2f, u * 2f, Draw.stroke)
            Icon.draw(c, inv[i], slotR.centerX(), slotR.centerY() - (if (sel) u else 0f), slotR.width() * 0.8f, Pal.INK, Pal.BRASS, time)
        }
        // firefly jar next to pause
        val jx = bPause.x + bPause.r + u * 8f
        val jy = bPause.y
        drawJar(c, jx, jy, u * 5f)
    }

    private fun drawJar(c: Canvas, x: Float, y: Float, s: Float) {
        val n = fireflyCount()
        Draw.glow(c, x, y, s * (2f + jarPulse * 2f), 0.2f + n * 0.03f + jarPulse * 0.5f)
        Draw.r1.set(x - s * 0.6f, y - s * 0.7f, x + s * 0.6f, y + s * 0.9f)
        Draw.fill.color = withAlpha(Pal.PAPER_HI, 90)
        c.drawRoundRect(Draw.r1, s * 0.3f, s * 0.3f, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = s * 0.1f
        c.drawRoundRect(Draw.r1, s * 0.3f, s * 0.3f, Draw.stroke)
        Draw.r1.set(x - s * 0.45f, y - s * 0.95f, x + s * 0.45f, y - s * 0.7f)
        Draw.fill.color = Pal.BRASS_DK
        c.drawRect(Draw.r1, Draw.fill); c.drawRect(Draw.r1, Draw.stroke)
        val r = Random(3)
        for (k in 0 until n) {
            val fx = x + (r.nextFloat() - 0.5f) * s * 0.9f + sin(time * 2f + k) * s * 0.08f
            val fy = y + (r.nextFloat() - 0.4f) * s * 1.2f + cos(time * 1.7f + k) * s * 0.08f
            Draw.fill.color = withAlpha(Pal.AMBER_HI, (160 + 90 * sin(time * 4f + k * 1.3f)).toInt().coerceIn(0, 255))
            c.drawCircle(fx, fy, s * 0.08f, Draw.fill)
        }
    }

    fun drawButton(c: Canvas, b: Btn) {
        if (!b.visible) return
        val off = b.press * u * 0.6f
        Draw.fill.color = withAlpha(Pal.INK, 170)
        c.drawCircle(b.x + u * 0.6f, b.y + u * 0.8f, b.r, Draw.fill)
        Draw.fill.color = Pal.PAPER
        c.drawCircle(b.x + off, b.y + off, b.r, Draw.fill)
        Draw.stroke.color = Pal.INK; Draw.stroke.strokeWidth = u * 0.5f
        c.drawCircle(b.x + off, b.y + off, b.r, Draw.stroke)
        Draw.stroke.strokeWidth = u * 0.2f
        c.drawCircle(b.x + off, b.y + off, b.r * 0.84f, Draw.stroke)
        Icon.draw(c, b.icon, b.x + off, b.y + off, b.r * 1.2f, Pal.INK, Pal.BRASS, time, b.off)
    }

    private fun drawPause(c: Canvas) {
        Draw.fill.color = withAlpha(0xFF15110E.toInt(), 170)
        c.drawRect(0f, 0f, w, h, Draw.fill)
        // a little lantern sways above the buttons
        val lx = w / 2f; val ly = h / 2f - u * 18f
        Draw.glow(c, lx, ly, u * 30f, 0.8f)
        Icon.draw(c, Icon.LAMP, lx, ly, u * 18f, Pal.INK, Pal.BRASS, time)
        for (b in arrayOf(bResume, bSound, bMusic, bHome)) drawButton(c, b)
        // firefly tally as dots (no words)
        val n = fireflyCount()
        val y = bResume.y + bResume.r + u * 9f
        for (k in 0 until fireflyTotal) {
            val x = w / 2f + (k - (fireflyTotal - 1) / 2f) * u * 3f
            Draw.fill.color = if (k < n) Pal.AMBER else withAlpha(Pal.PAPER, 90)
            if (k < n) Draw.glow(c, x, y, u * 2.5f, 0.6f)
            c.drawCircle(x, y, u * 0.8f, Draw.fill)
        }
    }

    private fun drawTitle(c: Canvas) {
        drawSky(c, 0.3f)
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        drawStars(c, 1f)
        Scenes.drawTitleBehind(c, time)
        Draw.shapes(c, titleArt.variants[boilFrame], warmth)
        Scenes.drawTitleFront(c, time, this)
        c.restore()
        // logo
        val size = min(w * 0.12f, h * 0.2f)
        val ty = h * 0.22f
        Draw.glow(c, w / 2f, ty, size * 3.2f, 0.5f)
        Draw.title(c, "Lamplighter", w / 2f + u * 0.5f, ty + u * 0.7f, size, withAlpha(Pal.INK, 200))
        Draw.title(c, "Lamplighter", w / 2f, ty, size, Pal.PAPER_HI)
        val sub = "~ a Pranvir Singh picture ~"
        Draw.title(c, sub, w / 2f, ty + size * 0.72f, size * 0.26f, withAlpha(Pal.PAPER_HI, 200))
        // play button glows like a lantern
        Draw.glow(c, bPlay.x, bPlay.y, bPlay.r * 3f, 0.6f + 0.15f * sin(time * 2f))
        drawButton(c, bPlay)
        drawButton(c, bNew)
        if (newArmed > 0f) drawButton(c, bNewYes)
        drawButton(c, bTSound)
        drawButton(c, bTMusic)
        // progress: 4 little lamps + jar
        val lx = w / 2f - u * 16f
        val py = bPlay.y + bPlay.r + u * 9f
        Draw.fill.color = withAlpha(0xFF15110E.toInt(), 150)
        c.drawRoundRect(lx - u * 6f, py - u * 5.5f, w / 2f + u * 27f, py + u * 5.5f, u * 5f, u * 5f, Draw.fill)
        for (k in 0 until 4) {
            val lit = lampLit(k + 1)
            val x = lx + k * u * 9f
            if (lit) Draw.glow(c, x, py - u * 1.5f, u * 7f, 0.9f)
            Icon.draw(c, Icon.LAMP, x, py, u * 7.5f, Pal.PAPER_HI, Pal.BRASS, time, !lit)
        }
        drawJar(c, w / 2f + u * 21f, py, u * 3.6f)
    }

    // ------------------------------------------------------------------ ending

    fun startEnding() {
        mode = MODE_ENDING
        endT = 0f
        flags[F.ENDING_SEEN] = true
        save()
        updateAudioState()
        host.setMusicState(5, Sfx.AMB_NONE)
        musicLayers = 5
        refreshButtons()
    }

    private val fwX = FloatArray(8); private val fwY = FloatArray(8); private val fwT = FloatArray(8)

    private fun drawEnding(c: Canvas) {
        drawSky(c, 1f)
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        drawStars(c, 1.3f)
        // fireworks
        for (k in 0 until 8) {
            val period = 2.6f + k * 0.37f
            val t = ((endT + k * 0.9f) % period) / period
            if (t < 0.05f) { val r = Random((endT / period).toLong() * 31 + k); fwX[k] = 200f + r.nextFloat() * 1200f; fwY[k] = 60f + r.nextFloat() * 260f }
            if (endT < 1.5f) continue
            if (t < 0.35f) {
                val yy = lerp(760f, fwY[k], t / 0.35f)
                Draw.fill.color = Pal.AMBER_HI
                c.drawCircle(fwX[k], yy, 3f, Draw.fill)
            } else {
                val e = (t - 0.35f) / 0.65f
                val rad = 30f + e * 120f
                Draw.glow(c, fwX[k], fwY[k], rad * 1.6f, (1f - e) * 0.7f)
                for (j in 0 until 14) {
                    val a = j * 6.283f / 14f
                    Draw.fill.color = alphaF(if (j % 2 == 0) Pal.AMBER_HI else Pal.AMBER, 1f - e)
                    c.drawCircle(fwX[k] + cos(a) * rad, fwY[k] + sin(a) * rad + e * e * 40f, 4f * (1f - e) + 1f, Draw.fill)
                }
            }
        }
        for (i in warmth.indices) warmth[i] = 1f
        Draw.shapes(c, endArt.variants[boilFrame], warmth)
        Scenes.drawEndingFront(c, time, endT)
        c.restore()
        // title card
        val a = clamp01((endT - 4f) / 2f)
        if (a > 0f) {
            val size = min(w * 0.11f, h * 0.18f)
            val cx = w * 0.42f
            Draw.title(c, "Lamplighter", cx, h * 0.2f, size, alphaF(Pal.PAPER_HI, a))
            Draw.title(c, "~ a Pranvir Singh picture ~", cx, h * 0.2f + size * 0.7f, size * 0.26f, alphaF(Pal.PAPER_HI, a * 0.85f))
            drawJar(c, cx, h * 0.2f + size * 1.4f, u * 4f * a)
        }
        if (endT > 5f) {
            val p = 0.5f + 0.5f * sin(time * 3f)
            val bx = w - insetR - u * 10f; val by = h - insetB - u * 10f
            Draw.glow(c, bx, by, u * 12f, 0.4f + 0.3f * p)
            Draw.fill.color = withAlpha(Pal.PAPER, 220)
            c.drawCircle(bx, by, u * 5f, Draw.fill)
            Icon.draw(c, Icon.UI_PLAY, bx, by, u * 6f, Pal.INK)
        }
    }

    // ================================================================== debug hooks for tests

    fun debugHot(i: Int): Hot? = scene.hotspots.getOrNull(i)
    fun debugTapWorld(x: Float, y: Float) { touchDown(toScreenX(x), toScreenY(y)); touchUp(toScreenX(x), toScreenY(y)) }
    fun debugInvSlotCenter(item: Int, out: FloatArray): Boolean {
        val i = inv.indexOf(item); if (i < 0) return false
        invSlot(i, slotR); out[0] = slotR.centerX(); out[1] = slotR.centerY(); return true
    }
    fun debugBtn(id: Int, out: FloatArray): Boolean {
        for (b in allBtns) if (b.id == id && b.visible) { out[0] = b.x; out[1] = b.y; return true }
        return false
    }
    val debugBusy get() = busy
    val debugActs get() = acts.size
    fun debugPaused() = paused
    val debugFade get() = fadePhase
}
