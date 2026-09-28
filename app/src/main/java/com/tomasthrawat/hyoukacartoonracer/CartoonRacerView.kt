package com.tomasthrawat.hyoukacartoonracer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class CartoonRacerView(context: Context) : View(context) {
    private enum class State { READY, RACING, FINISHED }

    private data class Racer(
        val name: String,
        val body: Int,
        val stripe: Int,
        var lane: Int,
        var progress: Float,
        var speed: Float,
        var wobble: Float = 0f
    )

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val white = 0xFFFFFFFF.toInt()
    private var state = State.READY
    private var last = SystemClock.uptimeMillis()
    private var time = 0f
    private var playerDistance = 0f
    private var scroll = 0f
    private var lane = 1f
    private var targetLane = 1
    private var boost = 0f

    private val player = Racer("YOU", 0xFFFF5A5F.toInt(), white, 1, 0f, 220f)
    private val rivals = mutableListOf<Racer>()

    init {
        reset()
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = min(0.033f, max(0f, (now - last) / 1000f))
        last = now
        if (state == State.RACING) update(dt)
        drawScene(c)
        postInvalidateOnAnimation()
    }

    private fun reset() {
        state = State.READY
        last = SystemClock.uptimeMillis()
        time = 0f
        playerDistance = 0f
        scroll = 0f
        lane = 1f
        targetLane = 1
        boost = 0f
        player.progress = 0f
        player.speed = 220f
        rivals.clear()
        rivals += Racer("MOMO", 0xFF4C9AFF.toInt(), 0xFFFFE66D.toInt(), 0, 80f, 198f)
        rivals += Racer("KUMA", 0xFF50C878.toInt(), white, 2, 120f, 204f)
        rivals += Racer("NOVA", 0xFF9B6DFF.toInt(), 0xFFFF8CC6.toInt(), 1, 150f, 201f)
    }

    private fun start() {
        if (state == State.FINISHED) reset()
        state = State.RACING
        last = SystemClock.uptimeMillis()
    }

    private fun update(dt: Float) {
        time += dt
        boost = max(0f, boost - dt)
        val targetSpeed = 230f + if (boost > 0f) 130f else 0f
        player.speed += (targetSpeed - player.speed) * min(1f, dt * 5f)
        player.progress += player.speed * dt
        playerDistance = player.progress
        lane += (targetLane - lane) * min(1f, dt * 9f)
        scroll = (scroll + player.speed * dt) % 600f

        rivals.forEachIndexed { i, r ->
            r.speed = (r.speed + sin(time * (0.9f + i * 0.2f) + i) * 5f).coerceIn(178f, 235f)
            r.progress += r.speed * dt
            r.wobble = sin(time * 1.7f + i) * 0.06f
            if (abs(r.progress - player.progress) < 62f && abs(r.lane - lane) < 0.3f) {
                player.speed = max(165f, player.speed - 75f)
                lane = (lane + if (r.lane < lane) 0.1f else -0.1f).coerceIn(0f, 2f)
            }
        }

        if (player.progress >= 3000f) state = State.FINISHED
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (state != State.RACING) {
            start()
            return true
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h) * 0.17f
        val y0 = h - 30f - s
        when {
            RectF(28f, y0, 28f + s, h - 30f).contains(e.x, e.y) ->
                targetLane = max(0, targetLane - 1)
            RectF(42f + s, y0, 42f + s * 2f, h - 30f).contains(e.x, e.y) ->
                targetLane = min(2, targetLane + 1)
            RectF(w - 40f - s * 1.15f, h - 30f - s, w - 40f, h - 30f).contains(e.x, e.y) ->
                boost = 1.35f
        }
        return true
    }

    private fun drawScene(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * 0.34f
        val roadTop = w * 0.24f
        val roadBottom = w * 1.02f
        val center = w * 0.5f

        p.style = Paint.Style.FILL
        p.color = 0xFF9DE2FF.toInt()
        c.drawRect(0f, 0f, w, h, p)
        drawSun(c, w * 0.82f, h * 0.14f, min(w, h) * 0.07f)
        drawCloud(c, w * 0.16f, h * 0.13f, 1f)
        drawCloud(c, w * 0.48f, h * 0.11f, 0.7f)
        drawHills(c, horizon)

        p.color = 0xFF66C66A.toInt()
        c.drawRect(0f, horizon, w, h, p)

        path.reset()
        path.moveTo(center - roadTop, horizon)
        path.lineTo(center + roadTop, horizon)
        path.lineTo(center + roadBottom, h)
        path.lineTo(center - roadBottom, h)
        path.close()
        p.color = 0xFF555C64.toInt()
        c.drawPath(path, p)

        drawEdge(c, center - roadTop, center - roadBottom, horizon, h, true)
        drawEdge(c, center + roadTop, center + roadBottom, horizon, h, false)
        drawDashes(c, center, roadTop, roadBottom, horizon, h)
        drawTrees(c, horizon)
        drawRivals(c, center, horizon)
        drawCar(c, center + (lane - 1f) * w * 0.17f, h * 0.80f, 1.35f, player.body, player.stripe, true, "YOU")
        drawHud(c)
        drawControls(c)

        if (state == State.READY) drawReady(c)
        if (state == State.FINISHED) drawFinish(c)
    }

    private fun drawEdge(c: Canvas, tx: Float, bx: Float, ty: Float, by: Float, left: Boolean) {
        path.reset()
        if (left) {
            path.moveTo(tx, ty)
            path.lineTo(tx + 8f, ty)
            path.lineTo(bx + 30f, by)
            path.lineTo(bx, by)
        } else {
            path.moveTo(tx - 8f, ty)
            path.lineTo(tx, ty)
            path.lineTo(bx, by)
            path.lineTo(bx - 30f, by)
        }
        path.close()
        p.color = white
        c.drawPath(path, p)
    }

    private fun drawDashes(c: Canvas, center: Float, top: Float, bottom: Float, horizon: Float, by: Float) {
        p.color = 0xFFDCE0E5.toInt()
        for (off in floatArrayOf(-0.33f, 0.33f)) {
            for (i in 0..11) {
                val t = ((i * 0.11f) + scroll / 700f) % 1f
                val y = horizon + (by - horizon) * t
                val half = top + (bottom - top) * t
                val x = center + half * off
                val dash = 6f + 18f * t
                c.drawRect(x - dash, y, x + dash, y + 10f + 15f * t, p)
            }
        }
    }

    private fun drawTrees(c: Canvas, horizon: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0..9) {
            val t = ((i * 0.17f + scroll / 520f) % 1f)
            val y = horizon + (h - horizon) * t
            val half = w * (0.25f + 0.8f * t)
            val side = if (i % 2 == 0) -1f else 1f
            val x = w * 0.5f + side * (half + 55f + (i % 3) * 23f)
            drawTree(c, x, y, 18f + 44f * t)
        }
    }

    private fun drawTree(c: Canvas, x: Float, y: Float, s: Float) {
        p.color = 0xFF8A5A3B.toInt()
        c.drawRoundRect(RectF(x - s * 0.15f, y - s * 0.05f, x + s * 0.15f, y + s * 0.8f), s * 0.1f, s * 0.1f, p)
        p.color = 0xFF2E9B59.toInt()
        c.drawCircle(x, y - s * 0.25f, s * 0.5f, p)
        p.color = 0xFF56C878.toInt()
        c.drawCircle(x - s * 0.25f, y - s * 0.4f, s * 0.34f, p)
        p.color = 0xFF3BBE68.toInt()
        c.drawCircle(x + s * 0.24f, y - s * 0.38f, s * 0.31f, p)
    }

    private fun drawRivals(c: Canvas, center: Float, horizon: Float) {
        rivals.sortedByDescending { it.progress }.forEach { r ->
            val rel = r.progress - player.progress
            if (rel in -360f..420f) {
                val t = ((rel + 360f) / 780f).coerceIn(0f, 1f)
                val y = horizon + height * 0.61f * (1f - t)
                val sc = 0.42f + 0.92f * (1f - t)
                val half = width * (0.25f + 0.8f * (1f - t))
                val x = center + (r.lane.toFloat() - 1f + r.wobble) * half * 0.63f
                drawCar(c, x, y, sc, r.body, r.stripe, false, r.name)
            }
        }
    }

    private fun drawCar(c: Canvas, x: Float, y: Float, scale: Float, body: Int, stripe: Int, label: Boolean, name: String) {
        val cw = 62f * scale
        val ch = 104f * scale
        p.color = 0x44000000
        c.drawOval(RectF(x - cw * 0.62f, y + ch * 0.35f, x + cw * 0.62f, y + ch * 0.55f), p)
        p.color = body
        c.drawRoundRect(RectF(x - cw / 2f, y - ch / 2f, x + cw / 2f, y + ch / 2f), 17f * scale, 17f * scale, p)
        p.color = 0xFF2A3038.toInt()
        c.drawRoundRect(RectF(x - cw * 0.33f, y - ch * 0.28f, x + cw * 0.33f, y + ch * 0.03f), 12f * scale, 12f * scale, p)
        p.color = stripe
        c.drawRoundRect(RectF(x - cw * 0.1f, y - ch * 0.49f, x + cw * 0.1f, y + ch * 0.49f), 8f * scale, 8f * scale, p)
        p.color = white
        c.drawCircle(x - cw * 0.23f, y - ch * 0.34f, 6f * scale, p)
        c.drawCircle(x + cw * 0.23f, y - ch * 0.34f, 6f * scale, p)
        p.color = 0xFFFFF1A8.toInt()
        c.drawCircle(x - cw * 0.27f, y + ch * 0.36f, 7f * scale, p)
        c.drawCircle(x + cw * 0.27f, y + ch * 0.36f, 7f * scale, p)
        if (label) {
            p.color = 0xFFFFE45E.toInt()
            p.textAlign = Paint.Align.CENTER
            p.textSize = 16f * scale
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText(name, x, y - ch * 0.64f, p)
        }
    }

    private fun drawSun(c: Canvas, x: Float, y: Float, r: Float) {
        p.color = 0xFFFFD95A.toInt()
        c.drawCircle(x, y, r, p)
        p.color = 0x66FFFFFF
        p.strokeWidth = 6f
        for (i in 0..7) {
            val a = i * 0.785f
            c.drawLine(x + cos(a) * r * 1.45f, y + sin(a) * r * 1.45f, x + cos(a) * r * 1.8f, y + sin(a) * r * 1.8f, p)
        }
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, s: Float) {
        p.color = 0xEEFFFFFF.toInt()
        c.drawCircle(x, y, 23f * s, p)
        c.drawCircle(x + 28f * s, y - 8f * s, 31f * s, p)
        c.drawCircle(x + 60f * s, y, 22f * s, p)
        c.drawRoundRect(RectF(x - 20f * s, y, x + 80f * s, y + 28f * s), 15f, 15f, p)
    }

    private fun drawHills(c: Canvas, base: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = 0xFF78C878.toInt()
        path.reset()
        path.moveTo(0f, base)
        path.quadTo(w * 0.12f, base - 100f, w * 0.27f, base)
        path.quadTo(w * 0.45f, base - 135f, w * 0.61f, base)
        path.quadTo(w * 0.8f, base - 90f, w, base)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        p.color = 0xCC172033.toInt()
        c.drawRoundRect(RectF(22f, 20f, 315f, 106f), 24f, 24f, p)
        p.color = white
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        p.textSize = 23f
        val lap = min(3, (playerDistance / 1000f).toInt() + 1)
        c.drawText("LAP " + lap + "/3", 42f, 54f, p)
        c.drawText("POS " + position() + "/4", 42f, 85f, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText(player.speed.toInt().toString() + " km/h", w - 28f, 50f, p)
        c.drawText(time.toInt().toString() + " s", w - 28f, 82f, p)
    }

    private fun drawControls(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h) * 0.17f
        val b = h - 30f
        button(c, RectF(28f, b - s, 28f + s, b), "◀")
        button(c, RectF(42f + s, b - s, 42f + s * 2f, b), "▶")
        button(c, RectF(w - 40f - s * 1.15f, b - s * 0.92f, w - 40f, b), "BOOST")
    }

    private fun button(c: Canvas, r: RectF, label: String) {
        p.color = 0xE6FFFFFF.toInt()
        c.drawRoundRect(r, 20f, 20f, p)
        p.color = 0xFF182233.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = if (label == "BOOST") 18f else 30f
        c.drawText(label, r.centerX(), r.centerY() + p.textSize * 0.34f, p)
    }

    private fun drawReady(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = 0xAA0F1725.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.color = white
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = min(w, h) * 0.12f
        c.drawText("HYOUKA CARTOON RACER", w * 0.5f, h * 0.36f, p)
        p.color = 0xFFFFD95A.toInt()
        p.textSize = min(w, h) * 0.055f
        c.drawText("Tap anywhere to race", w * 0.5f, h * 0.49f, p)
        p.color = white
        p.textSize = min(w, h) * 0.034f
        c.drawText("◀ ▶ to steer  •  BOOST for speed", w * 0.5f, h * 0.56f, p)
    }

    private fun drawFinish(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = 0xB3141B2A.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.color = 0xFFFFD95A.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = min(w, h) * 0.13f
        c.drawText("FINISH!", w * 0.5f, h * 0.40f, p)
        p.color = white
        p.textSize = min(w, h) * 0.05f
        c.drawText("Position " + position() + "/4  •  " + time.toInt() + " seconds", w * 0.5f, h * 0.51f, p)
        p.textSize = min(w, h) * 0.042f
        c.drawText("Tap to race again", w * 0.5f, h * 0.60f, p)
    }

    private fun position(): Int = 1 + rivals.count { it.progress > player.progress }
}
