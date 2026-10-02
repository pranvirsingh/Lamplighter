import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import com.pranvir.lamplighter.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object IconGen {
    fun art(c: Canvas, s: Float, safe: Float, bg: Boolean) {
        if (bg) {
            val p = Paint()
            p.shader = LinearGradient(0f, 0f, 0f, s, intArrayOf(Pal.SKY_TOP, Pal.SKY_MID, 0xFF7A6E5E.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s, s, p)
            // roof line
            val roof = Path()
            roof.moveTo(0f, s); roof.lineTo(0f, s * 0.84f); roof.lineTo(s * 0.5f, s * 0.74f); roof.lineTo(s, s * 0.84f); roof.lineTo(s, s); roof.close()
            Draw.fill.color = Pal.T5
            c.drawPath(roof, Draw.fill)
        }
        // Wick, big, centred in the safe zone
        val wick = Wick()
        wick.hasPole = false
        wick.flame = 1.25f; wick.flameTarget = 1.25f
        val h = 230f
        val scale = safe * 0.95f / h
        c.save()
        c.translate(s / 2f, s / 2f + safe * 0.47f)
        c.scale(scale, scale)
        wick.x = 0f; wick.ground = 0f; wick.face = 1
        wick.draw(c, 0.4f, 0.6f)
        c.restore()
    }
    fun save(img: BufferedImage, path: String) { val f = File(path); f.parentFile.mkdirs(); ImageIO.write(img, "png", f) }

    @JvmStatic fun main(a: Array<String>) {
        val res = "/home/claude/lamplighter/res"
        for ((d, k) in linkedMapOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)) {
            val fs = (108 * k).toInt()
            val fg = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            art(Canvas(fg), fs.toFloat(), fs * 0.62f, true)
            save(fg, "$res/mipmap-$d/ic_launcher_fg.png")
            val mono = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            val tmp = BufferedImage(fs, fs, BufferedImage.TYPE_INT_ARGB)
            art(Canvas(tmp), fs.toFloat(), fs * 0.62f, false)
            for (y in 0 until fs) for (x in 0 until fs) { val px = tmp.getRGB(x, y); val al = (px ushr 24) and 255; mono.setRGB(x, y, (if (al > 128) 255 else al) shl 24) }
            save(mono, "$res/mipmap-$d/ic_launcher_mono.png")
            val ls = (48 * k).toInt()
            for (round in listOf(false, true)) {
                val big = ls * 4
                val img = BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB)
                val c = Canvas(img)
                val clip = Path(); val inset = big * 0.04f
                if (round) clip.addCircle(big / 2f, big / 2f, big / 2f - inset, Path.Direction.CW) else clip.addRect(inset, inset, big - inset, big - inset, Path.Direction.CW)
                c.save(); c.clipPath(clip); art(c, big.toFloat(), big * 0.8f, true); c.restore()
                val out = BufferedImage(ls, ls, BufferedImage.TYPE_INT_ARGB)
                out.createGraphics().drawImage(img.getScaledInstance(ls, ls, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
                save(out, "$res/mipmap-$d/" + (if (round) "ic_launcher_round.png" else "ic_launcher.png"))
            }
        }
        val prev = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        art(Canvas(prev), 512f, 512f * 0.8f, true)
        save(prev, "/home/claude/lamplighter/shots/icon512.png")
        println("ICONS OK")
    }
}
