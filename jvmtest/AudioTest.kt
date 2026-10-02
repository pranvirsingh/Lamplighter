import com.pranvir.lamplighter.*
import java.io.File
object AudioTest {
    @JvmStatic fun main(a: Array<String>) {
        val out = File("/home/claude/lamplighter/shots/audio").apply { mkdirs() }
        fun stats(name: String, p: ShortArray, ms: Double) {
            var peak = 0; var sum = 0.0
            for (s in p) { val v = Math.abs(s.toInt()); if (v > peak) peak = v; sum += s * s.toDouble() }
            check(peak > 2000) { "$name silent" }
            println("%-10s %6.2fs peak=%5d rms=%5.0f gen=%4.0fms".format(name, p.size / 22050f, peak, Math.sqrt(sum / p.size), ms))
            File(out, "$name.wav").writeBytes(Synth.wav(p))
        }
        var total = 0.0
        for (id in 0 until Sfx.COUNT) { val t = System.nanoTime(); val p = Synth.render(id); val ms = (System.nanoTime() - t) / 1e6; total += ms; stats("sfx$id", p, ms) }
        for (id in 1 until Sfx.AMB_COUNT) { val t = System.nanoTime(); val p = Synth.renderAmbient(id); val ms = (System.nanoTime() - t) / 1e6; total += ms; stats("amb$id", p, ms) }
        val stems = ArrayList<ShortArray>()
        for (s in 0 until Synth.STEMS) { val t = System.nanoTime(); val p = Synth.renderStem(s); val ms = (System.nanoTime() - t) / 1e6; total += ms; stats("stem$s", p, ms); stems.add(p) }
        // full mix preview like the game at 4 lamps
        val gains = floatArrayOf(0.55f, 0.5f, 0.26f, 0.3f, 0.32f)
        val n = stems.minOf { it.size }
        val mix = ShortArray(n) { i -> var s = 0f; for (k in stems.indices) s += stems[k][i] * gains[k]; s.coerceIn(-32000f, 32000f).toInt().toShort() }
        stats("mix_full", mix, 0.0)
        val mix0 = ShortArray(n) { i -> (stems[0][i] * gains[0]).toInt().toShort() }
        stats("mix_dark", mix0, 0.0)
        println("total gen ms %.0f".format(total))
    }
}
